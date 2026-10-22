package program;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;
import support.TemporaryFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.*;

public class PackagedCliIT extends TemporaryFiles {
    @Test
    public void shadedJarContainsVersionAndDesktopEntryPoint() throws Exception {
        try (JarFile jar = new JarFile("target/pdf-meta-viewer.jar")) {
            var attributes = jar.getManifest().getMainAttributes();
            assertThat(attributes.getValue("Main-Class")).isEqualTo("gui.MetaViewerForm");
            assertThat(attributes.getValue("Implementation-Version"))
                    .isEqualTo(System.getProperty("pdfMetaViewer.version"));
            assertThat(jar.getJarEntry("META-INF/services/org.apache.tika.parser.Parser")).isNotNull();
        }
    }

    @Test
    public void shadedJarRunsExtractionAndExportWithOnlyBundledDependencies() throws Exception {
        Path destination = directory.resolve("output with spaces.json");
        assertThat(invoke("src/test/resources/Amazon-S3-Tutorial.pdf", destination.toString())).isZero();
        var json = new ObjectMapper().readTree(Files.readString(destination));
        assertThat(json.get("metadata").get("Content-Type").asText()).isEqualTo("application/pdf");
        assertThat(json.has("content")).isFalse();
    }

    @Test
    public void processReturnsDocumentedExitCodes() throws Exception {
        assertThat(invoke()).isEqualTo(2);
        assertThat(invoke(directory.resolve("missing.pdf").toString())).isEqualTo(1);
    }

    private int invoke(String... args) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Djava.awt.headless=true", "-cp", "target/pdf-meta-viewer.jar", "program.Main"));
        command.addAll(List.of(args));
        Path log = directory.resolve("process.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("CLI must finish within 30 seconds").isTrue();
            return process.exitValue();
        } finally {
            process.destroyForcibly();
        }
    }
}
