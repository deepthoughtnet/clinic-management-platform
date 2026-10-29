package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AivaVoiceBuildIdentityTest {
    @Test
    void exposesOnlyWhitelistedNonSecretBuildIdentityFields() {
        var identity = AivaVoiceBuildIdentity.current();
        assertThat(identity.keySet()).allMatch(key ->
                key.equals("version") || key.equals("buildTime") || key.equals("commitSha") || key.equals("releaseTag"));
        assertThat(identity).doesNotContainKey("password").doesNotContainKey("token").doesNotContainKey("secret");
        if (identity.containsKey("commitSha")) assertThat(identity.get("commitSha")).matches("(?i)[0-9a-f]{7,40}");
    }
}
