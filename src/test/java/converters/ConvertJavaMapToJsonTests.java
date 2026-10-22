package converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class ConvertJavaMapToJsonTests {
    private final ConvertJavaMapToJson converter = new ConvertJavaMapToJson();

    @Test
    public void roundTripsEscapedCharactersAndUnicode() throws Exception {
        Map<String, String> values = Map.of("quoted\"key", "tab\tline\nslash\\ Привіт 中文");
        assertThat(new ObjectMapper().readTree(converter.convertMapToJson(values)))
                .isEqualTo(new ObjectMapper().valueToTree(values));
        assertThat(converter.convertMapToJson(Map.of())).isEqualTo("{ }");
    }

    @Test
    public void rejectsNullMapAndWrapsSerializationErrors() {
        assertThatThrownBy(() -> converter.convertMapToJson(null))
                .isInstanceOf(NullPointerException.class).hasMessage("mapToConvert");
        Map<String, String> invalid = new HashMap<>();
        invalid.put(null, "value");
        assertThatThrownBy(() -> converter.convertMapToJson(invalid))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Metadata could not be converted to JSON");
    }
}
