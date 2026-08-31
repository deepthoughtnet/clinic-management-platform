import { assertValidKeycloakClient, keycloak } from "./keycloakClient";

let initPromise: Promise<boolean> | null = null;

export function initKeycloakOnce(timeoutMs = 5000) {
  if (!initPromise) {
    initPromise = new Promise<boolean>((resolve, reject) => {
      const timeout = window.setTimeout(() => {
        reject(new Error("Keycloak initialization timed out"));
      }, timeoutMs);

      assertValidKeycloakClient(keycloak);
      Promise.resolve(keycloak.init({
        onLoad: "check-sso",
        pkceMethod: "S256",
        checkLoginIframe: false,
        flow: "standard",
      }))
        .then((authenticated) => resolve(authenticated === true))
        .catch(reject)
        .finally(() => window.clearTimeout(timeout));
    });
  }

  return initPromise;
}

export function resetKeycloakInit() {
  // Intentionally left blank. The singleton Keycloak client is initialized once per
  // JavaScript runtime; recovery should happen through a fresh browser/runtime load.
}
