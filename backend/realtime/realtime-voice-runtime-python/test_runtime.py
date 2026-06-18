from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

import app as runtime
from app import app


client = TestClient(app)


@pytest.fixture(autouse=True)
def configure_patient_portal_secret():
    runtime.CONFIG.patient_portal_session_secret = "test-session-secret"
    runtime.CONFIG.patient_portal_upstream_ws_url = "ws://clinic-management-api:8089/ws/patient-portal/careai"
    yield


def issue_patient_portal_token(
    *,
    secret: str,
    subject: str,
    tenant_id,
    patient_id,
    display_name: str,
    roles: list[str] | None = None,
    expires_at: datetime | None = None,
) -> str:
    payload = {
        "sub": subject,
        "tenantId": str(tenant_id),
        "patientId": str(patient_id),
        "displayName": display_name,
        "roles": roles or ["PATIENT"],
        "iat": datetime.now(timezone.utc).isoformat(),
        "exp": (expires_at or (datetime.now(timezone.utc) + timedelta(minutes=10))).isoformat(),
    }
    encoded_payload = base64.urlsafe_b64encode(json.dumps(payload, separators=(",", ":")).encode("utf-8")).decode("ascii").rstrip("=")
    digest = hmac.new(secret.encode("utf-8"), encoded_payload.encode("utf-8"), hashlib.sha256).digest()
    signature = base64.urlsafe_b64encode(digest).decode("ascii").rstrip("=")
    return f"{encoded_payload}.{signature}"


def test_health_endpoint():
    res = client.get("/health")
    assert res.status_code == 200
    body = res.json()
    assert "status" in body
    assert "activeSessions" in body


def test_ready_endpoint():
    res = client.get("/ready")
    assert res.status_code == 200
    assert "checks" in res.json()


def test_metrics_summary_endpoint():
    res = client.get("/metrics/summary")
    assert res.status_code == 200
    body = res.json()
    assert "avgSttLatencyMs" in body
    assert "avgTtsLatencyMs" in body


def test_patient_portal_session_token_parser_accepts_valid_token():
    tenant_id = uuid4()
    patient_id = uuid4()
    token = issue_patient_portal_token(
        secret=runtime.CONFIG.patient_portal_session_secret,
        subject=f"patientportal:{tenant_id}:{patient_id}",
        tenant_id=tenant_id,
        patient_id=patient_id,
        display_name="Riya Sharma",
    )

    context = runtime._parse_patient_portal_session_token(token)

    assert context.channel == "patient-portal"
    assert context.tenant_id == tenant_id
    assert context.patient_id == patient_id
    assert context.display_name == "Riya Sharma"
    assert context.roles == {"PATIENT"}


@pytest.mark.parametrize(
    ("token_factory", "expected_reason"),
    [
        (lambda secret, tenant_id, patient_id: (
            lambda token: f"{token.rsplit('.', 1)[0]}.invalidsig"
        )(issue_patient_portal_token(
            secret=secret,
            subject=f"patientportal:{tenant_id}:{patient_id}",
            tenant_id=tenant_id,
            patient_id=patient_id,
            display_name="Riya Sharma",
        )), "invalid-signature"),
        (lambda secret, tenant_id, patient_id: issue_patient_portal_token(
            secret=secret,
            subject=f"patientportal:{tenant_id}:{patient_id}",
            tenant_id=tenant_id,
            patient_id=patient_id,
            display_name="Riya Sharma",
            expires_at=datetime.now(timezone.utc) - timedelta(minutes=1),
        ), "expired-token"),
        (lambda secret, tenant_id, patient_id: issue_patient_portal_token(
            secret=secret,
            subject=f"patientportal:{tenant_id}:{patient_id}",
            tenant_id=tenant_id,
            patient_id=patient_id,
            display_name="Riya Sharma",
            roles=["PATIENT_REGISTRATION"],
        ), "missing-patient-role"),
    ],
)
def test_patient_portal_session_token_parser_rejects_invalid_tokens(token_factory, expected_reason):
    tenant_id = uuid4()
    patient_id = uuid4()
    token = token_factory(runtime.CONFIG.patient_portal_session_secret, tenant_id, patient_id)

    with pytest.raises(runtime.PatientPortalSessionTokenError) as exc_info:
        runtime._parse_patient_portal_session_token(token)

    assert exc_info.value.log_reason == expected_reason


def test_patient_portal_websocket_accepts_valid_token_and_relays_to_upstream(monkeypatch):
    tenant_id = uuid4()
    patient_id = uuid4()
    token = issue_patient_portal_token(
        secret=runtime.CONFIG.patient_portal_session_secret,
        subject=f"patientportal:{tenant_id}:{patient_id}",
        tenant_id=tenant_id,
        patient_id=patient_id,
        display_name="Riya Sharma",
    )

    fake_upstream = FakeUpstreamWebSocket()

    def fake_connect(url, **kwargs):
        fake_upstream.url = url
        fake_upstream.kwargs = kwargs
        return fake_upstream

    monkeypatch.setattr(runtime.websockets, "connect", fake_connect)

    with client.websocket_connect(f"/ws/patient-portal/careai?sessionToken={token}") as ws:
        ws.send_json({"type": "heartbeat"})
        ws.close()

    assert fake_upstream.url.endswith(f"sessionToken={token}")
    assert len(fake_upstream.sent) == 1
    assert json.loads(fake_upstream.sent[0]) == {"type": "heartbeat"}


class FakeUpstreamWebSocket:
    def __init__(self):
        self.url = ""
        self.kwargs = {}
        self.sent: list[str | bytes] = []

    async def send(self, message):
        self.sent.append(message)

    def __aiter__(self):
        return self

    async def __anext__(self):
        await asyncio.sleep(3600)
        raise StopAsyncIteration

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False
