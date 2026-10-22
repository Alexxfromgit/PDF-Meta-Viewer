package extractor;

import java.nio.file.Path;

/** Launched with only this class plus the shaded application JAR on the classpath. */
public final class TikaRuntimeProbe {
    public static void main(String[] args) throws Exception {
        var record = new PDFExtractor().extract(Path.of(args[0]));
        if (!record.getContent().contains("Parent migration text")
                || !record.getContent().contains("Embedded migration text")) {
            throw new AssertionError("Packaged runtime lost parent or embedded PDF text");
        }
        if (!"application/pdf".equals(record.getMetadata().get("Content-Type"))
                || !record.getMetadata().containsKey("pdf:pdf-version")) {
            throw new AssertionError("Packaged runtime lost canonical PDF metadata");
        }
    }
}
