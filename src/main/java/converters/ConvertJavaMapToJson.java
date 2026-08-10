package converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;
import java.util.Objects;

public final class ConvertJavaMapToJson {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public String convertMapToJson(Map<String, String> mapToConvert) {
        Objects.requireNonNull(mapToConvert, "mapToConvert");
        try {
            return OBJECT_MAPPER.writeValueAsString(mapToConvert);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Metadata could not be converted to JSON", exception);
        }
    }
}
