package program;

import extractor.PDFExtractor;
import extractor.PdfExtractionException;
import repository.DocumentRecord;
import repository.DocumentRepository;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Small command-line entry point for automation and smoke testing.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: program.Main <input.pdf> [output.json]");
            System.exit(2);
        }

        try {
            DocumentRecord record = new PDFExtractor().extract(Path.of(args[0]));
            if (args.length == 2) {
                DocumentRepository.export(record, Path.of(args[1]), false);
                System.out.println("Exported " + record.getMetadata().size() + " fields to " + args[1]);
            } else {
                record.getMetadata().forEach((key, value) -> System.out.println(key + "\t" + value));
            }
        } catch (PdfExtractionException | IOException exception) {
            System.err.println(exception.getMessage());
            System.exit(1);
        }
    }
}
