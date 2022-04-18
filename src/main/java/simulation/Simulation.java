package simulation;

import com.google.common.collect.Sets;
import config.Config;
import dao.Dao;
import helper.HelperIO;
import helper.MethodHelper;
import helper.Runtime;
import model.*;
import model.node.Node;
import simulation.matching.Matching;
import simulation.matching.ResultAssignment;
import simulation.rebalancing.Rebalance;
import simulation.rebalancing.RebalanceHeuristic;
import simulation.rebalancing.RebalanceOptimal;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Simulation {


    // Solution coming from simulation
    protected Solution sol; //Simulation solution

    /* Hiring, unbounding, and simulation.rebalancing */
    protected int contractDuration;
    protected boolean isAllowedToHire; // Simulation can hire new vehicles as needed
    protected Rebalance rebalanceUtil;
    protected boolean allowRequestDisplacement;

    // Matching
    protected boolean sortWaitingUsersByClass;
    protected Matching matching;

    /*Scenario*/
    protected String serviceRateScenarioLabel;
    protected String segmentationScenarioLabel;

    /* TIME HORIZON */
    protected int timeWindow; // Size of time bins
    protected int timeHorizon; // Total time horizon

    /* VEHICLE INFO */
    protected int initialFleetSize; // Fleet size
    protected int vehicleCapacity; // Number of seats
    protected int maxRoundsIdle; // Ends the contract of extra vehicle after number of rounds

    /* POOLING DATA */
    protected int maxNumberOfTrips; //How many trips are pooled in time horizon
    protected int time_slot;
    protected int start_timestamp; // (00:00:00) Initial timestamp for pooling data
    public static int leftTW, rightTW; // Left and right time windows (rightTW = current time)

    /* ROUND INFO */
    protected int totalRounds; // How many rounds of time horizon will be pooled
    protected int roundCount;
    protected boolean activeFleet; //True if a single vehicle is still working (rebalancing, picking up, etc.)

    /* SETS OF VEHICLES AND REQUESTS */
    protected Map<Integer, User> allRequests; // Dictionary of all users
    protected Set<User> unassignedRequests; // Requests whose pickup time is lower than the current time
    protected Set<User> listPooledUsersTW;  // Requests pooled within TW
    protected Set<User> deniedRequests; // Requests with expired pickup time
    protected Set<User> finishedRequests; // Requests whose DP node was visited
    protected List<Vehicle> listVehicles; // List of vehicles
    protected List<Vehicle> listHiredVehicles; //List of hired vehicles
    protected Set<Vehicle> setDeactivated; // Vehicles to be deactivated in round
    protected Set<Vehicle> setHired; // Current set of hired vehicles
    protected Set<User> roundRejectedUsers;
    protected Set<User> roundUnmetServiceLevel;
    protected List<Vehicle> roundHiredVehicles;
    protected Runtime runTimes;

    public Simulation(int initialFleetSize,
                      int vehicleCapacity,
                      int maxNumberOfTrips,
                      int timeWindow,
                      int timeHorizon,
                      int contractDuration,
                      boolean isAllowedToHire,
                      Rebalance rebalanceUtil,
                      Matching matchingSettings) {

        /*MATCHING*/
        this.sortWaitingUsersByClass = true;
        this.matching = matchingSettings;

        /* REBALANCING */
        this.rebalanceUtil = rebalanceUtil;

        /* TIME HORIZON */
        this.timeWindow = timeWindow; // Size of time bins
        this.timeHorizon = timeHorizon; // Total time horizon

        /* DEACTIVATING */
        this.contractDuration = contractDuration;
        this.isAllowedToHire = isAllowedToHire;

        /* VEHICLE INFO */
        this.initialFleetSize = initialFleetSize; // Fleet size
        this.vehicleCapacity = vehicleCapacity; // Number of seats (1 - 4)

        /* PULLING DATA */
        this.maxNumberOfTrips = maxNumberOfTrips; //How many trips are pooled in time horizon
        totalRounds = timeHorizon / timeWindow; // How many rounds of time horizon will be pooled
        time_slot = totalRounds * timeWindow;
        start_timestamp = 0; // (00:00:00) Initial timestamp for pooling data
        leftTW = start_timestamp; // Left and right time windows (rightTW = current time)
        rightTW = leftTW + timeWindow;
        roundCount = 0;

        /* SETS OF VEHICLES AND REQUESTS */
        allRequests = new HashMap<>(); // Dictionary of all users
        unassignedRequests = new HashSet<>(); // Requests whose pickup time is lower than the current time
        roundHiredVehicles = new ArrayList<>();
        roundUnmetServiceLevel = new HashSet<>();
        deniedRequests = new HashSet<>(); // Requests with expired pickup time
        finishedRequests = new HashSet<>(); // Requests whose DP node was visited
        maxRoundsIdle = 40;
        activeFleet = false;
        setHired = new HashSet<>();

        runTimes = Dao.getInstance().getRunTimes();

        listVehicles = MethodHelper.createListVehicles(initialFleetSize, vehicleCapacity, true, leftTW); // List of vehicles
        listHiredVehicles = new ArrayList<>();
        //TODO hot_PK_list
    }

    public static void reset() {
        return;
    }

    public boolean canEndContract(Vehicle v, int currentTime) {
        return v.isHired() && currentTime >= v.getContractDeadline();
    }

    public abstract Set<User> getUsersAssigned(int currentTime);

    /**
     * Pull new requests (within TW) from database. Stop pulling if number of simulation rounds has reached set limit.
     */
    public Set<User> pullCurrentRequestBatch() {

        // Clean list of pooled users
        Set<User> newUsers = new LinkedHashSet<>();

        // After the number of rounds stop pooling requests but finish waiting requests
        if (roundCount < totalRounds) {

            // Dictionary of pooled requests inside time slot
            newUsers = Dao.getInstance()
                    .getListTripsClassed(
                            timeWindow,
                            vehicleCapacity,
                            maxNumberOfTrips);
        }
        return newUsers;
    }

    /**
     * Round info
     *
     * @param showRoundInfo If true, show status of all vehicles
     */
    public void computeRoundInfo(boolean showRoundInfo, boolean saveRoundCsv, boolean showVehicleStatusInfo) {

        // Print round statistics (Round info is also calculated here)
        String roundHeader = null;
        String roundSnapshot = null;

        if (showRoundInfo) {
            roundHeader = HelperIO.getHeaderTW(start_timestamp,
                    time_slot,
                    leftTW,
                    rightTW,
                    listPooledUsersTW,
                    allRequests,
                    initialFleetSize,
                    timeWindow,
                    roundCount,
                    totalRounds
            );
        }

        if (showRoundInfo || saveRoundCsv) {
            roundSnapshot = sol.calculateRoundStats(
                    rightTW,
                    vehicleCapacity,
                    listVehicles,
                    setHired,
                    setDeactivated,
                    listHiredVehicles,
                    unassignedRequests,
                    finishedRequests,
                    roundRejectedUsers,
                    deniedRequests,
                    listPooledUsersTW,
                    allRequests,
                    runTimes,
                    saveRoundCsv,
                    showRoundInfo
            );
        }

        if (showRoundInfo) {
            // Print the time window reading
            System.out.println(roundHeader);
            System.out.println(roundSnapshot);
        }

        if (showVehicleStatusInfo) {

            // Print vehicle details
            System.out.println(HelperIO.getVehicleInfo(
                    listVehicles,
                    rightTW,
                    true,
                    true,
                    true));
        }
    }

    private Set<User> getExpiredRequestsFromUnassigned() {
        return unassignedRequests.stream()
                .filter(u -> !u.canBePickedUp(rightTW))
                .collect(Collectors.toSet());
    }

    public List<Node> getRebalancingTargets() {
        List<Node> targets = new ArrayList<>();
        if (rebalanceUtil.strategy instanceof RebalanceOptimal) {
            List<Node> nodesFromRejectedUsers = User.getUserPickupNodes(roundRejectedUsers);
            List<Node> nodesFromUnmetServiceLevelUsers = User.getUserPickupNodes(roundUnmetServiceLevel);
            List<Node> nodesFromVehicleOrigins = Vehicle.getVehicleOrigins(roundHiredVehicles);
            targets.addAll(nodesFromRejectedUsers);
            targets.addAll(nodesFromUnmetServiceLevelUsers);
            targets.addAll(nodesFromVehicleOrigins);
            System.out.println("     Rejected = " + nodesFromRejectedUsers.size() + " (" + Sets.intersection(new HashSet<>(targets), new HashSet<>(nodesFromRejectedUsers)).size() + ")");
            System.out.println("Hired origins = " + nodesFromVehicleOrigins.size() + " (" + Sets.intersection(new HashSet<>(targets), new HashSet<>(nodesFromVehicleOrigins)).size() + ")");
            System.out.println(" Pickup unmet = " + nodesFromUnmetServiceLevelUsers.size() + " (" + Sets.intersection(new HashSet<>(targets), new HashSet<>(nodesFromUnmetServiceLevelUsers)).size() + ")");

        } else if (rebalanceUtil.strategy instanceof RebalanceHeuristic) {
            targets = Vehicle.setOfHotPoints;
        }
        return targets;
    }

    public void run() {

        if (Files.exists(this.sol.getOutputFile())) {
            System.out.println(this.sol.getOutputFile() + " already exists.");
            return;
        } else {
            System.out.println(String.format("# Processing instance \"%s\"...", this.sol.getOutputFile()));
        }

        // Loop rounds of TW
        do {

            // Status will change if vehicles are still working or there are users to service
            activeFleet = false;

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // FLEET STATUS UPDATE - Where are all vehicles at the current time? ///////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////

            runTimes.startTimerFor(Runtime.TIME_UPDATE_FLEET_STATUS);

            setDeactivated = new HashSet<>();

            // Loop vehicles to get set of finished requests and set of active vehicles (servicing users OR rebalancing)
            for (Vehicle vehicle : listVehicles) {

                // Limits the hiring contract
                vehicle.increaseActiveRounds();

                // Return set of users and update rebalancing vehicles
                Set<User> serviced = vehicle.getServicedUsersUntil(rightTW);

                if (serviced != null) {
                    finishedRequests.addAll(serviced);
                }

                // If vehicle is hired, it has to be deactivated as soon as it delivers its last customer
                if (canEndContract(vehicle, rightTW)) {
                    setDeactivated.add(vehicle);
                }

                // If vehicle is not parked, it is either cruising, servicing or rebalancing
                if (!vehicle.isParked()) {
                    activeFleet = true;
                } else {

                    /* Update vehicle's current nodes (if they are of types NodeOrigin and NodeStop)
                     * with the rightmost time window value. This is the time a vehicle is allowed to
                     * depart to get the customers.
                     * E.g.:
                     * [00:00:00 - 00:00:30] -> Pool requests
                     * [00:00:30 :] -> Route vehicles
                     */
                    // Time from current node in vehicle is only updated when:
                    //  - Current node is origin or NodeStop
                    //  - Model.Vehicle is idle

                    vehicle.updateDeparture(rightTW);

                    // Vehicle is not servicing customers
                    vehicle.increaseRoundsIdle();

                }
                vehicle.updateMiddle(rightTW);
            }

            // Updating vehicle lists
            listVehicles.removeAll(setDeactivated);
            setHired.removeAll(setDeactivated);
            System.out.printf("# Removing %d hired vehicles:\n", setDeactivated.size());

            runTimes.endTimerFor(Runtime.TIME_UPDATE_FLEET_STATUS);

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // UPDATE USER DEMAND //////////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            runTimes.startTimerFor(Runtime.TIME_UPDATE_DEMAND);

            listPooledUsersTW = pullCurrentRequestBatch();
            unassignedRequests.addAll(listPooledUsersTW);

            // Store all requests
            for (User e : listPooledUsersTW) {
                allRequests.put(e.getId(), e);
            }

            // Compute requests that cannot be serviced
            roundRejectedUsers = getExpiredRequestsFromUnassigned();
            roundRejectedUsers.forEach(user -> user.computeRejection(rightTW));
            deniedRequests.addAll(roundRejectedUsers);
            unassignedRequests.removeAll(roundRejectedUsers);

            runTimes.endTimerFor(Runtime.TIME_UPDATE_DEMAND);
            // Info to rebalance vehicles (to hired origins and unmet service level origins)
            roundHiredVehicles.clear();
            roundUnmetServiceLevel.clear();

            ///// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES /////////////////////////////////////
            ResultAssignment resultAssignment = this.matching.executeStrategy(rightTW, unassignedRequests, listVehicles);

            ///// 4 - COLLECT HIRED VEHICLES ///////////////////////////////////////////////////////////////////////////
            for (Vehicle vehicle : resultAssignment.getVehiclesHired()) {
                // New vehicle is added in list
                listVehicles.add(vehicle);
                listHiredVehicles.add(vehicle);
                setHired.add(vehicle);
                roundHiredVehicles.add(vehicle);
            }

            ///// 5 - UPDATE WAITING LIST //////////////////////////////////////////////////////////////////////////////
            unassignedRequests = resultAssignment.getRequestsUnassigned();
            roundUnmetServiceLevel = resultAssignment.requestsServicedLevelNotAchieved;
            System.out.println("N. of second tier:" + roundUnmetServiceLevel.size());

            assert rejectedUnassignedFinishedSetsAreConsistent() : "Not all requests are processed.";
            assert eachUserIsAssignedToSingleVehicle() : "Users are assigned to two vehicles!";

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            //REBALANCING //////////////////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            if (rebalanceUtil.isRebalanceEnabled()) {
                // Rebalance idle vehicles

                runTimes.startTimerFor(Runtime.TIME_REBALANCING_FLEET);
                Set<Vehicle> idleVehicles = Vehicle.getIdleVehiclesFrom(listVehicles);
                System.out.println("  Rebal. Idle = " + idleVehicles.size());
                List<Node> targets = getRebalancingTargets();
                rebalanceUtil.executeStrategy(idleVehicles, targets);
                runTimes.endTimerFor(Runtime.TIME_REBALANCING_FLEET);
            }

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            //COMPUTE AND PRINT ////////////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            computeRoundInfo(Config.showRoundInfo(), Config.saveRoundInfo(), Config.showRoundFleetStatus());


            //// UPDATING TW ///////////////////////////////////////////////////////////////////////////////////////////
            leftTW = rightTW;
            rightTW = leftTW + timeWindow;
            roundCount = roundCount + 1;

        } while (!unassignedRequests.isEmpty() || roundCount < totalRounds || activeFleet);

        // Print detailed journeys for each vehicle
        if (Config.infoHandling.get(Config.SHOW_ALL_VEHICLE_JOURNEYS))
            sol.printAllJourneys(listVehicles);

        // Saving vehicles traces
        if (Config.infoHandling.get(Config.SAVE_VEHICLE_ROUND_GEOJSON))
            sol.saveGeoJsonPerVehicle(listVehicles);

        // Save solution to file (summary of rounds)
        if (Config.infoHandling.get(Config.SAVE_ROUND_INFO_CSV))
            sol.saveRoundInfo();

        // Saving user info (how user was serviced, for example, pickup, dropoff, hired vehicle, delay, etc.)
        if (Config.infoHandling.get(Config.SAVE_REQUEST_INFO_CSV))
            sol.saveUserInfo(allRequests);
    }


    //****************************************************************************************************************//
    //***** ASSERTIONS ***********************************************************************************************//
    //****************************************************************************************************************//
    public boolean allVehicleVisitsAreValid() {
        for (Vehicle vehicle : listVehicles) {
            if (vehicle.getVisit() != null && !vehicle.getVisit().isValid()) {
                System.out.println(String.format("Sequence of vehicle %s is invalid! Visit: %s", vehicle, vehicle.getVisit()));
                return false;
            }
        }
        return true;
    }

    public boolean thereAreNoRepeatedRequests(List<User> allRequests) {
        return (new HashSet<>(allRequests)).size() == allRequests.size();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ASSERTIONS //////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public boolean eachUserIsAssignedToSingleVehicle() {

        for (int i = 0; i < listVehicles.size() - 1; i++) {
            Vehicle v1 = listVehicles.get(i);
            if (!v1.isServicing())
                continue;

            for (int j = i + 1; j < listVehicles.size(); j++) {

                Vehicle v2 = listVehicles.get(j);

                if (!v2.isServicing())
                    continue;

                Set<User> intersection = new HashSet<>(v1.getVisit().getRequests());
                intersection.retainAll(v2.getVisit().getRequests());

                if (intersection.size() > 0) {

                    System.out.println(intersection);
                    System.out.println(v1.getVisit());
                    System.out.println(v2.getVisit());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean rejectedUnassignedFinishedSetsAreConsistent() {
        List<User> inVehicleRequests = Vehicle.getUsersFrom(listVehicles);
        if (deniedRequests.size() + unassignedRequests.size() + finishedRequests.size() + inVehicleRequests.size() == allRequests.size()) {
            return true;
        } else {
            printCurrentStatus("Inconsistency found");
            return false;
        }
    }

    private void printCurrentStatus(String label) {
        System.out.println("\n######################### " + label + " ###########################");
        List<User> inVehicleRequests = Vehicle.getUsersFrom(listVehicles);
        System.out.println(getCollectionInfo("Denied", deniedRequests));
        System.out.println(getCollectionInfo("Unassigned", unassignedRequests));
        System.out.println(getCollectionInfo("Finished", finishedRequests));
        System.out.println(getCollectionInfo("In-Vehicle", inVehicleRequests));
        System.out.println(getCollectionInfo("All requests", allRequests.values()));
    }

    public String getCollectionInfo(String label, Collection collection) {
        List col = new ArrayList(collection);
        Collections.sort(col);
        return String.format("# %15s (%d): %s", label, collection.size(), collection);
    }
}