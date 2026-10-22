package extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.*;

/** Exercises the runtime after Shade has merged dependency resources. */
public class TikaPackagedIT {
    @Test
    public void retainsBothCoreAndPdfParserComponentIndexes() throws Exception {
        try (JarFile jar = new JarFile("target/pdf-meta-viewer.jar")) {
            var entry = jar.getJarEntry("META-INF/tika/parsers.idx");
            assertThat(entry).isNotNull();
            try (var input = jar.getInputStream(entry)) {
                assertThat(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                        .contains("default-parser=org.apache.tika.parser.DefaultParser:")
                        .contains("pdf-parser=org.apache.tika.parser.pdf.PDFParser:");
            }
        }
    }

    @Test(timeOut = 60000)
    public void packagedRuntimeExtractsEmbeddedTextAndExportsCanonicalMetadata() throws Exception {
        Path directory = Files.createTempDirectory("tika-packaged-");
        try {
            Path pdf = directory.resolve("attachment with spaces.pdf");
            TikaMigrationTests.writePdfWithEmbeddedDocument(pdf);
            Path log = directory.resolve("process.log");
            // The probe adds only its own compiled class; every app/library class comes from the JAR.
            String separator = System.getProperty("path.separator");
            Path probeDirectory = directory.resolve("probe/extractor");
            Files.createDirectories(probeDirectory);
            Files.copy(Path.of("target/test-classes/extractor/TikaRuntimeProbe.class"),
                    probeDirectory.resolve("TikaRuntimeProbe.class"));
            String classpath = directory.resolve("probe") + separator + Path.of("target/pdf-meta-viewer.jar").toAbsolutePath();
            invoke(log, classpath, "extractor.TikaRuntimeProbe", pdf.toString());
            Path output = directory.resolve("export.json");
            invoke(log, Path.of("target/pdf-meta-viewer.jar").toAbsolutePath().toString(),
                    "program.Main", pdf.toString(), output.toString());
            var json = new ObjectMapper().readTree(Files.readString(output));
            assertThat(json.size()).isEqualTo(2);
            assertThat(json.get("source").asText()).isEqualTo(pdf.getFileName().toString());
            assertThat(json.get("metadata").get("Content-Type").asText()).isEqualTo("application/pdf");
            assertThat(json.get("metadata").has("pdf:pdf-version")).isTrue();
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void invoke(Path log, String classpath, String mainClass, String... args) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Djava.awt.headless=true", "-cp", classpath, mainClass));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(25, TimeUnit.SECONDS)).as("Packaged JVM must finish").isTrue();
            assertThat(process.exitValue()).as(Files.readString(log)).isZero();
        } finally {
            process.destroyForcibly();
        }
    }
}
