const env = typeof import.meta === "object" && import.meta && import.meta.env ? import.meta.env : {};

export const keycloak = {
  url: env.VITE_KEYCLOAK_URL || "http://localhost:8182",
  realm: env.VITE_KEYCLOAK_REALM || "clinic-management",
  clientId: env.VITE_KEYCLOAK_CLIENT_ID || "clinic-web-admin",
  token: undefined,
};

export default keycloak;
