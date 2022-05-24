package simulation.matching;

import config.CustomerBaseConfig;
import model.User;
import model.Vehicle;
import model.VisitObj;
import simulation.hiring.Hiring;
import simulation.hiring.HiringFromCenters;
import simulation.rebalancing.Rebalance;

import java.util.*;
import java.util.stream.Collectors;

public class Matching {
    public static final String METHOD_OPTIMAL = "method_optimal";
    public static final String METHOD_OPTIMAL_LEARN = "method_optimal_learn";
    public static final String METHOD_GREEDY = "method_greedy";
    public static final String METHOD_FCFS = "method_fcfs";
    public static final String METHOD_OPTIMAL_ENFORCE_SL = "method_optimal_enforce_sl";
    public static final String METHOD_OPTIMAL_ENFORCE_SL_HIRE = "method_optimal_enforce_sl_and_hire";
    protected Map<String, Long> runTimes;
    protected int contractDuration;
    protected Rebalance rebalanceUtil;
    protected boolean isAllowedToHire;
    protected CustomerBaseConfig customerBaseSettings;
    protected RideMatchingStrategy rideMatchingStrategy;
    private int maxVehicleCapacity;

    private boolean allowUserDisplacement;

    public RideMatchingStrategy getRideMatchingStrategy() {
        return rideMatchingStrategy;
    }

    public Matching(CustomerBaseConfig customerBaseConfig,
                    int contractDuration,
                    Rebalance rebalanceUtil,
                    boolean isAllowedToHire,
                    boolean allowUserDisplacement,
                    RideMatchingStrategy rideMatchingStrategy) {
        this.customerBaseSettings = customerBaseConfig;
        this.contractDuration = contractDuration;
        this.rebalanceUtil = rebalanceUtil;
        this.isAllowedToHire = isAllowedToHire;
        this.allowUserDisplacement = allowUserDisplacement;
        this.rideMatchingStrategy = rideMatchingStrategy;
    }


    public ResultAssignment executeStrategy(int currentTime, Set<User> setUnassignedRequests, Set<Vehicle> listVehicles) {

        Set<User> allRequestsInOutVehicles = new HashSet<>(setUnassignedRequests);
        System.out.println(String.format("# Matching - %4d unassigned users.", allRequestsInOutVehicles.size()));

        if (allowUserDisplacement) {
            List<User> assignedUsers = Vehicle.getRequestsFrom(listVehicles);
            allRequestsInOutVehicles.addAll(assignedUsers);
            System.out.println(String.format("# Matching - %4d displaced users.", assignedUsers.size()));
        }
        System.out.println(String.format("# Matching - %4d users to be matched.", allRequestsInOutVehicles.size()));

        Set<Vehicle> hired = new HashSet<>();
        if (isAllowedToHire) {
            Hiring hiring = new HiringFromCenters(this.contractDuration);
            hired = hiring.hire(setUnassignedRequests, currentTime);
        }

        ResultAssignment result = rideMatchingStrategy.match(currentTime, allRequestsInOutVehicles, listVehicles, hired);
        System.out.println(String.format("# Matching - Time step=%4d, #Requests=%4d, #Vehicles=%4d, #Hired(current period)=%4d, #Hired(kept)=%4d", currentTime, setUnassignedRequests.size(), listVehicles.size(), hired.size(), result.getVehiclesHired().size()));


        for (User request:result.requestsDisplaced) {
            request.setCurrentVisit(null);
        }
        rideMatchingStrategy.realize(result.visitsOK.stream().map(VisitObj::getVisit).collect(Collectors.toSet()));

        return result;
    }

    public void setRideMatchingStrategy(RideMatchingStrategy matchingMethod) {
        this.rideMatchingStrategy = matchingMethod;
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