package repository;

import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class DocumentRecordTests {
    @Test
    public void takesImmutableSortedSnapshotAndNormalizesSource() {
        Map<String, String> values = new HashMap<>(Map.of("z", "last", "a", "first"));
        Path source = Path.of("folder", "..", "example.pdf");
        DocumentRecord record = new DocumentRecord(source, 123L, values, null, true);
        values.put("new", "changed");
        assertThat(record.getMetadata()).containsExactly(entry("a", "first"), entry("z", "last"));
        assertThatThrownBy(() -> record.getMetadata().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(record.getSource()).isEqualTo(source.toAbsolutePath().normalize());
        assertThat(record.getFileSize()).isEqualTo(123L);
        assertThat(record.getContent()).isEmpty();
        assertThat(record.isContentTruncated()).isTrue();
        assertThat(UUID.fromString(record.getId()).toString()).isEqualTo(record.getId());
        assertThat(new DocumentRecord(values, "text").getId()).isNotEqualTo(record.getId());
    }

    @Test
    public void rejectsMissingSourceAndMetadata() {
        assertThatThrownBy(() -> new DocumentRecord(null, 0, Map.of(), "", false))
                .isInstanceOf(NullPointerException.class).hasMessage("source");
        assertThatThrownBy(() -> new DocumentRecord(Path.of("a.pdf"), 0, null, "", false))
                .isInstanceOf(NullPointerException.class).hasMessage("metadata");
    }

    @Test
    public void compatibilityConstructorHasSafeDefaults() {
        DocumentRecord record = new DocumentRecord(Map.of("title", "Example"), "hello");
        assertThat(record.getSource().getFileName().toString()).isEqualTo("unknown.pdf");
        assertThat(record.getFileSize()).isZero();
        assertThat(record.getContent()).isEqualTo("hello");
        assertThat(record.isContentTruncated()).isFalse();
    }
}
