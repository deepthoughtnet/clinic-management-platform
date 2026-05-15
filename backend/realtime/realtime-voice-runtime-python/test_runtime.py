from fastapi.testclient import TestClient

from app import app


client = TestClient(app)


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
