import config.*;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeMiddle;
import simulation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Execute all instances from test cases in json file
 */
public class Main {


    public static void main(String[] args) {

        InstanceConfig instanceSettings = Config.createInstanceFrom(args[0]);

        // Vary test case parameters
        for (Date earliestTime : instanceSettings.getEarliestTimeArray()) {
            Config.getInstance().setEarliestTime(earliestTime);
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
                                                Config.getInstance().updateQosDic(customerBaseSettings.qosDic);

                                                Rebalance rebalancingSettings = new Rebalance();

                                                for (RebalanceStrategy rebalanceStrategy : instanceSettings.getRebalancingMethods()) {

                                                    rebalancingSettings.setStrategy(rebalanceStrategy);
                                                    Matching matchingSettings = new Matching(
                                                            isAllowedToLowerServiceLevel,
                                                            customerBaseSettings,
                                                            contractDuration,
                                                            rebalancingSettings,
                                                            isAllowedToHire);

                                                    for (RideMatchingStrategy matchingMethod : instanceSettings.getMatchingMethods()) {

                                                        matchingSettings.setStrategy(matchingMethod);

                                                        Instant before = Instant.now();

                                                        // Create FCFS simulation
                                                        Simulation simulation = new SimulationFCFS(
                                                                instanceSettings.getInstanceName(),
                                                                initialFleet,
                                                                vehicleMaxCapacity,
                                                                maxRequestsIteration,
                                                                timeWindow,
                                                                timeHorizon,
                                                                contractDuration,
                                                                isAllowedToHire,
                                                                isAllowedToLowerServiceLevel,
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
    }
}
