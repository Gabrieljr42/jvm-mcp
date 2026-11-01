package dev.jvmmcp.core.attach;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jvmmcp.core.model.Framework;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FrameworkDetectorTest {

    @ParameterizedTest(name = "Detect {1} for displayName=''{0}''")
    @CsvSource({
        "org.springframework.boot.loader.JarLauncher, SPRING_BOOT",
        "io.quarkus.runner.GeneratedMain, QUARKUS",
        "io.micronaut.runtime.Micronaut, MICRONAUT",
        "com.example.plain.MainApp, PLAIN_JAVA"
    })
    @DisplayName("Should detect expected framework based on process display name")
    void shouldDetectExpectedFramework(String displayName, Framework expected) {
        Framework result = FrameworkDetector.detect(displayName, "");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should return UNKNOWN for null or empty metadata")
    void shouldReturnUnknownForBlankMetadata() {
        assertThat(FrameworkDetector.detect(null, null)).isEqualTo(Framework.UNKNOWN);
        assertThat(FrameworkDetector.detect("", "")).isEqualTo(Framework.UNKNOWN);
    }

    @Test
    @DisplayName("Should not classify framework names found only in application metadata")
    void shouldIgnoreFrameworkSubstringsOutsideMainClass() {
        assertThat(
                        FrameworkDetector.detect(
                                "com.example.QuarkusMigrationTool -Dspring-boot.enabled=true",
                                "com.example.QuarkusMigrationTool"))
                .isEqualTo(Framework.PLAIN_JAVA);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "java -jar app.jar",
                "app.jar --server.port=8080",
                "app.jar foo bar",
                "\"app.jar\" foo bar",
                "-jar app.jar foo",
                "java -jar app.jar foo",
                "-jar \"app.jar\" foo",
                "  app.jar foo  "
            })
    @DisplayName("Should resolve a relative Spring Boot jar against the target working directory")
    void shouldResolveRelativeJarAgainstTargetWorkingDirectory(
            String displayName, @TempDir Path tempDir) throws IOException {
        Path app = tempDir.resolve("app.jar");
        try (JarOutputStream ignored =
                new JarOutputStream(Files.newOutputStream(app), manifest())) {
            // The manifest is sufficient for framework detection.
        }

        assertThat(FrameworkDetector.detect(displayName, "app.jar", tempDir))
                .isEqualTo(Framework.SPRING_BOOT);
        assertThat(FrameworkDetector.detect(displayName, "app.jar", null))
                .isEqualTo(Framework.PLAIN_JAVA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%s", "%s --server.port=8080", "\"%s\" foo bar"})
    @DisplayName("Should detect an absolute framework jar without a working directory")
    void shouldDetectAbsoluteJar(String commandTemplate, @TempDir Path tempDir) throws IOException {
        Path app = tempDir.resolve("app.jar");
        try (JarOutputStream ignored =
                new JarOutputStream(Files.newOutputStream(app), manifest())) {
            // The manifest is sufficient for framework detection.
        }

        assertThat(FrameworkDetector.detect(commandTemplate.formatted(app), "app.jar", null))
                .isEqualTo(Framework.SPRING_BOOT);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\"app with spaces.jar\" --server.port=8080",
                "-jar \"app with spaces.jar\" foo",
                "java -jar \"app with spaces.jar\" foo"
            })
    @DisplayName("Should preserve spaces in quoted jar paths before program arguments")
    void shouldDetectQuotedJarWithArguments(String displayName, @TempDir Path tempDir)
            throws IOException {
        Path app = tempDir.resolve("app with spaces.jar");
        try (JarOutputStream ignored =
                new JarOutputStream(Files.newOutputStream(app), manifest())) {
            // The manifest is sufficient for framework detection.
        }

        assertThat(FrameworkDetector.detect(displayName, "", tempDir))
                .isEqualTo(Framework.SPRING_BOOT);
    }

    @ParameterizedTest
    @CsvSource({"Quarkus-Version, QUARKUS", "Micronaut-Version, MICRONAUT"})
    @DisplayName("Should detect other framework manifests when jar display names include arguments")
    void shouldDetectOtherFrameworkJarsWithArguments(
            String attributeName, Framework expected, @TempDir Path tempDir) throws IOException {
        Path app = tempDir.resolve("app.jar");
        try (JarOutputStream ignored =
                new JarOutputStream(Files.newOutputStream(app), manifest(attributeName))) {
            // The manifest is sufficient for framework detection.
        }

        assertThat(FrameworkDetector.detect("app.jar foo bar", "", tempDir)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "com.example.Main app.jar",
                "com.example.Main \"app.jar\"",
                "app.jar.bak foo",
                "\"app.jar foo"
            })
    @DisplayName("Should not inspect jar paths found only in program arguments or malformed tokens")
    void shouldIgnoreNonJarCommandTokens(String displayName, @TempDir Path tempDir)
            throws IOException {
        Path app = tempDir.resolve("app.jar");
        try (JarOutputStream ignored =
                new JarOutputStream(Files.newOutputStream(app), manifest())) {
            // The manifest is sufficient for framework detection.
        }

        assertThat(FrameworkDetector.detect(displayName, "", tempDir))
                .isEqualTo(Framework.PLAIN_JAVA);
    }

    private static Manifest manifest() {
        return manifest("Spring-Boot-Version");
    }

    private static Manifest manifest(String attributeName) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue(attributeName, "1.0");
        return manifest;
    }
}
