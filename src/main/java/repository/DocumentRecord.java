package repository;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Immutable result of extracting a PDF document.
 */
public final class DocumentRecord {
    private final String id;
    private final Path source;
    private final long fileSize;
    private final Map<String, String> metadata;
    private final String content;
    private final boolean contentTruncated;

    public DocumentRecord(Path source,
                          long fileSize,
                          Map<String, String> metadata,
                          String content,
                          boolean contentTruncated) {
        this.id = UUID.randomUUID().toString();
        this.source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        this.fileSize = fileSize;
        this.metadata = Collections.unmodifiableMap(new TreeMap<>(
                Objects.requireNonNull(metadata, "metadata")));
        this.content = Objects.requireNonNullElse(content, "");
        this.contentTruncated = contentTruncated;
    }

    /**
     * Compatibility constructor retained for callers that do not track a source file.
     */
    public DocumentRecord(Map<String, String> metadata, String content) {
        this(Path.of("unknown.pdf"), 0, metadata, content, false);
    }

    public String getId() {
        return id;
    }

    public Path getSource() {
        return source;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getContent() {
        return content;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public boolean isContentTruncated() {
        return contentTruncated;
    }
}
