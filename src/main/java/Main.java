import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeMiddle;
import simulation.Simulation;
import simulation.Solution;
import simulation.matching.Matching;
import simulation.matching.MatchingOptimalServiceLevelAndHire;
import simulation.matching.RideMatchingStrategy;
import simulation.matching.SimulationFCFS;
import simulation.rebalancing.Rebalance;
import simulation.rebalancing.RebalanceStrategy;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Execute all instances from test cases in json file
 */
public class Main {


    public static void main(String[] args) {
        String configFilePath = args[0];
        InstanceConfig instanceSettings;
        try {

            instanceSettings = Config.createInstanceFrom(configFilePath);


            // Vary test case parameters
            for (Date earliestTime : instanceSettings.getEarliestTimeArray()) {
                Config.getInstance().setEarliestTime(earliestTime);
                for (int timeHorizon : instanceSettings.getTimeHorizonArray()) {
                    for (int maxRequestsIteration : instanceSettings.getMaxRequestsIterationArray()) {
                        for (int timeWindow : instanceSettings.getTimeWindowArray()) {
                            for (int vehicleMaxCapacity : instanceSettings.getVehicleMaxCapacityArray()) {
                                for (int initialFleet : instanceSettings.getInitialFleetArray()) {
                                    for (boolean isAllowedToHire : instanceSettings.getAllowVehicleHiringArray()) {
                                        for (boolean isAllowedToDisplaceRequests : instanceSettings.getAllowRequestDisplacementArray()) {
                                            for (int contractDuration : instanceSettings.getContractDurationArray()) {
                                                for (CustomerBaseConfig customerBaseSettings : instanceSettings.getCustomerBaseSettingsArray()) {

                                                    // Update global class configuration to run current test case
                                                    Config.getInstance().updateQosDic(customerBaseSettings.qosDic);

                                                    Rebalance rebalancingSettings = new Rebalance();

                                                    for (RebalanceStrategy rebalanceStrategy : instanceSettings.getRebalancingMethods()) {

                                                        rebalancingSettings.setStrategy(rebalanceStrategy);

                                                        for (RideMatchingStrategy matchingMethod : instanceSettings.getMatchingMethods()) {

                                                            Matching matchingSettings = new Matching(
                                                                    customerBaseSettings,
                                                                    contractDuration,
                                                                    rebalancingSettings,
                                                                    isAllowedToHire && matchingMethod instanceof MatchingOptimalServiceLevelAndHire,
                                                                    isAllowedToDisplaceRequests);


                                                            matchingSettings.setStrategy(matchingMethod);

                                                            Instant before = Instant.now();

                                                            //TODO instance objects generating solution objects
                                                            // Create FCFS simulation
                                                            Simulation simulation = new SimulationFCFS(
                                                                    instanceSettings.getInstanceName(),
                                                                    instanceSettings.getMaxTimeToReachRegionCenter(),
                                                                    initialFleet,
                                                                    vehicleMaxCapacity,
                                                                    maxRequestsIteration,
                                                                    timeWindow,
                                                                    timeHorizon,
                                                                    contractDuration,
                                                                    isAllowedToHire,
                                                                    customerBaseSettings.serviceRateLabel,
                                                                    customerBaseSettings.customerSegmentationLabel,
                                                                    rebalancingSettings,
                                                                    matchingSettings);

                                                            // Run simulation
                                                            simulation.run();

                                                            // Reset classes for next iteration
                                                            Dao.getInstance().resetRecords();
                                                            User.reset();
                                                            Vehicle.reset();
                                                            Node.reset();
                                                            NodeMiddle.reset();
                                                            Visit.reset();
                                                            Simulation.reset();
                                                            Solution.reset();

                                                            Instant after = Instant.now();
                                                            Duration duration = Duration.between(before, after);
                                                            System.out.println("Duration:" + duration.toMillis());
                                                        }
                                                    }
                                                }
                                                Config.reset();
                                            }
                                        }

                                    }
                                }

                            }
                        }
                    }

                }
            }
        } catch (IOException e) {
            System.out.println("Error! Cannot read " + configFilePath);
            e.printStackTrace();
        }
    }
}
