package experiment;

import config.CustomerBaseConfig;
import config.InstanceConfig;
import config.TimeConfig;
import model.learn.LearningConfig;
import simulation.matching.RideMatchingStrategy;
import simulation.rebalancing.RebalanceStrategy;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class InstanceUtil {

    public static List<InstanceOld> getInstanceListFrom(InstanceConfig instanceSettings) {
        List<InstanceOld> instances = new ArrayList<>();
        // Vary test case parameters
        for (Date earliestTime : instanceSettings.getEarliestTimeArray()) {
            for (String spMethod : instanceSettings.getShortestPathAlgorithm()) {
                for (TimeConfig timeConfig : instanceSettings.getTimeHorizonArray()) {
                    for (int maxRequestsIteration : instanceSettings.getMaxRequestsIterationArray()) {
                        for (double percentageRequestsIteration : instanceSettings.getPercentageRequestsIterationArray()) {
                            for (int timeWindow : instanceSettings.getTimeWindowArray()) {
                                for (int vehicleMaxCapacity : instanceSettings.getVehicleMaxCapacityArray()) {
                                    for (int initialFleet : instanceSettings.getInitialFleetArray()) {
                                        for (boolean isAllowedToHire : instanceSettings.getAllowVehicleHiringArray()) {
                                            for (boolean isAllowedToDisplaceRequests : instanceSettings.getAllowRequestDisplacementArray()) {
                                                for (int contractDuration : instanceSettings.getContractDurationArray()) {
                                                    for (CustomerBaseConfig customerBaseSettings : instanceSettings.getCustomerBaseSettingsArray()) {
                                                        for (RebalanceStrategy rebalanceStrategy : instanceSettings.getRebalancingMethods()) {
                                                            for (RideMatchingStrategy matchingMethod : instanceSettings.getMatchingMethods()) {
                                                                for (LearningConfig learningConfig : instanceSettings.getLearningConfigs()) {

                                                                    InstanceOld n = new InstanceOld(
                                                                            instanceSettings.getInstanceName(),
                                                                            earliestTime,
                                                                            spMethod,
                                                                            timeConfig,
                                                                            maxRequestsIteration,
                                                                            percentageRequestsIteration,
                                                                            timeWindow,
                                                                            vehicleMaxCapacity,
                                                                            initialFleet,
                                                                            isAllowedToHire,
                                                                            isAllowedToDisplaceRequests,
                                                                            contractDuration,
                                                                            customerBaseSettings,
                                                                            rebalanceStrategy,
                                                                            matchingMethod,
                                                                            learningConfig);

                                                                    instances.add(n);
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
                }
            }
        }
        return instances;
    }
}
