package com.deepthoughtnet.clinic.api.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jeevanam")
public record PlatformOpsReleaseProperties(
        String releaseTag,
        String gitCommit,
        String buildTimestamp,
        String deploymentTimestamp,
        String environment
) {}
