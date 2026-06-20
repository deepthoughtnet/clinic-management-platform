import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(process.cwd(), "src");

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("runtime auth files import the explicit keycloak client module", () => {
  const authProvider = read("auth/AuthProvider.tsx");
  const keycloakInit = read("auth/keycloakInit.ts");
  const helpClient = read("api/helpClient.ts");
  const graphqlClient = read("api/graphqlClient.ts");

  assert.match(authProvider, /from "\.\/keycloakClient"/);
  assert.match(keycloakInit, /from "\.\/keycloakClient"/);
  assert.match(helpClient, /from "\.\.\/auth\/keycloakClient"/);
  assert.match(graphqlClient, /from "\.\.\/auth\/keycloakClient"/);
  assert.equal(authProvider.includes("keycloak.js"), false);
  assert.equal(keycloakInit.includes("keycloak.js"), false);
  assert.equal(helpClient.includes("keycloak.js"), false);
  assert.equal(graphqlClient.includes("keycloak.js"), false);
});

test("keycloak client module contains the real instance and runtime guards", () => {
  const client = read("auth/keycloakClient.ts");
  assert.match(client, /new Keycloak\(/);
  assert.match(client, /assertValidKeycloakClient/);
  assert.match(client, /typeof .*\.init/);
  assert.match(client, /typeof .*\.logout/);
});

