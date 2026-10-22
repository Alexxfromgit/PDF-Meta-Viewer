package extractor;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import repository.DocumentRecord;
import repository.DocumentRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class PDFExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PDFExtractor.class);
    private static final int TEXT_PREVIEW_LIMIT = 2_000_000;

    public DocumentRecord extract(Path file) throws PdfExtractionException {
        Path source = validate(file);
        Metadata tikaMetadata = new Metadata();
        BodyContentHandler contentHandler = new BodyContentHandler(TEXT_PREVIEW_LIMIT);
        boolean contentTruncated = false;

        try (TikaInputStream input = TikaInputStream.get(source)) {
            ParseContext context = new ParseContext();
            Detector detector = new DefaultDetector();
            // Tika 4 no longer supplies these for concrete parsers. Preserve embedded PDF text.
            context.set(Detector.class, detector);
            context.set(Parser.class, new AutoDetectParser(detector));
            new PDFParser().parse(input, contentHandler, tikaMetadata, context);
        } catch (SAXException exception) {
            if (WriteLimitReachedException.isWriteLimitReached(exception)) {
                contentTruncated = true;
                LOGGER.info("Text preview for {} was limited to {} characters", source, TEXT_PREVIEW_LIMIT);
            } else {
                throw new PdfExtractionException("The PDF structure could not be read.", exception);
            }
        } catch (TikaException exception) {
            throw new PdfExtractionException("The file is encrypted, damaged, or not a supported PDF.", exception);
        } catch (IOException exception) {
            throw new PdfExtractionException("The PDF could not be opened. Check its permissions and try again.", exception);
        } catch (RuntimeException exception) {
            throw new PdfExtractionException("The PDF could not be analyzed.", exception);
        }

        return new DocumentRecord(
                source,
                fileSize(source),
                toMap(tikaMetadata),
                contentHandler.toString(),
                contentTruncated
        );
    }

    /**
     * Compatibility entry point for the original public API.
     */
    public DocumentRecord getDocumentRecordFromImportedPDF(String filePath) {
        try {
            return extract(Paths.get(filePath));
        } catch (PdfExtractionException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    public void storeDocumentRecordAs(DocumentRecord documentRecord, String filePath, boolean isContentIncluded) {
        try {
            DocumentRepository.storeMetadataRecord(documentRecord, filePath, isContentIncluded);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not export metadata", exception);
        }
    }

    private Path validate(Path file) throws PdfExtractionException {
        if (file == null) {
            throw new PdfExtractionException("Choose a PDF file to continue.");
        }

        Path source = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new PdfExtractionException("The selected file no longer exists.");
        }
        if (!Files.isReadable(source)) {
            throw new PdfExtractionException("The selected PDF is not readable.");
        }
        if (!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new PdfExtractionException("Only PDF files can be analyzed.");
        }
        return source;
    }

    private long fileSize(Path source) throws PdfExtractionException {
        try {
            return Files.size(source);
        } catch (IOException exception) {
            throw new PdfExtractionException("The PDF size could not be determined.", exception);
        }
    }

    private Map<String, String> toMap(Metadata metadata) {
        Map<String, String> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : metadata.names()) {
            String[] entries = metadata.getValues(name);
            values.put(name, entries.length == 1 ? entries[0] : String.join(", ", entries));
        }
        return values;
    }
}
