package model.demand;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ServiceLevel(
        @JsonProperty("label")
        String label,
        @JsonProperty("priority")
        int priority,
        @JsonProperty("pickup_delay_sec")
        int pickupDelaySec,
        @JsonProperty("trip_delay_sec")
        int tripDelaySec,
        @JsonProperty("sharing_preference")
        boolean sharing){}
