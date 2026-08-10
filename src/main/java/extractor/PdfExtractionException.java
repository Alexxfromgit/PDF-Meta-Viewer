package extractor;

/**
 * Describes a PDF validation or parsing failure that can be shown to a user.
 */
public final class PdfExtractionException extends Exception {
    public PdfExtractionException(String message) {
        super(message);
    }

    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
