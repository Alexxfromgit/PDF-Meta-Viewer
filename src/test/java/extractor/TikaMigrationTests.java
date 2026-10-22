package extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import repository.DocumentRecord;
import repository.DocumentRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** Contracts exercised before and after the Tika 3 to 4 migration. */
public class TikaMigrationTests {
    private Path directory;
    private final PDFExtractor extractor = new PDFExtractor();

    @BeforeMethod(alwaysRun = true)
    public void createDirectory() throws Exception {
        directory = Files.createTempDirectory("tika-migration-");
    }

    @AfterMethod(alwaysRun = true)
    public void removeDirectory() throws Exception {
        if (directory != null) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    public void preservesDocumentFieldsTextAndJsonEnvelope() throws Exception {
        Path pdf = directory.resolve("Résumé.PDF");
        try (PDDocument document = textDocument("Migration document text")) {
            document.getDocumentInformation().setTitle("Résumé & <metadata>");
            document.getDocumentInformation().setAuthor("Migration Author");
            document.save(pdf.toFile());
        }
        DocumentRecord record = extractor.extract(pdf);
        assertThat(record.getMetadata()).containsEntry("Content-Type", "application/pdf")
                .containsEntry("dc:title", "Résumé & <metadata>")
                .containsEntry("dc:creator", "Migration Author");
        assertThat(record.getSource()).isEqualTo(pdf.toAbsolutePath());
        assertThat(record.getFileSize()).isEqualTo(Files.size(pdf));
        assertThat(record.getContent()).contains("Migration document text");
        assertThat(record.isContentTruncated()).isFalse();
        Path output = directory.resolve("export.json");
        ObjectMapper mapper = new ObjectMapper();
        for (boolean includeContent : new boolean[]{false, true}) {
            DocumentRepository.export(record, output, includeContent);
            var json = mapper.readTree(Files.readString(output));
            assertThat(json.size()).isEqualTo(includeContent ? 4 : 2);
            assertThat(json.get("source").asText()).isEqualTo("Résumé.PDF");
            assertThat(json.get("metadata").get("dc:title").asText()).isEqualTo("Résumé & <metadata>");
            assertThat(json.has("content")).isEqualTo(includeContent);
            assertThat(json.has("contentTruncated")).isEqualTo(includeContent);
            if (includeContent) {
                assertThat(json.get("content").asText()).isEqualTo(record.getContent());
                assertThat(json.get("contentTruncated").asBoolean()).isFalse();
            }
        }
        // A second parse must not retain a handle to the previous file or its parser state.
        assertThat(extractor.extract(pdf).getMetadata()).containsEntry("dc:title", "Résumé & <metadata>");
        Files.delete(pdf);
    }

    @Test
    public void includesEmbeddedPdfTextAlongsideParentText() throws Exception {
        Path pdf = directory.resolve("with-attachment.pdf");
        writePdfWithEmbeddedDocument(pdf);
        assertThat(extractor.extract(pdf).getContent())
                .contains("Parent migration text", "Embedded migration text");
    }

    @Test
    public void handlesBlankPdfWithoutTextOrExternalOcr() throws Exception {
        Path pdf = directory.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        DocumentRecord record = extractor.extract(pdf);
        assertThat(record.getContent()).isBlank();
        assertThat(record.isContentTruncated()).isFalse();
        assertThat(record.getMetadata()).containsEntry("Content-Type", "application/pdf");
    }

    @Test
    public void retainsFriendlyEncryptedAndMalformedFileFailures() throws Exception {
        Path pdf = directory.resolve("encrypted.pdf");
        try (PDDocument document = textDocument("Protected text")) {
            document.protect(new StandardProtectionPolicy("owner", "reader", new AccessPermission()));
            document.save(pdf.toFile());
        }
        assertThatThrownBy(() -> extractor.extract(pdf)).isInstanceOf(PdfExtractionException.class)
                .hasMessage("The file is encrypted, damaged, or not a supported PDF.");
        Path damaged = Files.writeString(directory.resolve("damaged.pdf"), "%PDF-1.7\nbroken");
        Path empty = Files.createFile(directory.resolve("empty.pdf"));
        for (Path invalid : List.of(damaged, empty)) {
            assertThatThrownBy(() -> extractor.extract(invalid))
                    .isInstanceOf(PdfExtractionException.class).hasCauseInstanceOf(Exception.class);
        }
        assertThatThrownBy(() -> extractor.extract(directory.resolve("missing.pdf")))
                .isInstanceOf(PdfExtractionException.class).hasMessage("The selected file no longer exists.");
    }

    @Test(timeOut = 60000)
    public void keepsPreviewBoundAndMetadataWhenTextIsTruncated() throws Exception {
        Path pdf = directory.resolve("large.pdf");
        try (PDDocument document = textDocument("Preview text ".repeat(850))) {
            PDPage first = document.getPage(0);
            for (int index = 0; index < 205; index++) {
                PDPage page = new PDPage();
                page.setResources(first.getResources());
                page.setContents(first.getContentStreams().next());
                document.addPage(page);
            }
            document.getDocumentInformation().setTitle("Large preview");
            document.save(pdf.toFile());
        }
        DocumentRecord record = extractor.extract(pdf);
        assertThat(record.getContent()).contains("Preview text").hasSizeLessThanOrEqualTo(2_000_000);
        assertThat(record.isContentTruncated()).isTrue();
        assertThat(record.getMetadata()).containsEntry("dc:title", "Large preview");
    }

    @Test(timeOut = 30000)
    public void isolatesConcurrentExtractionState() throws Exception {
        Path first = directory.resolve("first.pdf");
        Path second = directory.resolve("second.pdf");
        try (PDDocument document = textDocument("First independent document")) {
            document.save(first.toFile());
        }
        try (PDDocument document = textDocument("Second independent document")) {
            document.save(second.toFile());
        }
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<DocumentRecord>> tasks = List.of(() -> extractor.extract(first), () -> extractor.extract(second));
            var results = executor.invokeAll(tasks, 20, TimeUnit.SECONDS);
            assertThat(results.get(0).get().getContent()).contains("First independent document")
                    .doesNotContain("Second independent document");
            assertThat(results.get(1).get().getContent()).contains("Second independent document")
                    .doesNotContain("First independent document");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(groups = "tika4")
    public void exposesCanonicalTika4MetadataKeys() throws Exception {
        Path pdf = directory.resolve("canonical.pdf");
        try (PDDocument document = textDocument("Canonical metadata")) {
            document.save(pdf.toFile());
        }
        Map<String, String> metadata = extractor.extract(pdf).getMetadata();
        assertThat(metadata).containsKey("pdf:pdf-version").doesNotContainKey("pdf:PDFVersion");
        assertThat(metadata.keySet()).noneMatch(key -> key.startsWith("X-TIKA:") || key.startsWith("access_permission:"));
        assertThat(metadata.keySet()).anyMatch(key -> key.startsWith("access-permission:"));
    }

    static void writePdfWithEmbeddedDocument(Path pdf) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PDDocument child = textDocument("Embedded migration text")) {
            child.save(bytes);
        }
        try (PDDocument parent = textDocument("Parent migration text")) {
            PDEmbeddedFile embedded = new PDEmbeddedFile(parent, new ByteArrayInputStream(bytes.toByteArray()));
            embedded.setSubtype("application/pdf");
            PDComplexFileSpecification spec = new PDComplexFileSpecification();
            spec.setFile("child.pdf");
            spec.setFileUnicode("child.pdf");
            spec.setEmbeddedFile(embedded);
            spec.setEmbeddedFileUnicode(embedded);
            PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
            tree.setNames(Map.of("child.pdf", spec));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(parent.getDocumentCatalog());
            names.setEmbeddedFiles(tree);
            parent.getDocumentCatalog().setNames(names);
            parent.save(pdf.toFile());
        }
    }

    private static PDDocument textDocument(String text) throws Exception {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
            stream.showText(text);
            stream.endText();
        }
        return document;
    }
}
