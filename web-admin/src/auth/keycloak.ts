import Keycloak from "keycloak-js";

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8182",
  realm: import.meta.env.VITE_KEYCLOAK_REALM || "clinic-management",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "clinic-web-admin",
});
