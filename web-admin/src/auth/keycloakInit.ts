import { keycloak } from "./keycloak";

let initPromise: Promise<boolean> | null = null;

export function initKeycloakOnce() {
  if (!initPromise) {
    initPromise = Promise.resolve(keycloak.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
      flow: "standard",
    })).then((authenticated) => authenticated === true);
  }

  return initPromise;
}

export function resetKeycloakInit() {
  initPromise = null;
}
