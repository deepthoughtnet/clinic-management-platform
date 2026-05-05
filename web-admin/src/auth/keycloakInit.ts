import { keycloak } from "./keycloak";

let initPromise: Promise<boolean> | null = null;

export function initKeycloakOnce() {
  if (!initPromise) {
    initPromise = keycloak.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
    });
  }

  return initPromise;
}
