package simulation.matching;

import config.CustomerBaseConfig;
import dao.Logging;
import model.demand.User;
import model.Vehicle;
import model.visit.VisitObj;
import simulation.hiring.Hiring;
//import simulation.hiring.HiringFromCenters;
import simulation.rebalancing.Rebalance;

import java.util.*;
import java.util.stream.Collectors;

public class Matching {
    public static final String METHOD_OPTIMAL = "method_optimal";
    public static final String METHOD_OPTIMAL_LEARN = "optlearn";
    public static final String METHOD_GREEDY = "method_greedy";
    public static final String METHOD_FCFS = "method_fcfs";
    public static final String METHOD_OPTIMAL_ENFORCE_SL = "method_optimal_enforce_sl";
    public static final String METHOD_OPTIMAL_ENFORCE_SL_HIRE = "method_optimal_enforce_sl_and_hire";
//    protected Map<String, Long> runTimes;
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
        Logging.logger.info("# Matching - {} unassigned users.", allRequestsInOutVehicles.size());

        if (allowUserDisplacement) {
            List<User> assignedUsers = Vehicle.getRequestsFrom(listVehicles);
            allRequestsInOutVehicles.addAll(assignedUsers);
            Logging.logger.info(
                    "# Matching - {} displaced users.",
                    assignedUsers.size());
        }
        Logging.logger.info(
                "# Matching - {} users to be matched.",
                allRequestsInOutVehicles.size());

        Set<Vehicle> hired = new HashSet<>();
        if (isAllowedToHire) {
//            Hiring hiring = new HiringFromCenters(this.contractDuration);
//            hired = hiring.hire(setUnassignedRequests, currentTime);
        }

        ResultAssignment result = rideMatchingStrategy.match(currentTime, allRequestsInOutVehicles, listVehicles, hired);
        Logging.logger.info(
                "# Matching - Time step={}, #Requests={}, #Vehicles={}, #Hired(current period)={}, #Hired(kept)={}",
                currentTime,
                setUnassignedRequests.size(),
                listVehicles.size(),
                hired.size(),
                result.getVehiclesHired().size());


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

    public Rebalance getRebalanceConfig() {

        return this.rebalanceUtil;
    }
}