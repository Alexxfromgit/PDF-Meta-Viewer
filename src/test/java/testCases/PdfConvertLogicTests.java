package testCases;

import converters.ConvertJavaMapToJson;
import extractor.PDFExtractor;
import extractor.PdfExtractionException;
import org.testng.annotations.Test;
import repository.DocumentRecord;
import repository.DocumentRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PdfConvertLogicTests {
    private static final Path SAMPLE_PDF = Path.of("src/test/resources/Amazon-S3-Tutorial.pdf");

    @Test(groups = "smoke")
    public void extractsMetadataAndTextFromPdf() throws PdfExtractionException {
        DocumentRecord record = new PDFExtractor().extract(SAMPLE_PDF);

        assertThat(record.getContent()).isNotBlank();
        assertThat(record.getMetadata()).isNotEmpty();
        assertThat(record.getSource().getFileName().toString()).isEqualTo("Amazon-S3-Tutorial.pdf");
        assertThat(record.getFileSize()).isPositive();
    }

    @Test
    public void rejectsNonPdfFilesBeforeParsing() throws IOException {
        Path file = Files.createTempFile("pdf-meta-viewer-", ".txt");
        try {
            assertThatThrownBy(() -> new PDFExtractor().extract(file))
                    .isInstanceOf(PdfExtractionException.class)
                    .hasMessage("Only PDF files can be analyzed.");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void createsReadableAndStableJson() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("z-last", "second");
        metadata.put("a-first", "first");

        String json = new ConvertJavaMapToJson().convertMapToJson(metadata);

        assertThat(json).contains(System.lineSeparator());
        assertThat(json).containsSubsequence("a-first", "z-last");
    }

    @Test
    public void exportsStructuredMetadataWithoutDocumentText() throws Exception {
        DocumentRecord record = new PDFExtractor().extract(SAMPLE_PDF);
        Path directory = Files.createTempDirectory("pdf-meta-viewer-export-");
        Path destination = directory.resolve("metadata.json");
        try {
            DocumentRepository.export(record, destination, false);

            String json = Files.readString(destination);
            assertThat(json)
                    .contains("\"source\" : \"Amazon-S3-Tutorial.pdf\"")
                    .contains("\"metadata\"")
                    .doesNotContain("\"content\"");
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(directory);
        }
    }
}
