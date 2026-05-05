import Keycloak from "keycloak-js";

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8181",
  realm: import.meta.env.VITE_KEYCLOAK_REALM || "clinic-platform",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "clinic-web",
});
