package com.deepthoughtnet.clinic.api.config.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DbBackupScriptTest {
    @Test
    void backupScriptCreatesNonEmptyArchive() throws Exception {
        Path repoRoot = findRepoRoot();
        Path tempDir = Files.createTempDirectory("db-backup-script-test");
        Path fakeBin = Files.createDirectories(tempDir.resolve("bin"));
        Path fakeDocker = fakeBin.resolve("docker");
        Files.writeString(fakeDocker, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ "$*" == *"pg_dump"* ]]; then
                  printf 'custom-backup-bytes'
                  exit 0
                fi
                if [[ "$*" == *"ps -q"* ]]; then
                  printf 'fake-container'
                  exit 0
                fi
                exit 0
                """);
        fakeDocker.toFile().setExecutable(true);

        Path backupDir = Files.createDirectories(tempDir.resolve("backups"));
        Path composeFile = tempDir.resolve("compose.yml");
        Files.writeString(composeFile, "services: {}\n");

        ProcessBuilder processBuilder = new ProcessBuilder("bash", "scripts/db-backup.sh");
        processBuilder.directory(repoRoot.toFile());
        Map<String, String> env = processBuilder.environment();
        env.put("PATH", fakeBin + System.getProperty("path.separator") + env.getOrDefault("PATH", ""));
        env.put("COMPOSE_FILE", composeFile.toString());
        env.put("DB_SERVICE", "postgres");
        env.put("DB_NAME", "clinic_management_test");
        env.put("DB_USER", "clinic_test");
        env.put("DB_PASSWORD", "secret");
        env.put("BACKUP_DIR", backupDir.toString());

        Process process = processBuilder.start();
        String stdout = readAll(process.getInputStream());
        String stderr = readAll(process.getErrorStream());
        int exit = process.waitFor();

        assertThat(exit).isZero();
        assertThat(stderr).isEmpty();

        Path backupFile = Path.of(stdout.trim());
        assertThat(Files.exists(backupFile)).isTrue();
        assertThat(Files.size(backupFile)).isGreaterThan(0L);
    }

    private static String readAll(InputStream inputStream) throws Exception {
        try (inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    private static Path findRepoRoot() throws Exception {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("scripts/db-backup.sh"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate repository root");
        }
        return current;
    }
}
