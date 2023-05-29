package model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Comparator;
import java.util.List;

;
public record FleetConfig(
        @JsonProperty("vehicles")
        List<VehicleGroup> vehicles,
        @JsonProperty("rebalance")
        boolean rebalance) {
        public int maxVehicleCapacity(){
                return vehicles.stream().max(Comparator.comparingInt(VehicleGroup::capacity)).get().capacity();
        }
}