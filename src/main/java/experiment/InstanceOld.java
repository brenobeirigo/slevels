package experiment;

import config.CustomerBaseConfig;
import config.TimeConfig;
import model.FleetConfig;
import model.VehicleGroup;
import model.demand.DemandConfig;
import model.learn.LearningConfig;
import simulation.matching.Matching;
import simulation.matching.MatchingOptimalServiceLevelAndHire;
import simulation.matching.RideMatchingStrategy;
import simulation.rebalancing.Rebalance;
import simulation.rebalancing.RebalanceStrategy;

import java.util.Collections;
import java.util.Date;

public record InstanceOld(
        String methodName,
        Date earliestTime,
        String spMethod,
        TimeConfig timeConfig,
        Integer maxRequestsIteration,
        Double percentageRequestsIteration,
        Integer timeWindow,
        Integer vehicleMaxCapacity,
        Integer initialFleet,
        boolean isAllowedToHire,
        boolean isAllowedToDisplaceRequests,
        int contractDuration,
        CustomerBaseConfig customerBaseSettings,
        RebalanceStrategy rebalanceStrategy,
        RideMatchingStrategy matchingMethod,
        LearningConfig learningConfig) {

    public String getLabel() {

        String testCaseName = String.format(
                "%s%sST-%d_RH-%d_BA-%d_%s%sIF-%d_MC-%d_CS-%s_HC-%d",
                methodName != null ? String.format("IN-%s_", methodName) : "",
//                earliestTime != null ? String.format("SD-%s_", getDigitsFromDate(earliestTime)) : "",
                timeConfig.totalSimulationHorizonSec(),
                timeConfig.totalSimulationHorizonSec(),
                timeWindow,
                maxRequestsIteration != null ? String.format("MR-%d_", maxRequestsIteration) : "",
                percentageRequestsIteration != null ? String.format("PR-%4.3f_", percentageRequestsIteration) : "",
                initialFleet,
                vehicleMaxCapacity,
                customerBaseSettings);
        testCaseName += (isAllowedToDisplaceRequests ? "_SR-" + customerBaseSettings.serviceRateLabel : "");
        testCaseName += rebalanceStrategy != null ? rebalanceStrategy : "_RE-NO";
        testCaseName += matchingMethod != null ? matchingMethod : "";
        return testCaseName;
    }

    public DemandConfig demandConfig() {

        DemandConfig demandConfig = new DemandConfig(
                "aa",
                this.maxRequestsIteration(),
                this.percentageRequestsIteration(),
                null,
                null,
                null, true);
        return demandConfig;
    }

    public FleetConfig fleetConfig() {

        FleetConfig fleetConfig = new FleetConfig(
                Collections.singletonList(new VehicleGroup(20,4)),
                true);

        return fleetConfig;
    }

    public Matching routingConfig() {
        Rebalance rebalancingSettings = new Rebalance(this.rebalanceStrategy());
        Matching matchingSettings = new Matching(
                this.customerBaseSettings(),
                this.contractDuration(),
                rebalancingSettings,
                this.isAllowedToHire() && this.matchingMethod() instanceof MatchingOptimalServiceLevelAndHire,
                this.isAllowedToDisplaceRequests(), this.matchingMethod());
        return matchingSettings;
    }
};


