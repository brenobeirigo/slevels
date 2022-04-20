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
    public static final String METHOD_OPTIMAL_ENFORCE_SL_HIRE = "method_optimal_enforce_sl_and_hire";
    protected Map<String, Long> runTimes;
    protected int contractDuration;
    protected Rebalance rebalanceUtil;
    protected boolean isAllowedToHire;
    protected CustomerBaseConfig customerBaseSettings;
    protected RideMatchingStrategy strategy;
    private int maxVehicleCapacity;

    private boolean allowUserDisplacement;

    public Matching(boolean isAllowedToLowerServiceLevel, int contractDuration, Rebalance rebalanceSettings, boolean isAllowedToHire) {

    }

    public Matching(CustomerBaseConfig customerBaseConfig,
                    int contractDuration,
                    Rebalance rebalanceUtil,
                    boolean isAllowedToHire,
                    boolean allowUserDisplacement) {
        this.customerBaseSettings = customerBaseConfig;
        this.contractDuration = contractDuration;
        this.rebalanceUtil = rebalanceUtil;
        this.isAllowedToHire = isAllowedToHire;
        this.allowUserDisplacement = allowUserDisplacement;
    }


    public ResultAssignment executeStrategy(int currentTime, Set<User> setUnassignedRequests, Set<Vehicle> listVehicles) {

        Set<User> allRequestsInOutVehicles = new HashSet<>(setUnassignedRequests);
        System.out.println(String.format("# Matching - %4d unassigned users.", allRequestsInOutVehicles.size()));

        if (allowUserDisplacement) {
            allRequestsInOutVehicles.addAll(Vehicle.getRequestsFrom(listVehicles));
        }

        Set<Vehicle> hired = new HashSet<>();
        if (isAllowedToHire) {
            Hiring hiring = new HiringFromCenters(this.contractDuration);
            hired = hiring.hire(setUnassignedRequests, currentTime);
        }

        ResultAssignment result = strategy.match(currentTime, allRequestsInOutVehicles, listVehicles, hired, this);
        System.out.printf("time=%4d, requests=%4d, vehicles=%4d, hired(current period)=%4d, hired(kept):=%4d", currentTime, setUnassignedRequests.size(), listVehicles.size(), hired.size(), result.getVehiclesHired().size());

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