package program;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;
import support.TemporaryFiles;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class MainTests extends TemporaryFiles {
    private static final String SAMPLE = "src/test/resources/Amazon-S3-Tutorial.pdf";

    @Test
    public void printsUsageForMissingOrExcessArguments() {
        for (String[] args : new String[][]{ {}, {"a", "b", "c"} }) {
            Result result = run(args);
            assertThat(result.status()).isEqualTo(2);
            assertThat(result.err()).contains("Usage: program.Main");
            assertThat(result.out()).isEmpty();
        }
    }

    @Test
    public void printsMetadataOrExportsStructuredJson() throws Exception {
        Result result = run(SAMPLE);
        assertThat(result.status()).isZero();
        assertThat(result.out()).contains("Content-Type\tapplication/pdf");
        assertThat(result.err()).isEmpty();
        Path destination = directory.resolve("metadata.json");
        result = run(SAMPLE, destination.toString());
        assertThat(result.status()).isZero();
        assertThat(result.out()).contains("Exported", destination.toString());
        assertThat(new ObjectMapper().readTree(Files.readString(destination)).has("metadata")).isTrue();
    }

    @Test
    public void reportsExtractionExportAndInvalidPathFailures() {
        for (String[] args : new String[][]{
                {directory.resolve("missing.pdf").toString()},
                {SAMPLE, directory.resolve("missing/output.json").toString()},
                {"invalid\0.pdf"}
        }) {
            Result result = run(args);
            assertThat(result.status()).isEqualTo(1);
            assertThat(result.err()).isNotBlank();
            assertThat(result.out()).isEmpty();
        }
    }

    @Test
    public void mainReturnsNormallyOnSuccessfulInvocation() {
        PrintStream original = System.out;
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
            System.setOut(output);
            Main.main(new String[]{SAMPLE});
        } finally {
            System.setOut(original);
        }
    }

    private Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int status, String out, String err) { }
}
