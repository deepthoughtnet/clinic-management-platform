import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("phase 5 landing page builder routes and preview are wired", () => {
  const app = read("src/App.tsx");
  const routes = read("src/routes.ts");
  const builder = read("src/pages/provider/ProviderLandingPagePage.tsx");
  const publicPage = read("src/pages/public/LandingPagePage.tsx");
  const renderer = read("src/components/landing/LandingPageRenderer.tsx");
  const mediaImage = read("src/components/landing/PublicMediaImage.tsx");
  const imageHook = read("src/hooks/useAuthenticatedImage.ts");
  const styles = read("src/styles.css");
  const api = read("src/api/providerLandingPage.ts");
  const onboardingApi = read("src/api/providerOnboarding.ts");

  assert.ok(routes.includes('providerWorkspace: { path: "/provider"'));
  assert.ok(routes.includes('providerLandingPage: { path: "/provider/profiles"'));
  assert.ok(routes.includes('providerAccount: { path: "/provider/account"'));
  assert.ok(routes.includes('clinic: (slug: string) => `/discover/clinics/${slug}/home`'));
  assert.ok(routes.includes('hospital: (slug: string) => `/discover/hospitals/${slug}/home`'));
  assert.ok(routes.includes('doctor: (slug: string) => `/discover/doctors/${slug}/home`'));
  assert.ok(app.includes("DISCOVER_ROUTES.providerLandingPage.path"));
  assert.ok(app.includes("ProviderLandingPagePage"));
  assert.ok(app.includes('path="/discover/clinics/:clinicSlug/home"'));
  assert.ok(app.includes('path="/discover/hospitals/:hospitalSlug/home"'));
  assert.ok(app.includes('path="/discover/doctors/:doctorSlug/home"'));
  assert.ok(builder.includes("Save draft"));
  assert.ok(builder.includes("Publish landing page"));
  assert.ok(builder.includes("Revert to published"));
  assert.ok(builder.includes("Your profile is approved"));
  assert.ok(builder.includes("LandingPageRenderer"));
  assert.ok(builder.includes("device-frame"));
  assert.ok(publicPage.includes("loadPublicLandingPage"));
  assert.ok(renderer.includes("LandingHero"));
  assert.ok(renderer.includes("LandingAbout"));
  assert.ok(renderer.includes("LandingServices"));
  assert.ok(renderer.includes("LandingDoctors"));
  assert.ok(renderer.includes("LandingGallery"));
  assert.ok(renderer.includes("LandingFAQ"));
  assert.ok(renderer.includes("LandingContact"));
  assert.ok(renderer.includes("PublicMediaImage"));
  assert.ok(renderer.includes("galleryImageUrls"));
  assert.ok(renderer.includes("landing-cover-image"));
  assert.ok(renderer.includes("landing-avatar-image"));
  assert.ok(renderer.includes("landing-gallery-media"));
  assert.ok(imageHook.includes("fetchProviderDocumentBlob"));
  assert.ok(imageHook.includes('resolved.pathname.startsWith("/api/public/")'));
  assert.ok(imageHook.includes("if (isPublicApiImage(nextUrl))"));
  assert.ok(mediaImage.includes("onError={() => setImageError(true)}"));
  assert.ok(onboardingApi.includes("X-Provider-Onboarding-Token"));
  assert.ok(onboardingApi.includes("fetchProviderDocumentBlob"));
  assert.ok(styles.includes(".landing-page"));
  assert.ok(styles.includes(".landing-builder-layout"));
  assert.ok(styles.includes(".device-frame"));
  assert.ok(styles.includes(".landing-hero-media"));
  assert.ok(styles.includes(".landing-cover-image"));
  assert.ok(styles.includes(".landing-avatar-image"));
  assert.ok(styles.includes(".landing-gallery-media"));
  assert.ok(api.includes("loadLandingPage"));
  assert.ok(api.includes("publishLandingPage"));
  assert.ok(api.includes("compareLandingPageVersions"));
  assert.ok(api.includes("galleryImageUrls"));
});
