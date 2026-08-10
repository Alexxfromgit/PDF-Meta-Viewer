package repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DocumentRepository {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private DocumentRepository() {
    }

    public static void export(DocumentRecord documentRecord, Path destination, boolean includeContent)
            throws IOException {
        Objects.requireNonNull(documentRecord, "documentRecord");
        Objects.requireNonNull(destination, "destination");

        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("The selected destination folder is unavailable.");
        }

        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("source", documentRecord.getSource().getFileName().toString());
        exportData.put("metadata", documentRecord.getMetadata());
        if (includeContent) {
            exportData.put("content", documentRecord.getContent());
            exportData.put("contentTruncated", documentRecord.isContentTruncated());
        }

        Path temporary = Files.createTempFile(parent, ".pdf-meta-viewer-", ".tmp");
        try {
            String json = toJson(exportData);
            Files.writeString(temporary, json + System.lineSeparator(), StandardCharsets.UTF_8);
            moveIntoPlace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void storeMetadataRecord(DocumentRecord documentRecord,
                                           String filePath,
                                           boolean isContentIncluded) throws IOException {
        export(documentRecord, Paths.get(filePath), isContentIncluded);
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String toJson(Map<String, Object> values) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Metadata could not be converted to JSON", exception);
        }
    }
}
