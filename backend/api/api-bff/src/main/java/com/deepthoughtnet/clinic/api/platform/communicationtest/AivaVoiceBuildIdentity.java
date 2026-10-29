package com.deepthoughtnet.clinic.api.platform.communicationtest;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Whitelisted, non-secret artifact identity for the inbound Voice diagnostics panel. */
final class AivaVoiceBuildIdentity {
    private static final Pattern COMMIT = Pattern.compile("(?i)[0-9a-f]{7,40}");

    private AivaVoiceBuildIdentity() { }

    static Map<String, String> current() {
        Properties build = new Properties();
        try (InputStream input = AivaVoiceBuildIdentity.class.getClassLoader()
                .getResourceAsStream("META-INF/build-info.properties")) {
            if (input != null) build.load(input);
        } catch (Exception ignored) {
            // Diagnostics must remain available even if optional build metadata is absent.
        }
        Map<String, String> identity = new LinkedHashMap<>();
        put(identity, "version", first(build.getProperty("build.version"),
                AivaVoiceBuildIdentity.class.getPackage().getImplementationVersion()));
        put(identity, "buildTime", build.getProperty("build.time"));
        String commit = first(System.getProperty("aiva.build.commit"), System.getenv("AIVA_BUILD_COMMIT"),
                System.getenv("GIT_COMMIT"));
        if (commit != null && COMMIT.matcher(commit).matches()) identity.put("commitSha", commit);
        put(identity, "releaseTag", safeTag(first(System.getProperty("aiva.release.tag"),
                System.getenv("AIVA_RELEASE_TAG"), System.getenv("RELEASE_TAG"))));
        return Map.copyOf(identity);
    }

    private static String safeTag(String value) {
        return value != null && value.matches("[A-Za-z0-9._/-]{1,80}") ? value : null;
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) values.put(key, value);
    }
}
