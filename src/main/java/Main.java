import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
import config.Rebalance;
import dao.Dao;
import dao.FileUtil;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeMiddle;
import simulation.*;

import java.lang.reflect.Array;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Execute all instances from test cases in json file
 */
public class Main {


    public static void main(String[] args) {

        InstanceConfig instanceSettings = Config.createInstanceFrom(args[0]);

        // Vary test case parameters
        for (boolean sortWaitingUsersByClass : instanceSettings.getSortWaitingUsersByClassArray()) {
            for (int timeHorizon : instanceSettings.getTimeHorizonArray()) {
                for (int maxRequestsIteration : instanceSettings.getMaxRequestsIterationArray()) {
                    for (int timeWindow : instanceSettings.getTimeWindowArray()) {
                        for (int vehicleMaxCapacity : instanceSettings.getVehicleMaxCapacityArray()) {
                            for (int initialFleet : instanceSettings.getInitialFleetArray()) {
                                for (boolean isAllowedToHire : instanceSettings.getAllowVehicleHiringArray()) {
                                    for (boolean isAllowedToLowerServiceLevel : instanceSettings.getAllowServiceDeteriorationArray()) {

                                        // If can hire than service level have to be lowered
                                        if (isAllowedToHire != isAllowedToLowerServiceLevel) continue;

                                        for (int contractDuration : instanceSettings.getContractDurationArray()) {

                                            for (CustomerBaseConfig customerBaseSettings : instanceSettings.getCustomerBaseSettingsArray()) {
                                                // Update global class configuration to run current test case
                                                Config.getInstance().qosDic = customerBaseSettings.qosDic;

                                                for (boolean allowRebalancing : instanceSettings.getAllowRebalancingArray()) {

                                                    List<Rebalance> allRebalancingSettings = new ArrayList<>();
                                                    List<Matching> allMatchingSettings = new ArrayList<>();


                                                    if (!allowRebalancing) {
                                                        Rebalance rebalanceSettings = new Rebalance(
                                                                false,
                                                                false,
                                                                false,
                                                                false,
                                                                Rebalance.METHOD_HEURISTIC,
                                                                false,
                                                                false
                                                        );

                                                        allRebalancingSettings.add(rebalanceSettings);
                                                    } else {
                                                        allRebalancingSettings = instanceSettings.getListRebalanceSettings();
                                                    }
                                                    for (Rebalance rebalanceSettings : allRebalancingSettings) {

                                                        allMatchingSettings.add(new Matching(
                                                                Matching.METHOD_OPTIMAL,
                                                                isAllowedToLowerServiceLevel,
                                                                customerBaseSettings,
                                                                contractDuration,
                                                                rebalanceSettings,
                                                                isAllowedToHire));

                                                        for (Matching matchingSettings : allMatchingSettings) {

                                                            Instant before = Instant.now();

                                                            // Create FCFS simulation
                                                            Simulation simulation = new SimulationFCFS(
                                                                    instanceSettings.getInstanceName(),
                                                                    initialFleet,
                                                                    vehicleMaxCapacity,
                                                                    maxRequestsIteration,
                                                                    timeWindow,
                                                                    timeHorizon,
                                                                    allowRebalancing,
                                                                    contractDuration,
                                                                    isAllowedToHire,
                                                                    isAllowedToLowerServiceLevel,
                                                                    sortWaitingUsersByClass,
                                                                    customerBaseSettings.serviceRateLabel,
                                                                    customerBaseSettings.customerSegmentationLabel,
                                                                    rebalanceSettings,
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
        }
    }
}