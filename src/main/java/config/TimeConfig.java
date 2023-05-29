package config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;

public record TimeConfig(
        @JsonProperty("start_datetime")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        LocalDateTime startDateTime,
        @JsonProperty("total_simulation_horizon_sec")
        int totalSimulationHorizonSec,
        @JsonProperty("time_window_sec")
        int timeWindowSec) {

    LocalDateTime endDatetime() {
        return this.startDateTime.plusSeconds(totalSimulationHorizonSec);
    }
}
