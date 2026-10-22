package repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;
import support.TemporaryFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class DocumentRepositoryTests extends TemporaryFiles {
    private final DocumentRecord record = new DocumentRecord(Path.of("private/folder/Résumé.pdf"), 42,
            Map.of("title", "Quotes \" \\ \n Привіт 中文"), "Full text\nwith Unicode: café", true);

    @Test
    public void roundTripsUnicodeAndOptionalContentWithoutLeakingSourcePath() throws Exception {
        Path destination = directory.resolve("metadata.json");
        DocumentRepository.export(record, destination, true);
        String json = Files.readString(destination);
        JsonNode parsed = new ObjectMapper().readTree(json);
        assertThat(parsed.size()).isEqualTo(4);
        assertThat(parsed.get("source").asText()).isEqualTo("Résumé.pdf");
        assertThat(parsed.get("metadata").get("title").asText()).isEqualTo(record.getMetadata().get("title"));
        assertThat(parsed.get("content").asText()).isEqualTo(record.getContent());
        assertThat(parsed.get("contentTruncated").asBoolean()).isTrue();
        assertThat(json).doesNotContain("private/folder").endsWith(System.lineSeparator());
        assertNoTemporaryFiles();
    }

    @Test
    public void replacesExistingExportAndOmitsBothContentFields() throws Exception {
        Path destination = Files.writeString(directory.resolve("existing.json"), "old data");
        DocumentRepository.storeMetadataRecord(record, destination.toString(), false);
        JsonNode parsed = new ObjectMapper().readTree(Files.readString(destination));
        assertThat(parsed.size()).isEqualTo(2);
        assertThat(parsed.has("content")).isFalse();
        assertThat(parsed.has("contentTruncated")).isFalse();
        assertNoTemporaryFiles();
    }

    @Test
    public void rejectsUnavailableParentAndNullArguments() {
        assertThatThrownBy(() -> DocumentRepository.export(record, directory.resolve("missing/a.json"), false))
                .isInstanceOf(java.io.IOException.class).hasMessage("The selected destination folder is unavailable.");
        assertThatThrownBy(() -> DocumentRepository.export(null, directory.resolve("a.json"), false))
                .isInstanceOf(NullPointerException.class).hasMessage("documentRecord");
        assertThatThrownBy(() -> DocumentRepository.export(record, null, false))
                .isInstanceOf(NullPointerException.class).hasMessage("destination");
    }

    @Test
    public void cleansTemporaryFileAfterFailedReplacementAndPreservesDestination() throws Exception {
        Path destination = Files.createDirectory(directory.resolve("occupied.json"));
        Path existing = Files.writeString(destination.resolve("keep.txt"), "keep me");
        assertThatThrownBy(() -> DocumentRepository.export(record, destination, true))
                .isInstanceOf(java.io.IOException.class);
        assertThat(Files.readString(existing)).isEqualTo("keep me");
        assertNoTemporaryFiles();
    }

    private void assertNoTemporaryFiles() throws Exception {
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".pdf-meta-viewer-"));
        }
    }
}
