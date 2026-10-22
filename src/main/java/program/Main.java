package program;

import extractor.PDFExtractor;
import extractor.PdfExtractionException;
import repository.DocumentRecord;
import repository.DocumentRepository;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Small command-line entry point for automation and smoke testing.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 1 || args.length > 2) {
            err.println("Usage: program.Main <input.pdf> [output.json]");
            return 2;
        }

        try {
            DocumentRecord record = new PDFExtractor().extract(Path.of(args[0]));
            if (args.length == 2) {
                DocumentRepository.export(record, Path.of(args[1]), false);
                out.println("Exported " + record.getMetadata().size() + " fields to " + args[1]);
            } else {
                record.getMetadata().forEach((key, value) -> out.println(key + "\t" + value));
            }
            return 0;
        } catch (PdfExtractionException | IOException | InvalidPathException exception) {
            err.println(exception.getMessage());
            return 1;
        }
    }
}
