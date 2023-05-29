package model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VehicleGroup(
        @JsonProperty("n_vehicles")
        int nVehicles,
        @JsonProperty("capacity")
        int capacity) {
}
