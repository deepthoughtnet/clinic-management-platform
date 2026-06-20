import Keycloak from "keycloak-js";

const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8182";
const keycloakRealm = import.meta.env.VITE_KEYCLOAK_REALM || "clinic-management";
const keycloakClientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "clinic-web-admin";

export const keycloak = new Keycloak({
  url: keycloakUrl,
  realm: keycloakRealm,
  clientId: keycloakClientId,
});

export function assertValidKeycloakClient(client: unknown): asserts client is Keycloak {
  if (!client || typeof (client as { init?: unknown }).init !== "function" || typeof (client as { logout?: unknown }).logout !== "function") {
    console.error("[auth] invalid keycloak client", client);
    throw new Error("Invalid Keycloak client. keycloak.init is not available.");
  }
}

