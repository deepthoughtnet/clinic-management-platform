import { keycloak } from "./keycloak";

let initPromise: Promise<boolean> | null = null;

export function initKeycloakOnce(timeoutMs = 5000) {
  if (!initPromise) {
    initPromise = new Promise<boolean>((resolve, reject) => {
      const timeout = window.setTimeout(() => {
        reject(new Error("Keycloak initialization timed out"));
      }, timeoutMs);

      Promise.resolve(keycloak.init({
        onLoad: "check-sso",
        pkceMethod: "S256",
        checkLoginIframe: false,
        flow: "standard",
      }))
        .then((authenticated) => resolve(authenticated === true))
        .catch(reject)
        .finally(() => window.clearTimeout(timeout));
    }).catch((err) => {
      initPromise = null;
      throw err;
    });
  }

  return initPromise;
}

export function resetKeycloakInit() {
  initPromise = null;
}
