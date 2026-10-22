package extractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.testng.annotations.Test;
import repository.DocumentRecord;
import support.TemporaryFiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

public class PDFExtractorTests extends TemporaryFiles {
    private final PDFExtractor extractor = new PDFExtractor();

    @Test
    public void rejectsNullMissingAndDirectoryInputs() {
        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(PdfExtractionException.class).hasMessage("Choose a PDF file to continue.");
        assertThatThrownBy(() -> extractor.extract(directory.resolve("missing.pdf")))
                .isInstanceOf(PdfExtractionException.class).hasMessage("The selected file no longer exists.");
        assertThatThrownBy(() -> extractor.extract(directory))
                .isInstanceOf(PdfExtractionException.class).hasMessage("The selected file no longer exists.");
    }

    @Test
    public void acceptsUppercaseExtensionAndExtractsKnownMetadata() throws Exception {
        Path pdf = directory.resolve("Résumé.PDF");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.getDocumentInformation().setTitle("Résumé & <metadata>");
            document.getDocumentInformation().setAuthor("Test Author");
            document.save(pdf.toFile());
        }
        DocumentRecord record = extractor.extract(pdf);
        assertThat(record.getMetadata()).containsEntry("dc:title", "Résumé & <metadata>")
                .containsEntry("dc:creator", "Test Author");
        assertThat(record.getSource()).isEqualTo(pdf.toAbsolutePath());
        assertThat(record.getFileSize()).isEqualTo(Files.size(pdf));
        assertThat(record.getContent()).isBlank();
        assertThat(record.isContentTruncated()).isFalse();
    }

    @Test
    public void rejectsEmptyAndDamagedPdfs() throws Exception {
        Path empty = Files.createFile(directory.resolve("empty.pdf"));
        Path damaged = Files.writeString(directory.resolve("damaged.pdf"), "%PDF-1.7\nnot a PDF");
        for (Path pdf : new Path[]{empty, damaged}) {
            assertThatThrownBy(() -> extractor.extract(pdf))
                    .isInstanceOf(PdfExtractionException.class).hasCauseInstanceOf(Exception.class);
        }
    }

    @Test
    public void rejectsPasswordProtectedPdfWithFriendlyMessage() throws Exception {
        Path pdf = directory.resolve("encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner-secret", "reader-secret", new AccessPermission()));
            document.save(pdf.toFile());
        }
        assertThatThrownBy(() -> extractor.extract(pdf))
                .isInstanceOf(PdfExtractionException.class)
                .hasMessage("The file is encrypted, damaged, or not a supported PDF.");
    }

    @Test(timeOut = 60000)
    public void boundsLargeTextPreviewAndMarksItTruncated() throws Exception {
        Path pdf = directory.resolve("large.pdf");
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage first = new PDPage();
            document.addPage(first);
            try (PDPageContentStream stream = new PDPageContentStream(document, first)) {
                stream.beginText();
                stream.setFont(font, 10);
                stream.showText("Preview text ".repeat(850));
                stream.endText();
            }
            for (int index = 0; index < 205; index++) {
                PDPage page = new PDPage();
                page.setResources(first.getResources());
                page.setContents(first.getContentStreams().next());
                document.addPage(page);
            }
            document.save(pdf.toFile());
        }
        DocumentRecord record = extractor.extract(pdf);
        assertThat(record.isContentTruncated()).isTrue();
        assertThat(record.getContent().stripLeading().substring(0, 12)).isEqualTo("Preview text");
        assertThat(record.getContent().length()).isLessThanOrEqualTo(2_000_000);
        assertThat(record.getMetadata()).isNotEmpty();
    }

    @Test
    public void compatibilityApiExtractsAndExports() throws Exception {
        DocumentRecord record = extractor.getDocumentRecordFromImportedPDF(
                "src/test/resources/Amazon-S3-Tutorial.pdf");
        Path destination = directory.resolve("compatibility.json");
        extractor.storeDocumentRecordAs(record, destination.toString(), true);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(destination));
        assertThat(json.get("content").asText()).isEqualTo(record.getContent());
        assertThat(json.get("contentTruncated").asBoolean()).isFalse();
    }

    @Test
    public void compatibilityApiWrapsExtractionAndExportFailures() {
        assertThatThrownBy(() -> extractor.getDocumentRecordFromImportedPDF(directory.resolve("missing.pdf").toString()))
                .isInstanceOf(IllegalArgumentException.class).hasCauseInstanceOf(PdfExtractionException.class);
        assertThatThrownBy(() -> extractor.storeDocumentRecordAs(
                new DocumentRecord(java.util.Map.of(), ""), directory.resolve("missing/out.json").toString(), false))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(java.io.IOException.class);
    }
}
