import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

import {
  buildCareProfileBackState,
  hasCareProfileBackState,
  resolveCareProfileBackTarget,
  resolveCareProfileFallbackPath,
} from "../src/pages/patient/careProfileNavigation.js";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("care profile back helper marks internal Care navigation and falls back inside Care", () => {
  const state = buildCareProfileBackState("/patient/doctors?q=cardiology");
  assert.equal(hasCareProfileBackState(state), true);
  assert.equal(resolveCareProfileBackTarget(state), "/patient/doctors?q=cardiology");
  assert.equal(hasCareProfileBackState(null), false);
  assert.equal(hasCareProfileBackState({}), false);
  assert.equal(resolveCareProfileBackTarget({ careBackTo: "https://discover.example/doctors" }), "/patient/doctors");
  assert.equal(resolveCareProfileFallbackPath(), "/patient/doctors");
});

test("doctor, clinic, hospital, and speciality profile pages render a shared back control and keep back navigation inside Care", () => {
  const pages = readSource("pages/patient/PatientDiscoveryPages.tsx");
  const shell = readSource("pages/patient/PatientPortalPages.tsx");
  const styles = readSource("styles.css");

  assert.match(shell, /const currentBookingPath = `\$\{location\.pathname\}\$\{location\.search\}`;/);
  assert.match(shell, /state=\{buildCareProfileBackState\(currentBookingPath\)\}/);
  assert.match(pages, /const initialValueRef = useRef\(initialValue\);/);
  assert.match(pages, /useEffect\(\(\) => {\n    initialValueRef\.current = initialValue;\n  }, \[initialValue\]\);/);
  assert.match(pages, /}, \[path, refreshKey, session\]\);/);
  assert.doesNotMatch(pages, /\[initialValue, path, refreshKey, session\]/);
  assert.match(pages, /topbarBeforeTitle=\{/);
  assert.match(pages, /aria-label="Back to previous Care page"/);
  assert.match(pages, /const backTarget = hasCareProfileBackState\(location\.state\)\n\s+\? resolveCareProfileBackTarget\(location\.state\)\n\s+: resolveCareProfileFallbackPath\(\);/);
  assert.match(pages, /navigate\(backTarget, \{ replace: true \}\);/);
  assert.match(pages, /state=\{buildCareProfileBackState\(currentPath\)\}/);
  assert.match(pages, /PatientPublicDoctorProfilePage/);
  assert.match(pages, /PatientPublicClinicProfilePage/);
  assert.match(pages, /PatientPublicHospitalProfilePage/);
  assert.match(pages, /PatientPublicSpecialityPage/);
  assert.match(shell, /topbarBeforeTitle\?/);
  assert.match(shell, /patient-topbar-before-title/);
  assert.match(styles, /\.patient-profile-back-link/);
  assert.doesNotMatch(pages, /navigate\("\/doctors"/);
});
