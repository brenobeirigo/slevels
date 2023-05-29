package config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dao.FileUtil;
import dao.Logging;
import experiment.LoggingConfig;
import model.FleetConfig;
import model.demand.DemandConfig;
import simulation.matching.Matching;

import java.util.stream.Collectors;

import static simulation.Solution.getDigitsFromDate;

public class InstanceData {

    public String source;
    public String label;
    public MatchingConfig matchingConfig;
    public LoggingConfig loggingConfig;
    public TimeConfig timeConfig;
    public DemandConfig demandConfig;
    public NetworkConfig networkConfig;
    public FleetConfig fleetConfig;

    public String getLabel() {

        String instanceLabel = String.format("IN-%s_",label) +
                String.format("SD-%s_",getDigitsFromDate(timeConfig.startDateTime())) +
                String.format("ST-%s_", timeConfig.totalSimulationHorizonSec()) +
                String.format("TW-%s_", timeConfig.timeWindowSec()) +
                String.format("PR-%4.3f_", demandConfig.percentageRequests()) +
                String.format("IF-%s_", fleetConfig.vehicles().stream().map(vehicleGroup -> String.format("%d(%d)", vehicleGroup.nVehicles(), vehicleGroup.capacity())).collect(Collectors.joining())) +
                String.format("MT-%s", matchingConfig.name());
        return instanceLabel;
    }

    public InstanceData(String source) {
        this.source = source;
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        try {
            JsonNode config = mapper.readValue(FileUtil.readJson(source), JsonNode.class);

            label = config.get("label").asText();
            Logging.logger.info("Label = {}", label);

            loggingConfig = mapper.treeToValue(config.get("logging"), LoggingConfig.class);
            Logging.logger.info("Logging = {}", loggingConfig);

            timeConfig = mapper.treeToValue(config.get("time"), TimeConfig.class);
            Logging.logger.info("Time = {}", timeConfig);

            demandConfig = mapper.treeToValue(config.get("demand"), DemandConfig.class);
            Logging.logger.info("Demand = {}", demandConfig);

            networkConfig = mapper.treeToValue(config.get("network"), NetworkConfig.class);
            Logging.logger.info("Network = {}", networkConfig);

            fleetConfig = mapper.treeToValue(config.get("fleet"), FleetConfig.class);
            Logging.logger.info("Fleet = {}", fleetConfig);

            JsonNode method = config.get("routing");

            matchingConfig = getRideMatchingStrategy(method, mapper);
            Logging.logger.info("Matching = {}", matchingConfig);

        } catch (JsonProcessingException e) {
            Logging.logger.error("Can't read {}", source);
            e.printStackTrace();
        }

    }

    public static MatchingConfig getRideMatchingStrategy(JsonNode method, ObjectMapper mapper) throws JsonProcessingException {
        String matchingStrategyLabel = method.get("name").asText();
        Logging.logger.info("Strategy: {}", matchingStrategyLabel);
        if (Matching.METHOD_OPTIMAL_ENFORCE_SL_HIRE.equals(matchingStrategyLabel)) {
            return null;

        } else if (Matching.METHOD_OPTIMAL_ENFORCE_SL.equals(matchingStrategyLabel)) {
            return null;

        } else if (Matching.METHOD_OPTIMAL.equals(matchingStrategyLabel)) {
            return null;

        } else if (Matching.METHOD_OPTIMAL_LEARN.equals(matchingStrategyLabel)) {
            return mapper.treeToValue(method, MatchingConfig.class);

        } else if (Matching.METHOD_FCFS.equals(matchingStrategyLabel)) {
            return null;
        } else {
            Logging.logger.info("NO METHOD");
            return null;
        }
    }
}
