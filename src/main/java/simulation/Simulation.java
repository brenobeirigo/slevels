package simulation;

import config.Rebalance;
import dao.Dao;
import helper.HelperIO;
import helper.MethodHelper;
import model.RebalanceEpisode;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeStop;
import model.node.NodeTargetRebalancing;
import org.boon.collections.ConcurrentHashSet;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Simulation {


    public static final byte PRINT_ALL_ROUND_INFO = 1;
    public static final byte PRINT_SUMMARY_ROUND_INFO = 2;
    public static final byte PRINT_NO_ROUND_INFO = 0;

    public static final String SAVE_VEHICLE_ROUND_GEOJSON = "save_vehicle_round_geojson";
    public static final String SAVE_REQUEST_INFO_CSV = "save_request_info_csv";
    public static final String SAVE_ROUND_INFO_CSV = "save_round_info_csv";

    // Print info in console
    public static final String SHOW_ALL_VEHICLE_JOURNEYS = "show_all_vehicle_journeys";
    public static final String SHOW_ROUND_FLEET_STATUS = "show_round_fleet_status";
    public static final String SHOW_ROUND_INFO = "show_round_info";

    // Duration
    public static final int DURATION_SINGLE_RIDE = 0;
    public static final int DURATION_1H = 3600;
    public static final int DURATION_3H = 3 * 3600;


    // Solution coming from simulation
    protected Solution sol; //Simulation solution

    /* Hiring, unbounding, and rebalancing */
    protected int contractDuration;
    protected boolean isAllowedToHire; // Simulation can hire new vehicles as needed
    protected boolean isAllowedToLowerServiceLevel;
    protected boolean isAllowedToRebalance;
    protected Rebalance rebalanceUtil;

    // Matching
    protected boolean sortWaitingUsersByClass;

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
    protected int leftTW, rightTW; // Left and right time windows (rightTW = current time)

    /* ROUND INFO */
    protected int totalRounds; // How many rounds of time horizon will be pooled
    protected int roundCount;
    protected boolean activeFleet; //True if a single vehicle is still working (rebalancing, picking up, etc.)

    /* SETS OF VEHICLES AND REQUESTS */
    protected Map<Integer, User> allRequests; // Dictionary of all users
    protected List<User> setWaitingUsers; // Requests whose pickup time is lower than the current time
    protected List<User> listPooledUsersTW;  // Requests pooled within TW
    protected Set<User> deniedRequests; // Requests with expired pickup time
    protected Set<User> finishedRequests; // Requests whose DP node was visited
    protected List<Vehicle> listVehicles; // List of vehicles
    protected List<Vehicle> listHiredVehicles; //List of hired vehicles
    protected Set<Vehicle> setDeactivated; // Vehicles to be deactivated in round
    protected Set<Vehicle> setHired; // Current set of hired vehicles
    protected List<User> roundRejectedUsers;
    protected List<User> roundPrivateRides;
    protected Map<String, Long> runTimes;

    public Simulation(int initialFleetSize,
                      int vehicleCapacity,
                      int maxNumberOfTrips,
                      int timeWindow,
                      int timeHorizon,
                      boolean isAllowedToRebalance,
                      int contractDuration,
                      boolean isAllowedToHire,
                      boolean isAllowedToLowerServiceLevel,
                      boolean sortWaitingUsersByClass,
                      Rebalance rebalanceUtil) {
        /*MATCHING*/
        this.sortWaitingUsersByClass = sortWaitingUsersByClass;

        /* REBALANCING */
        this.isAllowedToRebalance = isAllowedToRebalance;
        this.rebalanceUtil = rebalanceUtil;

        /* TIME HORIZON */
        this.timeWindow = timeWindow; // Size of time bins
        this.timeHorizon = timeHorizon; // Total time horizon

        /* DEACTIVATING */
        this.contractDuration = contractDuration;
        this.isAllowedToHire = isAllowedToHire;
        this.isAllowedToLowerServiceLevel = isAllowedToLowerServiceLevel;

        /* VEHICLE INFO */
        this.initialFleetSize = initialFleetSize; // Fleet size
        this.vehicleCapacity = vehicleCapacity; // Number of seats (1 - 4)

        /* POOLING DATA */
        this.maxNumberOfTrips = maxNumberOfTrips; //How many trips are pooled in time horizon
        totalRounds = timeHorizon / timeWindow; // How many rounds of time horizon will be pooled
        time_slot = totalRounds * timeWindow;
        start_timestamp = 0; // (00:00:00) Initial timestamp for pooling data
        leftTW = start_timestamp; // Left and right time windows (rightTW = current time)
        rightTW = leftTW + timeWindow;
        roundCount = 0;

        /* SETS OF VEHICLES AND REQUESTS */
        allRequests = new HashMap<>(); // Dictionary of all users
        setWaitingUsers = new ArrayList<>(); // Requests whose pickup time is lower than the current time
        roundPrivateRides = new ArrayList<>();
        deniedRequests = new HashSet<>(); // Requests with expired pickup time
        finishedRequests = new HashSet<>(); // Requests whose DP node was visited
        maxRoundsIdle = 40;
        activeFleet = false;
        setHired = new HashSet<>();

        runTimes = new HashMap<>();

        listVehicles = MethodHelper.createListVehicles(initialFleetSize, vehicleCapacity, true, leftTW); // List of vehicles
        listHiredVehicles = new ArrayList<>();
        //TODO hot_PK_list
    }

    public static void reset() {
        return;
    }

    public static int getInfoLevel(String infoLevelLabel) {
        int infoLevel;
        switch (infoLevelLabel) {
            case "print_all_round_info":
                infoLevel = Simulation.PRINT_ALL_ROUND_INFO;
                break;
            case "print_no_round_info":
                infoLevel = Simulation.PRINT_NO_ROUND_INFO;
                break;
            case "print_summary_round_info":
                infoLevel = Simulation.PRINT_SUMMARY_ROUND_INFO;
                break;
            default:
                infoLevel = Simulation.PRINT_SUMMARY_ROUND_INFO;
        }
        return infoLevel;
    }

    public boolean canEndContract(Vehicle v, int currentTime) {
        // Is the vehicle hired?
        // Has the contracted deadline been met?

        /*
            System.out.println(v.getContractDeadline() + "-"+ v.getVisit());
            if (v.isParked()) {
                System.out.println("Vehicle is parked!");
            } else if (v.isRebalancing()) {
                System.out.println("OMG! Rebalancing!");
            } else if (v.isCruising()) {
                System.out.println("OMG! Cruising!");
            }else{
                System.out.println("OMG! Servicing!");
            }
            */
        return v.isHired() && currentTime >= v.getContractDeadline();
    }

    public abstract Set<User> getServicedUsersDynamicSizedFleet(int currentTime);

    public void updateFleetStatusParallel() {
        ////// 1 - GET FINISHED USERS (before current time) ////////////////////////////////////////////////////////
        setDeactivated = new HashSet<>();

        // Vehicles not servicing users nor rebalancing
        ConcurrentHashSet<Vehicle> candidateVehiclesToRebalance = new ConcurrentHashSet<>();

        //Instant before = Instant.now();
        // Loop vehicles to get set of finished requests and set of active vehicles (servicing users OR rebalancing)
        listVehicles.parallelStream().filter(v ->
        {

            // Limits the hiring contract
            v.increaseActiveRounds();

            // Return set of users and update rebalancing vehicles
            Set<User> serviced = v.getServicedUsersUntil(rightTW);

            if (serviced != null) {
                finishedRequests.addAll(serviced);
            }

            // If vehicle is hired, it has to be deactivated as soon as it delivers its last customer
            if (canEndContract(v, rightTW)) {
                //setDeactivated.add(v);
                setHired.remove(v);
                return false;
            } else
                return true;
        }).map(v -> {

            // If vehicle is not parked, it is either cruising, servicing or rebalancing
            if (!v.isParked()) {
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

                v.updateDeparture(rightTW);

                // Vehicle is not servicing customers
                v.increaseRoundsIdle();

                // Check if it is candidate to rebalance
                if (isAllowedToRebalance) {
                    // System.out.println("REBALANCE - - - "+ v.getId() + " - - - - - " + v + " - " + v.getRoundsIdle() + "----" + maxRoundsIdleBeforeRebalance);
                    candidateVehiclesToRebalance.add(v);
                }
            }
            v.updateMiddle(rightTW);
            return v;
        }).collect(Collectors.toList());

        //Instant after = Instant.now();
        //Duration duration = Duration.between(before, after);
        //System.out.println("Duration update (" + listVehicles.size() + " vehices): " + duration.toMillis());
        // Updating vehicle lists
        //listVehicles.removeAll(setDeactivated);
        //setHired.removeAll(setDeactivated);
    }

    /**
     * Vehicle can:
     * - Finish rebalance
     * - Start rebalance
     * - Be deactivated
     * - Have parking nodes (origin, stop) updated in departure
     */
    public Set<Vehicle> updateFleetStatus() {
        ////// 1 - GET FINISHED USERS (before current time) ////////////////////////////////////////////////////////
        setDeactivated = new HashSet<>();

        // Vehicles not servicing users nor rebalancing
        Set<Vehicle> candidateVehiclesToRebalance = new HashSet<>();

        // Loop vehicles to get set of finished requests and set of active vehicles (servicing users OR rebalancing)
        for (Vehicle v : listVehicles) {

            // Limits the hiring contract
            v.increaseActiveRounds();

            // Return set of users and update rebalancing vehicles
            Set<User> serviced = v.getServicedUsersUntil(rightTW);

            if (serviced != null) {
                finishedRequests.addAll(serviced);
            }

            // If vehicle is hired, it have to be deactivated as soon as it delivers its last customer
            if (canEndContract(v, rightTW)) {
                setDeactivated.add(v);
            }

            // If vehicle is not parked, it is either cruising, servicing or rebalancing
            if (!v.isParked()) {
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

                v.updateDeparture(rightTW);

                // Vehicle is not servicing customers
                v.increaseRoundsIdle();

                // Check if it is candidate to rebalance
                if (isAllowedToRebalance) {
                    // System.out.println("REBALANCE - - - "+ v.getId() + " - - - - - " + v + " - " + v.getRoundsIdle() + "----" + maxRoundsIdleBeforeRebalance);
                    candidateVehiclesToRebalance.add(v);
                }
            }
            v.updateMiddle(rightTW);
            if (v.isRebalancing() && v.getMiddleNode() == null)
                System.out.println(v);
        }

        // Updating vehicle lists
        listVehicles.removeAll(setDeactivated);
        setHired.removeAll(setDeactivated);

        return candidateVehiclesToRebalance;
    }


    /**
     * Pool new requests (within TW) from database
     */
    public void updateSetWaitingUsers() {

        // Clean list of pooled users
        listPooledUsersTW = new ArrayList<>();

        // After the number of rounds stop pooling requests but finish waiting requests
        if (roundCount < totalRounds) {

            // Dictionary of pooled requests inside time slot
            listPooledUsersTW = Dao.getInstance()
                    .getListTripsClassed(
                            timeWindow,
                            vehicleCapacity,
                            maxNumberOfTrips);

            // Add pooled requests into waiting list
            setWaitingUsers.addAll(listPooledUsersTW);

            // Sort by class and earliest time
            if (sortWaitingUsersByClass) {
                Collections.sort(setWaitingUsers, Comparator.comparing(User::getPerformanceClass)
                        .thenComparing(User::getReqTime));

                /*System.out.println("##### Users");
                for (User e: setWaitingUsers) {
                    System.out.println(e + String.valueOf(e.getReqTime()));
                }*/
            }

            // Store all requests
            for (User e : listPooledUsersTW) {
                allRequests.put(e.getId(), e);
            }
        }
    }

    /**
     * Round info
     *
     * @param showRoundInfo If true, show status of all vehicles
     */
    public void computeRoundInfoAndShow(boolean showRoundInfo, boolean saveRoundCsv, boolean showVehicleStatusInfo) {

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
                    setWaitingUsers,
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

    public void updateDemandStatus() {

        ////// 1 - GET USERS INSIDE TW /////////////////////////////////////////////////////////////////////////////////
        updateSetWaitingUsers();

        ///// 2 - REMOVE USERS THAT CANNOT BE PICKED UP  ///////////////////////////////////////////////////////////////
        roundRejectedUsers = setWaitingUsers.stream()
                .filter(u -> !u.canBePickedUp(rightTW))
                .collect(Collectors.toList());

        // Update request status
        roundRejectedUsers.forEach(User::computeRejection);
        deniedRequests.addAll(roundRejectedUsers);
        setWaitingUsers.removeAll(roundRejectedUsers);
        roundPrivateRides.clear();

        ///// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES /////////////////////////////////////////
        Set<User> setScheduledUsers = getServicedUsersDynamicSizedFleet(rightTW);

        ///// 4 - REMOVE SERVICED USERS FROM WAITING SET////////////////////////////////////////////////////////////////
        // Vehicles will become idle here (i.e., parked)
        setWaitingUsers.removeAll(setScheduledUsers);

    }

    /**
     * Join new requests (current period) with all matched requests (not yet picked up).
     * There can have better matches (i.e., requests can be serviced by different vehicles)
     *
     * @return all requests
     */
    public List<User> getExtendedRequestList() {
        List<User> allRequests = new ArrayList<>(setWaitingUsers);

        // Requests can still be picked up by other vehicles, add them to request list
        for (Vehicle vehicle : listVehicles) {
            if (vehicle.isServicing()) {
                allRequests.addAll(vehicle.getVisit().getRequests());
            }
        }
        return allRequests;
    }

    public void rebalance(Set<Vehicle> idleVehicles) {
        List<Node> targets = null;
        if (rebalanceUtil.method.equals(Rebalance.METHOD_OPTIMAL)) {
            if (roundRejectedUsers != null && !roundRejectedUsers.isEmpty()) {
                targets = roundRejectedUsers.stream().map(User::getNodePk).collect(Collectors.toList());
            } else if (roundPrivateRides != null && !roundPrivateRides.isEmpty()) {
                targets = roundPrivateRides.stream().map(User::getNodePk).collect(Collectors.toList());
            }
        } else if (rebalanceUtil.method.equals(Rebalance.METHOD_HEURISTIC)) {
            targets = Vehicle.setOfHotPoints;
        }
        rebalanceUtil.executeStrategy(idleVehicles, targets);
    }

    public void run(Map<String, Boolean> infoHandling) {

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

            // Rebalance, deactivate, and update parking nodes /////////////////////////////////////////////////////////
            // Move vehicles, Compute serviced users, remove hired
            runTimes.put(Solution.TIME_UPDATE_FLEET_STATUS, System.nanoTime());
            Set<Vehicle> idleVehicles = updateFleetStatus();
            // TODO Preliminary tests show that parallel stream is slower. Can more cores be of any help?
            //updateFleetStatusParallel();
            runTimes.put(Solution.TIME_UPDATE_FLEET_STATUS, System.nanoTime() - runTimes.get(Solution.TIME_UPDATE_FLEET_STATUS));

            // Rebalance idle vehicles
            if (isAllowedToRebalance)
                runTimes.put(Solution.TIME_REBALANCING_FLEET, System.nanoTime());
            rebalance(idleVehicles);
            runTimes.put(Solution.TIME_REBALANCING_FLEET, System.nanoTime() - runTimes.get(Solution.TIME_REBALANCING_FLEET));

            // Pool, service, and reject users /////////////////////////////////////////////////////////////////////////
            runTimes.put(Solution.TIME_UPDATE_DEMAND, System.nanoTime());
            updateDemandStatus();
            runTimes.put(Solution.TIME_UPDATE_DEMAND, System.nanoTime() - runTimes.get(Solution.TIME_UPDATE_DEMAND));

            // Save solution and print round info
            if (infoHandling.get(Simulation.SHOW_ROUND_INFO) || infoHandling.get(Simulation.SAVE_ROUND_INFO_CSV))
                computeRoundInfoAndShow(
                        infoHandling.get(Simulation.SHOW_ROUND_INFO),
                        infoHandling.get(Simulation.SAVE_ROUND_INFO_CSV),
                        infoHandling.get(Simulation.SHOW_ROUND_FLEET_STATUS)
                );

            //System.out.println(HelperIO.printJourneys(listHiredVehicles));

            //// UPDATING TW ///////////////////////////////////////////////////////////////////////////////////////////
            leftTW = rightTW;
            rightTW = leftTW + timeWindow;
            roundCount = roundCount + 1;

        } while (!setWaitingUsers.isEmpty() || roundCount < totalRounds || activeFleet);

        // Print detailed journeys for each vehicle
        if (infoHandling.get(Simulation.SHOW_ALL_VEHICLE_JOURNEYS)) {
            sol.printAllJourneys(listVehicles);
        }

        // Saving vehicles traces
        if (infoHandling.get(Simulation.SAVE_VEHICLE_ROUND_GEOJSON))
            sol.saveGeoJsonPerVehicle(listVehicles);

        // Save solution to file (summary of rounds)
        if (infoHandling.get(Simulation.SAVE_ROUND_INFO_CSV))
            sol.saveRoundInfo();

        // Saving user info (how user was serviced, for example, pickup, dropoff, hired vehicle, delay, etc.)
        if (infoHandling.get(Simulation.SAVE_REQUEST_INFO_CSV))
            sol.saveUserInfo(allRequests);
    }

    /**
     * Add user to vehicle and setup visit.
     * Called when best visit is determined.
     */
    public void setup(Visit visit) {

        // Check if rebalancing was interrupted to pick up user
        if (visit.getVehicle().isRebalancing()) {
            if ((visit.getVehicle().getLastVisitedNode() instanceof NodeStop) && visit.getVehicle().getMiddleNode() == null)
                System.out.println("AQUI");
            interruptRebalancing(visit);
        }

        // Add visit to vehicle (circular)
        visit.getVehicle().setVisit(visit);

        for (User request : visit.getRequests()) {
            request.setCurrentVisit(visit);
        }

        visit.updateArrivalSoFar();

        // Vehicle is not idle
        visit.getVehicle().setRoundsIdle(0);
    }

    private void interruptRebalancing(Visit visit) {

        //System.out.println("STOPPED REBALANCING!" + this);
        Vehicle vehicle = visit.getVehicle();

        Node currentNode = vehicle.getLastVisitedNode();
        Node middleNode = vehicle.getMiddleNode();
        NodeTargetRebalancing target = (NodeTargetRebalancing) vehicle.getVisit().getTargetNode();

        // Target was not reached, it goes back to attractive points
        if (this.rebalanceUtil.reinsertTargets && !target.isReached()) {

            //TODO Does re-adding the node helps? It looks like YES!
            //System.out.println("STOPPED REB.:" + target.getGenNode().getUrgent() + " - " + target.getGenNode().getArrival() + " :" + this.getSequenceVisits().getFirst() + "-"+target+"-" + target.getGenNode());

            // If a node was not reached, it means vehicles keep being assigned around its region.
            // The immediate demand factor is therefore incremented to keep attracting vehicles to this region
            if (this.rebalanceUtil.useUrgentKey)
                target.increaseUrgency();

            makeTargetRebalancingCandidate(target);
        }

        // Vehicle is no longer rebalancing (User was inserted)
        vehicle.stoppedRebalancingToPickup();

        if (middleNode == null)
            System.out.println("Middle node");

        double distTraveledKmCurrentMiddle = Dao.getInstance().getDistKm(currentNode, middleNode);

        vehicle.increaseDistanceTraveledRebalancing(distTraveledKmCurrentMiddle);

        // Create structure that helps to understand what is happening in the rebalancing process
        if (rebalanceUtil.createEpisode) {

            int roundsToFindUser = (Dao.getInstance().getDistSec(currentNode, middleNode) / this.timeWindow);

            double distTraveledKm = Dao.getInstance().getDistKm(currentNode, target);

            RebalanceEpisode r = new RebalanceEpisode(
                    currentNode.getNetworkId(),
                    target.getNetworkId(),
                    middleNode.getNetworkId(),
                    vehicle.getRoundsIdle(),
                    distTraveledKmCurrentMiddle,
                    distTraveledKm,
                    roundsToFindUser);

            if (rebalanceUtil.showInfo) {
                System.out.println(r);
            }
        }
    }

    private void makeTargetRebalancingCandidate(NodeTargetRebalancing target) {
        Vehicle.setOfHotPoints.add(target);
    }


    /******************************************************************************************************************/
    /***** ASSERTIONS *************************************************************************************************/
    /******************************************************************************************************************/

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

}