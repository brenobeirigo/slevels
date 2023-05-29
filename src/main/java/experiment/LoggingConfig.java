package experiment;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record LoggingConfig(
        @JsonProperty("info_level") String info_level,
        @JsonProperty("info_handling") Map<String, Boolean> info_handling,
        @JsonProperty("instances_folder") String instances_folder){};