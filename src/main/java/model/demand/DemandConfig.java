package model.demand;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public record DemandConfig(
        @JsonProperty("requests_file")
        String requestFile,
        @JsonProperty("max_number_of_trips")
        int maxNumberOfTrips,
        @JsonProperty("percentage_requests")
        double percentageRequests,
        @JsonProperty("service_rate")
        HashMap<String, Double> serviceRateScenarioMap,
        @JsonProperty("service_level")
        HashMap<String, ServiceLevel> serviceLevelMap,
        @JsonProperty("customer_segmentation")
        HashMap<String, Double> segmentationScenarioMap,
        @JsonProperty("allow_request_displacement")
        Boolean allowRequestDisplacement){
}