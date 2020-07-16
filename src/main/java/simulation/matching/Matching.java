package simulation.matching;

import config.CustomerBaseConfig;
import model.User;
import model.Vehicle;
import model.VehicleHired;
import simulation.hiring.Hiring;
import simulation.hiring.HiringFromCenters;
import simulation.rebalancing.Rebalance;

import java.util.*;
import java.util.stream.Collectors;

public class Matching {
    public static final String METHOD_OPTIMAL = "method_optimal";
    public static final String METHOD_GREEDY = "method_greedy";
    public static final String METHOD_FCFS = "method_fsfc";
    public static final String METHOD_OPTIMAL_ENFORCE_SL = "method_optimal_enforce_sl";
    protected Map<String, Long> runTimes;
    protected boolean isAllowedToLowerServiceLevel;
    protected int contractDuration;
    protected Rebalance rebalanceUtil;
    protected boolean isAllowedToHire;
    protected CustomerBaseConfig customerBaseSettings;
    protected RideMatchingStrategy strategy;
    private int maxVehicleCapacity;

    private boolean allowUserDisplacement;

    public Matching(boolean isAllowedToLowerServiceLevel, int contractDuration, Rebalance rebalanceSettings, boolean isAllowedToHire) {

    }

    public Matching(boolean isAllowedToLowerServiceLevel,
                    CustomerBaseConfig customerBaseConfig,
                    int contractDuration,
                    Rebalance rebalanceUtil,
                    boolean isAllowedToHire,
                    boolean allowUserDisplacement) {
        this.isAllowedToLowerServiceLevel = isAllowedToLowerServiceLevel;
        this.customerBaseSettings = customerBaseConfig;
        this.contractDuration = contractDuration;
        this.rebalanceUtil = rebalanceUtil;
        this.isAllowedToHire = isAllowedToHire;
        this.allowUserDisplacement = allowUserDisplacement;
    }


    public ResultAssignment executeStrategy(int currentTime, List<User> setUnassignedRequests, List<Vehicle> listVehicles) {

        List<User> allRequestsInOutVehicles = new ArrayList<>(setUnassignedRequests);

        if (allowUserDisplacement) {
            allRequestsInOutVehicles.addAll(Vehicle.getRequestsFrom(listVehicles));
        }

        Set<Vehicle> hired = new HashSet<>();
        if (isAllowedToHire) {
            Hiring hiring = new HiringFromCenters(this.contractDuration);
            hired = hiring.hire(setUnassignedRequests, currentTime);
        }

        System.out.println(String.format("time=%4d, requests=%4d, vehicles=%4d, hired=%4d", currentTime, setUnassignedRequests.size(), listVehicles.size(), hired.size()));
        ResultAssignment result = strategy.match(currentTime, allRequestsInOutVehicles, listVehicles, hired, this);
        strategy.realize(result.visitsOK, this.rebalanceUtil, currentTime);

        return result;
    }

    public void setStrategy(RideMatchingStrategy matchingMethod) {
        this.strategy = matchingMethod;
    }

    public int getMaxVehicleCapacity() {
        return maxVehicleCapacity;
    }

    public void setMaxVehicleCapacity(int maxVehicleCapacity) {
        this.maxVehicleCapacity = maxVehicleCapacity;
    }

    public boolean isAllowUserDisplacement() {
        return allowUserDisplacement;
    }
}