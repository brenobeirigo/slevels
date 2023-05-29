package config;


import com.fasterxml.jackson.annotation.JsonProperty;

public record MatchingConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("rtv_max_vehicle_capacity")
        int maxVehicleCapacityRTV,
        @JsonProperty("mip_time_limit")
        double mipTimeLimit,
        @JsonProperty("rtv_vehicle_timeout")
        double timeoutVehicleRTV,
        @JsonProperty("mip_gap")
        double mipGap,
        @JsonProperty("max_edges_rv")
        int maxEdgesRV,
        @JsonProperty("max_edges_rr")
        int maxEdgesRR,
        @JsonProperty("rejection_penalty")
        int rejectionPenalty,
        @JsonProperty("objectives")
        String[] orderedListOfObjectiveLabels,
        @JsonProperty("pd_generation_strategy")
        PDPGenerationStrategy PDPStrategy){};

record PDPGenerationStrategy(
        @JsonProperty("name")
        String name,
        @JsonProperty("precalculated_permutations_file")
        String permutationFile,
        @JsonProperty("max_sequences")
        int maxSequences
){};
