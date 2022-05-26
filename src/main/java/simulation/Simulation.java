package simulation;

import com.google.common.collect.Sets;
import config.Config;
import config.InstanceConfig;
import dao.Dao;
import helper.HelperIO;
import helper.MethodHelper;
import helper.Runtime;
import model.User;
import model.Vehicle;
import model.node.Node;
import simulation.matching.Matching;
import simulation.matching.ResultAssignment;
import simulation.rebalancing.Rebalance;
import simulation.rebalancing.RebalanceHeuristic;
import simulation.rebalancing.RebalanceOptimal;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Simulation {


    protected Random randomSeed;
    protected Duration runTime;

    public Solution getSol() {
        return sol;
    }

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
    public static int timeWindow; // Size of time bins
    public static int timeHorizon; // Total time horizon
    public static int requestSamplingHorizon; // Until when requests are sampled (must be lower then time horizon)
    protected Date earliestTime;

    /* VEHICLE INFO */
    protected int initialFleetSize; // Fleet size
    protected int vehicleCapacity; // Number of seats
    protected int maxRoundsIdle; // Ends the contract of extra vehicle after number of rounds

    /* POOLING DATA */
    protected int maxNumberOfTrips; //How many trips are pooled in time window
    protected double percentageTrips; // Percentage of total number of trips sampled from time window batch
    protected int time_slot;
    protected int start_timestamp; // (00:00:00) Initial timestamp for pooling data
    public static int leftTW, rightTW; // Left and right time windows (rightTW = current time)

    /* ROUND INFO */
    protected int totalTimeSteps; // How many rounds of time horizon will be pooled
    protected int timeStep;
    protected boolean activeFleet; //True if a single vehicle is still working (rebalancing, picking up, etc.)

    /* SETS OF VEHICLES AND REQUESTS */
    protected Map<Integer, User> allRequests; // Dictionary of all users
    protected Set<User> unassignedRequests; // Requests whose pickup time is lower than the current time
    protected Set<User> listPooledUsersTW;  // Requests pooled within TW
    protected Set<User> deniedRequests; // Requests with expired pickup time
    protected Set<User> finishedRequests; // Requests whose DP node was visited
    protected Set<Vehicle> listVehicles; // List of vehicles
    protected Set<Vehicle> listHiredVehicles; //List of hired vehicles
    protected Set<Vehicle> setDeactivated; // Vehicles to be deactivated in round
    protected Set<Vehicle> setHired; // Current set of hired vehicles
    protected Set<User> roundRejectedUsers;
    protected Set<User> roundUnmetServiceLevel;
    protected Set<Vehicle> roundHiredVehicles;
    protected Runtime runTimes;

    public Simulation(int initialFleetSize,
                      int vehicleCapacity,
                      int maxNumberOfTrips,
                      double percentageTrips,
                      Date earliestTime,
                      int timeWindow,
                      InstanceConfig.TimeConfig timeHorizon,
                      int contractDuration,
                      boolean isAllowedToHire,
                      Rebalance rebalanceUtil,
                      Matching matchingSettings,
                      Random randomSeed) {

        /* USER SELECTION AND VEHICLE ORIGINS*/
        this.randomSeed = randomSeed;

        /*MATCHING*/
        this.sortWaitingUsersByClass = true;
        this.matching = matchingSettings;

        /* REBALANCING */
        this.rebalanceUtil = rebalanceUtil;

        /* TIME HORIZON */
        Simulation.timeWindow = timeWindow; // Size of time bins
        Simulation.timeHorizon = timeHorizon.total_simulation_horizon; // Total time horizon
        Simulation.requestSamplingHorizon = timeHorizon.request_sampling_horizon;
        this.earliestTime = earliestTime;

        /* DEACTIVATING */
        this.contractDuration = contractDuration;
        this.isAllowedToHire = isAllowedToHire;

        /* VEHICLE INFO */
        this.initialFleetSize = initialFleetSize; // Fleet size
        this.vehicleCapacity = vehicleCapacity; // Number of seats (1 - 4)

        /* PULLING DATA */
        this.maxNumberOfTrips = maxNumberOfTrips; //How many trips are pooled in time horizon
        this.percentageTrips = percentageTrips;
        totalTimeSteps = timeHorizon.total_simulation_horizon / timeWindow; // How many rounds of time horizon will be pooled
        time_slot = timeHorizon.total_simulation_horizon;
        start_timestamp = 0; // (00:00:00) Initial timestamp for pooling data
        leftTW = start_timestamp; // Left and right time windows (rightTW = current time)
        rightTW = leftTW + timeWindow;
        timeStep = 1;

        /* SETS OF VEHICLES AND REQUESTS */
        allRequests = new HashMap<>(); // Dictionary of all users
        unassignedRequests = new HashSet<>(); // Requests whose pickup time is lower than the current time
        roundHiredVehicles = new HashSet<>();
        roundUnmetServiceLevel = new HashSet<>();
        deniedRequests = new HashSet<>(); // Requests with expired pickup time
        finishedRequests = new HashSet<>(); // Requests whose DP node was visited
        maxRoundsIdle = 40;
        activeFleet = false;
        setHired = new HashSet<>();

        runTimes = Dao.getInstance().getRunTimes();

        listVehicles = MethodHelper.createListVehicles(initialFleetSize, vehicleCapacity, true, leftTW, this.randomSeed, Dao.getInstance().getDistMatrix().length); // List of vehicles
//        String a = listVehicles.stream().map(v -> String.valueOf(v.getOrigin().getNetworkId())).reduce("", (subtotal, element) -> subtotal + "," + element);
//        HelperIO.saveDataWithHeaders(String.format("vehicle_distribution_%s.txt", this.randomSeed), a, this.matching.toString(), true);
        listHiredVehicles = new HashSet<>();
        //TODO hot_PK_list
    }

    public String getSummary(int episode) {

        return String.format("%20s;%4d;%s,%4d;%10d;%10d;%5.4f;%4d;%4d;%10d;%,10.2f;%,10.2f,%4d",
                this.matching.getRideMatchingStrategy(),
                episode,
                Config.formatter_date_time.format(this.earliestTime),
                this.listVehicles.size(),
                this.allRequests.size(),
                this.finishedRequests.size(),
                (double) this.finishedRequests.size() / this.allRequests.size(),
                this.totalTimeSteps,
                this.timeStep,
                this.finishedRequests.stream().mapToInt(sub -> sub.getNodeDp().getDelay()).sum(),
                this.listVehicles.stream().mapToDouble(sub -> sub.getDistanceTraveledEmpty()).sum(),
                this.listVehicles.stream().mapToDouble(sub -> sub.getDistanceTraveledLoaded()).sum(),
                this.runTime.toSeconds());
    }

    public static void reset() {
        return;
    }

    /**
     * Pull new requests (within TW) from database. Stop pulling if number of simulation rounds has reached set limit.
     */
    public Set<User> pullCurrentRequestBatch() {

        // Clean list of pooled users
        Set<User> newUsers = new LinkedHashSet<>();

        // After the number of rounds stop pooling requests but finish waiting requests
        if (rightTW <= requestSamplingHorizon) {

            // Dictionary of pooled requests inside time slot
            newUsers = Dao.getInstance()
                    .getListTripsClassedShuffled(
                            this.earliestTime,
                            timeWindow,
                            vehicleCapacity,
                            percentageTrips,
                            this.randomSeed);
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
            roundHeader = HelperIO.getHeaderTW(
                    earliestTime,
                    start_timestamp,
                    time_slot,
                    leftTW,
                    rightTW,
                    listPooledUsersTW,
                    allRequests,
                    initialFleetSize,
                    timeWindow,
                    timeStep,
                    totalTimeSteps
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

        Instant before = Instant.now();

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

                // If vehicle is hired, it has to be deactivated as soon as it delivers its last customer
                if (vehicle.canEndContract(rightTW)) {
                    setDeactivated.add(vehicle);
                }

                if (vehicle.isRebalancing()) {
                    // If rebalancing is finished (vehicle arrived at target)
                    if (vehicle.hasFinishedRebalancing(rightTW)) {
                        vehicle.endRebalancing(rightTW);
                        vehicle.createNodeStopAndFinishVisitAt(rightTW);
                    } else {
                        activeFleet = true;
                    }
                } else if (vehicle.isServicing()) {

                    // Update finished requests
                    finishedRequests.addAll(vehicle.getServicedUsersUntil(rightTW));

                    if (vehicle.hasServicedAllRequests()) {
                        vehicle.createNodeStopAndFinishVisitAt(rightTW);
                    } else {
                        activeFleet = true;
                    }
                } else {

                    /* Update vehicle's current nodes (if they are of types NodeOrigin and NodeStop)
                     * with the rightmost time window value (current time step).
                     * This is the time a vehicle is allowed to depart to get the customers.
                     * E.g.:
                     * [00:00:00 - 00:00:30] -> Pool requests
                     * [00:00:30 :] -> Route vehicles
                     */
                    // Time from current node in vehicle is only updated when:
                    //  - Current node is origin or NodeStop
                    //  - Model.Vehicle is idle

                    vehicle.updateEarliestDeparture(rightTW);


                    // Vehicle is not servicing customers
                    vehicle.increaseRoundsIdle();

                }
                // Where is the vehicle in the network map at the current time step?
                // 1. Vehicle is parked => Middle is NULL
                // 2. Vehicle is moving => Middle is between [last visited node, target node]
                // 2.1. Elapsed time since departure at last visited node is zero = Middle is NULL
                // 2.2. Middle is target node (there are no middle points) = Middle is target node
                vehicle.updateMiddle(rightTW);
                assertConsistentArrivalDepartureTimesForVehicle(vehicle);

                //vehicle.printVisitTrack();
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

            // System.out.println(resultAssignment.getSnapshot(listVehicles));
            ///// 5 - UPDATE WAITING LIST //////////////////////////////////////////////////////////////////////////////
            unassignedRequests = resultAssignment.getRequestsUnassigned();
            roundUnmetServiceLevel = resultAssignment.requestsServicedLevelNotAchieved;

            assert eachUserIsAssignedToSingleVehicle() : "Users are assigned to two vehicles!";
            assert rejectedUnassignedFinishedSetsAreConsistent() : "Not all requests are processed.";

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
            timeStep = timeStep + 1;

        } while (timeStep <= totalTimeSteps || activeFleet);


        Instant after = Instant.now();
        this.runTime = Duration.between(before, after);
        System.out.println("Duration:" + runTime.toMinutes());

        System.out.printf("FINISHED SIMULATION - Duration=%02d:%02d", runTime.toMinutesPart(), runTime.toSecondsPart());
        // Print detailed journeys for each vehicle
        if (Config.infoHandling.get(Config.SHOW_ALL_VEHICLE_JOURNEYS))
            sol.printAllJourneys(listVehicles);

        // Saving vehicles traces
        if (Config.infoHandling.get(Config.SAVE_VEHICLE_ROUND_GEOJSON))
            sol.saveGeoJsonPerVehicle(earliestTime, listVehicles);

        // Save solution to file (summary of rounds)
        if (Config.infoHandling.get(Config.SAVE_ROUND_INFO_CSV))
            sol.saveRoundInfo();

        // Saving user info (how user was serviced, for example, pickup, dropoff, hired vehicle, delay, etc.)
        if (Config.infoHandling.get(Config.SAVE_REQUEST_INFO_CSV))
            sol.saveUserInfo(allRequests);
    }

    private void assertConsistentArrivalDepartureTimesForVehicle(Vehicle vehicle) {
        assert vehicle.getLastVisitedNode().getArrival() >= vehicle.getLastVisitedNode().getEarliest() : String.format("Node=%s (%s) - %s - %s", vehicle.getLastVisitedNode().getInfo(), vehicle.getVisit(), vehicle.getJourney(), String.valueOf(vehicle.getVisitTrack()));
        if (!vehicle.isParked()) {
            assert vehicle.getLastVisitedNode().getDeparture() >= vehicle.getLastVisitedNode().getArrival() : String.format("Dep=%s >= Arr=%s (%s) - %s", vehicle.getLastVisitedNode().getDeparture(), vehicle.getLastVisitedNode().getArrival(), vehicle.getVisit(), vehicle.getJourney());
            assert vehicle.getLastVisitedNode().getDeparture() >= vehicle.getLastVisitedNode().getEarliestDeparture() : String.format("Dep=%s >= EarDep=%s (%s) - %s", vehicle.getLastVisitedNode().getDeparture(), vehicle.getLastVisitedNode().getEarliestDeparture(), vehicle.getVisit(), vehicle.getJourney());
        }
    }

    public boolean instanceAlreadyProcessed() {
        return Files.exists(this.sol.getOutputFile());
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

        List<Vehicle> vehicles = new ArrayList<>(listVehicles);
        for (int i = 0; i < vehicles.size() - 1; i++) {
            Vehicle v1 = vehicles.get(i);
            if (!v1.isServicing())
                continue;

            for (int j = i + 1; j < vehicles.size(); j++) {

                Vehicle v2 = vehicles.get(j);

                if (!v2.isServicing())
                    continue;

                Set<User> intersection = new HashSet<>(v1.getVisit().getRequests());
                intersection.retainAll(v2.getVisit().getRequests());

                if (intersection.size() > 0) {

                    System.out.printf("Users=%s are assigned to vehicles %s (%s) and %s (%s).\n", intersection, v1, v1.getId(), v2, v2.getId());

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
        assert inVehicleRequests.size() == new HashSet<>(inVehicleRequests).size();
        System.out.println(getCollectionInfo("Denied", deniedRequests));
        System.out.println(getCollectionInfo("Unassigned", unassignedRequests));
        System.out.println(getCollectionInfo("Finished", finishedRequests));
        System.out.println(getCollectionInfo("In-Vehicle", inVehicleRequests));
        System.out.println(getCollectionInfo("All requests", allRequests.values()));
        List<User> diff = new ArrayList(allRequests.values());
        diff.removeAll(inVehicleRequests);
        System.out.println(getCollectionInfo("Diff:", diff));
    }

    public String getCollectionInfo(String label, Collection collection) {
        List col = new ArrayList(collection);
        Collections.sort(col);
        return String.format("# %15s (%d): %s", label, collection.size(), collection);
    }
}