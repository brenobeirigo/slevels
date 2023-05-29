package simulation;

import com.google.common.collect.Sets;
import config.Config;
import dao.DateUtil;
import dao.Logging;
import helper.HelperIO;
import helper.Runtime;
import model.Vehicle;
import model.demand.User;
import model.node.Node;
import model.visit.Visit;
import model.visit.VisitRelocation;
import result.LoggingUtil;
import simulation.matching.Matching;
import simulation.matching.ResultAssignment;
import simulation.rebalancing.Rebalance;
import simulation.rebalancing.RebalanceOptimal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class Simulation {
    protected RequestManager requestManager;
    private final Environment env;
    private LocalDateTime previousDateTime;
    private LocalDateTime currentDateTime;
    private LocalDateTime startDateTime;
    /* TIME HORIZON */
    public int timeWindowSec; // Size of time bins
    public int simulationHorizonSec; // Total time horizon
    public int requestSamplingHorizon; // Until when requests are sampled (must be lower then time horizon)
    public int previousTimeStep, currentTime; // Left and right time windows (rightTW = current time)
    public Matching matching;
    protected int randomSeed;
    protected Duration runTime;
    // Solution coming from simulation
    protected Solution sol; //Environment solution
    /* Hiring, unbounding, and simulation.rebalancing */
    protected int contractDuration;
    protected boolean isAllowedToHire; // Environment can hire new vehicles as needed
    protected Rebalance rebalanceUtil;
    protected boolean allowRequestDisplacement;
    // Matching
    protected boolean sortWaitingUsersByClass;
    /*Scenario*/
    protected String serviceRateScenarioLabel;
    protected String segmentationScenarioLabel;
    protected @com.fasterxml.jackson.annotation.JsonProperty("start_datetime") LocalDateTime earliestTime;
    /* VEHICLE INFO */
    protected int initialFleetSize; // FleetConfig size
    protected int vehicleCapacity; // Number of seats
    /* POOLING DATA */
    protected int maxNumberOfTrips; //How many trips are pooled in time window
    protected double percentageTrips; // Percentage of total number of trips sampled from time window batch
    protected int startTimeStep; // (00:00:00) Initial timestamp for pooling data
    /* ROUND INFO */
    protected int totalTimeSteps; // How many rounds of time horizon will be pooled
    protected int currentTimeStep;
    protected boolean activeFleet; //True if a single vehicle is still working (rebalancing, picking up, etc.)
    protected Set<Vehicle> listVehicles; // List of vehicles
    protected Set<Vehicle> listHiredVehicles; //List of hired vehicles
    protected Set<Vehicle> setDeactivated; // Vehicles to be deactivated in round
    protected Set<Vehicle> setHired; // Current set of hired vehicles
    protected Set<User> roundRejectedUsers;
    protected Set<User> roundUnmetServiceLevel;
    protected Set<Vehicle> roundHiredVehicles;
    protected Runtime runTimes;



    public Simulation(Environment environment, int randomSeed) {
        this.env = environment;
        this.randomSeed = randomSeed;
        this.requestManager = new RequestManager(environment);


        /* TIME HORIZON */
        this.timeWindowSec = this.env.timeConfig.timeWindowSec();
        this.simulationHorizonSec = this.env.timeConfig.totalSimulationHorizonSec();
        this.startDateTime = this.env.timeConfig.startDateTime();
        this.currentDateTime = this.startDateTime;
        this.previousDateTime = this.currentDateTime;

        // Steps
        this.currentTimeStep = 0;
        this.previousTimeStep = this.currentTimeStep;
        this.totalTimeSteps = this.simulationHorizonSec / timeWindowSec;
//
//        this.earliestTime = this.env.timeConfig.startDateTime();
//
//        activeFleet = false;
//        roundUnmetServiceLevel = new HashSet<>();
//        roundHiredVehicles = new HashSet<>();
//


        /* USER SELECTION AND VEHICLE ORIGINS*/

//        /*MATCHING*/
//        this.sortWaitingUsersByClass = true;
//        this.matching = this.env.matching;
//
//        /* REBALANCING */
////        this.rebalanceUtil = this.env.matching.getRebalanceConfig();
//
//
//
//        /* DEACTIVATING */
////        this.contractDuration = 1000;
////        this.isAllowedToHire = true;
//
//        /* VEHICLE INFO */
//        this.initialFleetSize = this.env.fleetConfig.vehicles().get(0).nVehicles(); // FleetConfig size
//        this.vehicleCapacity = this.env.fleetConfig.vehicles().get(0).capacity(); // Number of seats (1 - 4)
//
//        /* PULLING DATA */
//        this.maxNumberOfTrips = this.env.demandConfig.maxNumberOfTrips(); //How many trips are pooled in time horizon
//        this.percentageTrips = this.env.demandConfig.percentageRequests();
//
//        /* SETS OF VEHICLES AND REQUESTS */
//        allRequests = new HashMap<>(); // Dictionary of all users
//        unassignedRequests = new HashSet<>(); // Requests whose pickup time is lower than the current time
//        roundHiredVehicles = new HashSet<>();
//        roundUnmetServiceLevel = new HashSet<>();
//        deniedRequests = new HashSet<>(); // Requests with expired pickup time
//        finishedRequests = new HashSet<>(); // Requests whose DP node was visited
//        activeFleet = false;
//        setHired = new HashSet<>();
//
//        runTimes = Dao.getInstance().getRunTimes();
//
//        listVehicles = this.env.createListVehicles(
//                previousTime,
//                this.randomSeed);
//        listHiredVehicles = new HashSet<>();
    }


    /**
     * End rebalancing if target node was reached.
     * t is important to add rebalancing target to journey because:
     * - legs (NodeStop, NodeTargetRebalancing) indicate rebalancing
     * - legs (NodeTargetRebalancing, NodeStop) indicate waiting
     *
     * @param currentSimulationTime current execution time of the simulation
     * @param vehicle
     */
    public void endRebalancing(int currentSimulationTime, Vehicle vehicle) {

        vehicle.setDistanceTraveledRebalancing(vehicle.getDistanceTraveledRebalancing() + this.env.getNetwork().getDistance(
                vehicle.getLastVisitedNode().getNetworkId(),
                vehicle.getVisit().getTargetNode().getNetworkId()));

        vehicle.setRoundsIdle(vehicle.roundsIdle + 1);

        vehicle.lastVisitedNode = vehicle.getVisit().getTargetNode();
        vehicle.lastVisitedNode.setArrival(this.env.getArrivalTimeAtNext(vehicle.getVisit()));
        // Min. departure is arrival time
        vehicle.lastVisitedNode.setEarliestDeparture(this.env.getArrivalTimeAtNext(vehicle.getVisit()));

        // Vehicle has been recently rebalanced, worth keeping in the fleet
        vehicle.setRoundsIdle(0);

        // Node can become a rebalancing target again
        Node.tabu.remove(vehicle.lastVisitedNode.getNetworkId());

        vehicle.getJourney().add(vehicle.lastVisitedNode);
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
                if (vehicle.canEndContract(currentTime)) {
                    setDeactivated.add(vehicle);
                }

                if (vehicle.isRebalancing()) {
                    // If rebalancing is finished (vehicle arrived at target)
                    if (this.hasFinishedRebalancing(currentTime, vehicle)) {
                        this.endRebalancing(currentTime, vehicle);
                        vehicle.createNodeStopAndFinishVisitAt(currentTime);
                    } else {
                        vehicle.getServicedUsersUntil(currentTime, this.env.network);
                        //activeFleet = true;
                    }
                } else if (vehicle.isServicing()) {

                    // Update finished requests
                    requestManager.finishedRequests.addAll(vehicle.getServicedUsersUntil(currentTime, this.env.network));

                    if (vehicle.hasServicedAllRequests()) {
                        vehicle.createNodeStopAndFinishVisitAt(currentTime);
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

                    vehicle.updateEarliestDeparture(currentTime);


                    // Vehicle is not servicing customers
                    vehicle.increaseRoundsIdle();

                }
                // Where is the vehicle in the network map at the current time step?
                // 1. Vehicle is parked => Middle is NULL
                // 2. Vehicle is moving => Middle is between [last visited node, target node]
                // 2.1. Elapsed time since departure at last visited node is zero = Middle is NULL
                // 2.2. Middle is target node (there are no middle points) = Middle is target node
                this.env.updateMiddle(currentTime, vehicle);
                assert this.currentTime <= vehicle.getMiddleNode().getEarliest() : String.format("\nNode=%s\nVisit=%s, \nMiddle=%s \nDist: %s", env.network.getInfo(vehicle.getLastVisitedNode()), vehicle.getVisit(), env.network.getInfo(vehicle.getMiddleNode()), vehicle.distMiddleNode);

                assertConsistentArrivalDepartureTimesForVehicle(vehicle);
            }


            // Updating vehicle lists
            listVehicles.removeAll(setDeactivated);
            setHired.removeAll(setDeactivated);
            Logging.logger.info("# Removing {} hired vehicles.", setDeactivated.size());

            runTimes.endTimerFor(Runtime.TIME_UPDATE_FLEET_STATUS);

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // UPDATE USER DEMAND //////////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            runTimes.startTimerFor(Runtime.TIME_UPDATE_DEMAND);

            requestManager.listPooledUsersTW = this.getRequestsBetween(currentDateTime, currentDateTime.plusSeconds(env.timeConfig.timeWindowSec()));
            requestManager.unassignedRequests.addAll(requestManager.listPooledUsersTW);

            // Store all requests
            for (User e : requestManager.listPooledUsersTW) {
                requestManager.allRequests.put(e.getId(), e);
            }

            // Compute requests that cannot be serviced
            roundRejectedUsers = getExpiredRequestsFromUnassigned();
            roundRejectedUsers.forEach(user -> user.computeRejection(currentTime));
            requestManager.deniedRequests.addAll(roundRejectedUsers);
            requestManager.unassignedRequests.removeAll(roundRejectedUsers);

            runTimes.endTimerFor(Runtime.TIME_UPDATE_DEMAND);
            // Info to rebalance vehicles (to hired origins and unmet service level origins)
            roundHiredVehicles.clear();
            roundUnmetServiceLevel.clear();

            ///// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES /////////////////////////////////////
            runTimes.startTimerFor(Runtime.TIME_MATCHING);
            ResultAssignment resultAssignment = this.matching.executeStrategy(currentTime, requestManager.unassignedRequests, listVehicles);
            runTimes.endTimerFor(Runtime.TIME_MATCHING);
            ///// 4 - COLLECT HIRED VEHICLES ///////////////////////////////////////////////////////////////////////////
            for (Vehicle vehicle : resultAssignment.getVehiclesHired()) {
                // New vehicle is added in list
                listVehicles.add(vehicle);
                listHiredVehicles.add(vehicle);
                setHired.add(vehicle);
                roundHiredVehicles.add(vehicle);
            }

            // Logging.logger.info(resultAssignment.getSnapshot(listVehicles));
            ///// 5 - UPDATE WAITING LIST //////////////////////////////////////////////////////////////////////////////
            requestManager.unassignedRequests = resultAssignment.getRequestsUnassigned();
            roundUnmetServiceLevel = resultAssignment.requestsServicedLevelNotAchieved;

            assert eachUserIsAssignedToSingleVehicle() : "Users are assigned to two vehicles!";
            assert rejectedUnassignedFinishedSetsAreConsistent() : "Not all requests are processed.";

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            //REBALANCING //////////////////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            if (rebalanceUtil.isRebalanceEnabled()) {
                // Rebalance idle vehicles

                runTimes.startTimerFor(Runtime.TIME_REBALANCING_FLEET);
//                if (!(this.matching.getRideMatchingStrategy() instanceof MatchingSimple)) {


                Set<Vehicle> idleVehicles = Vehicle.getIdleVehiclesFrom(listVehicles);
                Logging.logger.info("  Rebal. Idle = " + idleVehicles.size());
                List<Node> targets = getRebalancingTargets();
                rebalanceUtil.executeStrategy(idleVehicles, targets);
//                } else {
//                    Logging.logger.info("Skipping rebalancing...");
//                }
                runTimes.endTimerFor(Runtime.TIME_REBALANCING_FLEET);
            }

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            //COMPUTE AND PRINT ////////////////////////////////////////////////////////////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////////////////////////////////////
            computeRoundInfo(Config.showRoundInfo(), Config.saveRoundInfo(), Config.showRoundFleetStatus());


            //// UPDATING TW ///////////////////////////////////////////////////////////////////////////////////////////
            previousTimeStep = currentTime;
            currentTime = previousTimeStep + timeWindowSec;
            currentTimeStep = currentTimeStep + 1;

        } while (currentTimeStep <= totalTimeSteps || activeFleet);

//        // Initialize solution
//        sol = new Solution(instance);
//    }
//
//        Instant after = Instant.now();
//        this.runTime = Duration.between(before, after);
//        Logging.logger.info("Duration: {}", runTime.toMinutes());
//
//        Logging.logger.info("{}", String.format("FINISHED SIMULATION - Duration=%02d:%02d\n", runTime.toMinutesPart(), runTime.toSecondsPart()));
//        // Print detailed journeys for each vehicle
//        if (Config.info_handling().get(Logging.SHOW_ALL_VEHICLE_JOURNEYS)) {
//            Logging.logger.info("Showing vehicle journeys...");
//            GeoJsonUtil journeys = new GeoJsonUtil(this.env);
//            journeys.printAllJourneys();
//        }
//
//        // Saving vehicles traces
//        if (Config.info_handling().get(Logging.SAVE_VEHICLE_ROUND_GEOJSON)) {
//            Logging.logger.info("Saving vehicle round geojson...");
//            GeoJsonUtil journeys = new GeoJsonUtil(this.env);
//            journeys.saveGeoJsonPerVehicle(earliestTime, listVehicles, sol.getTestCaseName());
//        }
//
//        // Save solution to file (summary of rounds)
//        if (Config.info_handling().get(Logging.SAVE_ROUND_INFO_CSV)) {
//            Logging.logger.info("Saving round info csv...");
//            sol.saveRoundInfo();
//        }
//
//        // Saving user info (how user was serviced, for example, pickup, dropoff, hired vehicle, delay, etc.)
//        if (Config.info_handling().get(Logging.SAVE_REQUEST_INFO_CSV)) {
//            Logging.logger.info("Saving request info csv...");
//            Solution.saveUserInfo(sol, allRequests, this.env);
//        }
    }
//
//    public Set<User> getListTripsClassedShuffled( earliestTime, int timeSpanSec, int maxPassengerCount, int maxNumber, Random rand) {
//
//        List<User> trips = getUsers(earliestTimeRequestBatch, earliestTime, timeSpanSec, maxPassengerCount);
//
//
//
//        return getTripSubset(maxNumber, trips);


        public Set<User> getRequestsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
//            List<Request> trips = env.getRequestsBetween(startDateTime, endDateTime);
//            Collections.shuffle(trips, new Random(this.randomSeed));
//        env.pullCurrentRequestBatch()
            return null;
    }


    public String getSummaryEpisodeInfo(int episode) {

        return String.format("%20s;%4d;%s;%4d;%10d;%10d;%5.4f;%4d;%4d;%10d;%,10.2f;%,10.2f;%4d",
                this.matching.getRideMatchingStrategy(),
                episode,
                DateUtil.formatter_date_time.format(this.earliestTime),
                this.listVehicles.size(),
                this.requestManager.allRequests.size(),
                this.requestManager.finishedRequests.size(),
                (double) this.requestManager.finishedRequests.size() / this.requestManager.allRequests.size(),
                this.totalTimeSteps,
                this.currentTimeStep,
                this.requestManager.finishedRequests.stream().mapToInt(sub -> sub.getNodeDp().getDelay()).sum(),
                this.listVehicles.stream().mapToDouble(sub -> sub.getDistanceTraveledEmpty()).sum(),
                this.listVehicles.stream().mapToDouble(sub -> sub.getDistanceTraveledLoaded()).sum(),
                this.runTime.toSeconds());
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
                    startTimeStep,
                    simulationHorizonSec,
                    previousTimeStep,
                    currentTime,
                    requestManager.listPooledUsersTW,
                    requestManager.allRequests,
                    initialFleetSize,
                    timeWindowSec,
                    currentTimeStep,
                    totalTimeSteps
            );
        }

        if (showRoundInfo || saveRoundCsv) {
            roundSnapshot = sol.calculateRoundStats(
                    currentTime,
                    vehicleCapacity,
                    listVehicles,
                    setHired,
                    setDeactivated,
                    listHiredVehicles,
                    requestManager.unassignedRequests,
                    requestManager.finishedRequests,
                    roundRejectedUsers,
                    requestManager.deniedRequests,
                    requestManager.listPooledUsersTW,
                    requestManager.allRequests,
                    runTimes,
                    saveRoundCsv,
                    showRoundInfo
            );
        }

        if (showRoundInfo) {
            // Print the time window reading
            Logging.logger.info(roundHeader);
            Logging.logger.info(roundSnapshot);
        }

        if (showVehicleStatusInfo) {

            // Print vehicle details
            Logging.logger.info(HelperIO.getVehicleInfo(
                    listVehicles,
                    currentTime,
                    true,
                    true,
                    true));
        }
    }

    private Set<User> getExpiredRequestsFromUnassigned() {
        return requestManager.unassignedRequests.stream()
                .filter(u -> !u.canBePickedUp(currentTime))
                .collect(Collectors.toSet());
    }


    public String getVisitDetail(Visit visit) {
        if (visit.getVehicle() == null) {
            return "DUMMY VISIT";
        }

        String vehicleData = visit.getVehicleInfo();

        // When visit was not yet setup to vehicle, shows that it refers to a draft visit
        String draftVisit = visit.isSetup() ? "[SETUP]" : "[DRAFT]";

        // If rebalancing, create sequence with rebalancing node
        List<Node> sequenceNodesToVisit;
        //if (visit.getVehicle().isRebalancing() && visit.getVehicle().getVisit().getSequenceVisits() == null) {
        if (visit instanceof VisitRelocation) {
            sequenceNodesToVisit = new LinkedList<>();

            // Add rebalancing target to list
            sequenceNodesToVisit.add(visit.getTargetNode());
        } else {
            sequenceNodesToVisit = visit.getSequenceVisits();
        }

        //List<Node> sequenceNodesToVisit = visit.getSequenceVisits();

        Node lastVisitedNode = visit.getVehicle().getLastVisitedNode();
        int loadUntilLastVisited = visit.getVehicle().getCurrentLoad();
        Integer departureLastVisited = visit.getVehicle().getLastVisitedNode().getDeparture() != null ? visit.getVehicle().getLastVisitedNode().getDeparture() : visit.getVehicle().getLastVisitedNode().getEarliestDeparture();

        //Current node info
        String lastVisitedNodeInfo = getVehicleDepartureNodeInfo(visit, sequenceNodesToVisit);

        // Node strings
        List<String> pkDpNodeInfo = new ArrayList<>();
        for (int i = 0; i < sequenceNodesToVisit.size(); i++) {

            Node next = sequenceNodesToVisit.get(i);
            int dist = this.env.getDistanceSeconds(lastVisitedNode, next);

            // Update data from last visited node
            lastVisitedNode = next;
            departureLastVisited = Math.max(departureLastVisited + dist, lastVisitedNode.getEarliest());
            loadUntilLastVisited += lastVisitedNode.getLoad();

            String nodeInfo = LoggingUtil.legInfo(visit, lastVisitedNode, loadUntilLastVisited, departureLastVisited, dist);
            pkDpNodeInfo.add(nodeInfo); // config.Config.sec2TStamp(sequenceArrivals.get(i))));

        }

        if (visit.getVehicle().isHired())
            pkDpNodeInfo.add(String.format("contract=<%s/%s>", departureLastVisited, visit.getVehicle().getContractDeadline()));
        String delayInfo = String.format("(delay: %5d - occ.: %5.2f)", visit.getDelay(), visit.getAvgLoadPerVisitLeg());
        return String.format(
                "[timestep=%5d] %s %s %s %s %s --- %s",
                this.previousTimeStep,
                draftVisit,
                delayInfo,
                vehicleData,
                lastVisitedNodeInfo,
                String.join(" ", pkDpNodeInfo),
                visit.getUserInfo());

    }

    private String getVehicleDepartureNodeInfo(Visit visit, List<Node> sequenceNodesToVisit) {

        return String.format(
                "%s (departure=%7d, visit dep.=%7d, target=%10s) :::%s%s%s%s:::",
                (visit.getVehicle().isRebalancing() ? "--RE--" : "--ST--"),
                visit.getVehicle().getLastVisitedNode().getDeparture(),
                visit.getDeparture(),
                visit.getTargetNode(),
                LoggingUtil.legInfo(
                        visit, visit.getVehicle().getLastVisitedNode(),
                        visit.getVehicle().getCurrentLoad(),
                        visit.getVehicle().getLastVisitedNode().getDeparture(),
                        Integer.MIN_VALUE),
                getDistanceLegInfoFromNodes(
                        visit.getVehicle().getLastVisitedNode(),
                        visit.getVehicle().getMiddleNode()),
                visit.getVehicle().getMiddleNode() == null ? "-------" : visit.getVehicle().getMiddleNode(),
                getDistanceLegInfoFromNodes(
                        visit.getVehicle().getMiddleNode(),
                        sequenceNodesToVisit.isEmpty() ? null : sequenceNodesToVisit.get(0))
        );
    }

    private String getDistanceLegInfoFromNodes(Node from, Node to) {
        String distLegInfo = from == null || to == null ? "--------------" : String.format(
                "--> {%4s} -->", this.env.getDistanceSeconds(from, to));
        return distLegInfo;
    }


    public boolean hasFinishedRebalancing(int currentSimulationTime, Vehicle vehicle) {
        return currentSimulationTime >= this.env.getArrivalTimeAtNext(vehicle.getVisit());
    }

    private void assertConsistentArrivalDepartureTimesForVehicle(Vehicle vehicle) {
        assert vehicle.getLastVisitedNode().getArrival() >= vehicle.getLastVisitedNode().getEarliest() : String.format("Node=%s (%s) - %s - %s", env.network.getInfo(vehicle.getLastVisitedNode()), vehicle.getVisit(), vehicle.getJourney(), String.valueOf(vehicle.getVisitTrack()));

        if (!vehicle.isParked()) {
            assert vehicle.getLastVisitedNode().getDeparture() >= vehicle.getLastVisitedNode().getArrival() : String.format("Dep=%s >= Arr=%s (%s) - %s", vehicle.getLastVisitedNode().getDeparture(), vehicle.getLastVisitedNode().getArrival(), vehicle.getVisit(), vehicle.getJourney());
            assert vehicle.getLastVisitedNode().getDeparture() >= vehicle.getLastVisitedNode().getEarliestDeparture() : String.format("Dep=%s >= EarDep=%s (%s) - %s", vehicle.getLastVisitedNode().getDeparture(), vehicle.getLastVisitedNode().getEarliestDeparture(), vehicle.getVisit(), vehicle.getJourney());
        }
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
            Logging.logger.info("     Rejected = " + nodesFromRejectedUsers.size() + " (" + Sets.intersection(new HashSet<>(targets), new HashSet<>(nodesFromRejectedUsers)).size() + ")");
            Logging.logger.info("Hired origins = " + nodesFromVehicleOrigins.size() + " (" + Sets.intersection(new HashSet<>(targets), new HashSet<>(nodesFromVehicleOrigins)).size() + ")");
            Logging.logger.info(" Pickup unmet = " + nodesFromUnmetServiceLevelUsers.size() + " (" + Sets.intersection(new HashSet<>(targets), new HashSet<>(nodesFromUnmetServiceLevelUsers)).size() + ")");
        }
//        } else if (rebalanceUtil.strategy instanceof RebalanceHeuristic) {
//            targets = Vehicle.setOfHotPoints;
//        }
        return targets;
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

                    Logging.logger.info("{}", String.format("Users=%s are assigned to vehicles %s (%s) and %s (%s).\n", intersection, v1, v1.getId(), v2, v2.getId()));

                    Logging.logger.info(v1.getVisit().toString());
                    Logging.logger.info(v2.getVisit().toString());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean rejectedUnassignedFinishedSetsAreConsistent() {
        List<User> inVehicleRequests = Vehicle.getUsersFrom(listVehicles);
        if (requestManager.deniedRequests.size() + requestManager.unassignedRequests.size() + requestManager.finishedRequests.size() + inVehicleRequests.size() == requestManager.allRequests.size()) {
            return true;
        } else {
            printCurrentStatus("Inconsistency found");
            return false;
        }
    }

    public boolean thereAreNoRepeatedRequests(List<User> allRequests) {
        return (new HashSet<>(allRequests)).size() == allRequests.size();
    }


    public String getCollectionInfo(String label, Collection collection) {
        List col = new ArrayList(collection);
        Collections.sort(col);
        return String.format("# %15s (%d): %s", label, collection.size(), collection);
    }

    private void printCurrentStatus(String label) {
        Logging.logger.info("\n######################### " + label + " ###########################");
        List<User> inVehicleRequests = Vehicle.getUsersFrom(listVehicles);
        assert inVehicleRequests.size() == new HashSet<>(inVehicleRequests).size();
        Logging.logger.info(getCollectionInfo("Denied", requestManager.deniedRequests));
        Logging.logger.info(getCollectionInfo("Unassigned", requestManager.unassignedRequests));
        Logging.logger.info(getCollectionInfo("Finished", requestManager.finishedRequests));
        Logging.logger.info(getCollectionInfo("In-Vehicle", inVehicleRequests));
        Logging.logger.info(getCollectionInfo("All requests", requestManager.allRequests.values()));
        List<User> diff = new ArrayList(requestManager.allRequests.values());
        diff.removeAll(inVehicleRequests);
        Logging.logger.info(getCollectionInfo("Diff:", diff));
    }

    public Solution getSol() {
        return sol;
    }

    public Set<Vehicle> getListVehicles() {
        return listVehicles;
    }
}
