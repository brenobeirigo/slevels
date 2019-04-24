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
import model.node.NodeTargetRebalancing;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Simulation {

    public static final byte ALL_INFO = 1;
    public static final byte ROUND_INFO = 2;
    public static final byte NO_INFO = 0;

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
    protected long roundTimeNanoSec;
    protected long rebalancingTime;

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

    //TODO hot_PK_list


    public Simulation(int initialFleetSize,
                      int vehicleCapacity,
                      int maxNumberOfTrips,
                      int timeWindow,
                      int timeHorizon,
                      boolean isAllowedToRebalance,
                      int contractDuration,
                      boolean isAllowedToHire,
                      boolean isAllowedToLowerServiceLevel,
                      Rebalance rebalanceUtil) {


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
        roundTimeNanoSec = 0;

        /* SETS OF VEHICLES AND REQUESTS */
        allRequests = new HashMap<>(); // Dictionary of all users
        setWaitingUsers = new ArrayList<>(); // Requests whose pickup time is lower than the current time
        deniedRequests = new HashSet<>(); // Requests with expired pickup time
        finishedRequests = new HashSet<>(); // Requests whose DP node was visited
        maxRoundsIdle = 40;
        activeFleet = false;
        setHired = new HashSet<>();


        listVehicles = MethodHelper.createListVehicles(initialFleetSize, vehicleCapacity, true, leftTW); // List of vehicles
        listHiredVehicles = new ArrayList<>();
        //TODO hot_PK_list
    }

    public static void reset() {
        return;
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

    /**
     * Vehicle can:
     * - Finish rebalance
     * - Start rebalance
     * - Be deactivated
     * - Have parking nodes (origin, stop) updated in departure
     */
    public void updateFleetStatus() {
        ////// 1 - GET FINISHED USERS (before current time) ////////////////////////////////////////////////////////
        setDeactivated = new HashSet<>();

        // Vehicles not servicing users nor rebalancing
        List<Vehicle> candidateVehiclesToRebalance = new ArrayList<>();

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
                continue;
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
        }

        // Updating vehicle lists
        listVehicles.removeAll(setDeactivated);
        setHired.removeAll(setDeactivated);


        /*#********************************************************************************************************/
        ////// REBALANCING /////////////////////////////////////////////////////////////////////////////////////////
        /*#********************************************************************************************************/

        if (isAllowedToRebalance) {
            /// Rescheduling empty vehicles ////////////////////////////////////////////////////////////////////////////////
            rebalancingTime = System.nanoTime();
            rebalanceUtil.rebalanceVehicles(candidateVehiclesToRebalance);
            rebalancingTime = (System.nanoTime() - rebalancingTime) / 1000000;
        }

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

            // Store all requests
            for (User e : listPooledUsersTW) {
                allRequests.put(e.getId(), e);
            }
        }
    }

    /**
     * Round info
     *
     * @param infoLevel If true, show status of all vehicles
     */
    public void printRoundInfo(int infoLevel) {


         /*#*********************************************************************************************************
             ///// Print round information  ////////////////////////////////////////////////////////////////////////////
            /*#********************************************************************************************************/


        long runTime = (System.nanoTime() - roundTimeNanoSec) / 1000000;
        // Print round statistics (Round info is also calculated here)
        String roundSnapshot = sol.calculateRoundStats(
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
                runTime,
                rebalancingTime);


        if (infoLevel == Simulation.ROUND_INFO || infoLevel == Simulation.ALL_INFO) {
            // Print the time window reading
            System.out.println(HelperIO.getHeaderTW(start_timestamp,
                    time_slot,
                    leftTW,
                    rightTW,
                    listPooledUsersTW,
                    allRequests,
                    initialFleetSize,
                    timeWindow,
                    roundCount,
                    totalRounds
            ));

            System.out.println(roundSnapshot);
        }


        if (infoLevel == Simulation.ALL_INFO) {

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



        System.out.println("Getting serviced users...");
        ///// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES /////////////////////////////////////////
        Set<User> setScheduledUsers = getServicedUsersDynamicSizedFleet(rightTW);
        System.out.println("Finished.");

        ///// 4 - REMOVE SERVICED USERS FROM WAITING SET////////////////////////////////////////////////////////////////
        // Vehicles will become idle here (i.e., parked)
        setWaitingUsers.removeAll(setScheduledUsers);

    }

    public void run(int infoLevel) {

        if (Files.exists(this.sol.getOutputFile())) {
            System.out.println(this.sol.getOutputFile() + " already exists.");
            return;
        } else {
            System.out.println(this.sol.getOutputFile() + " instance being processed...");
        }

        // Loop rounds of TW
        do {
            // Status will change if vehicles are still working or there are users to service
            activeFleet = false;

            roundTimeNanoSec = System.nanoTime();


            // Rebalance, deactivate, and update parking nodes /////////////////////////////////////////////////////////
            System.out.println("Updating fleet status...");
            // Get serviced users
            // Remove hired
            // Rebalance vehicles
            updateFleetStatus();

            // Pool, service, and reject users /////////////////////////////////////////////////////////////////////////
            System.out.println("Updating demand status...");
            updateDemandStatus();


            printRoundInfo(infoLevel);

            //// UPDATING TW ///////////////////////////////////////////////////////////////////////////////////////////
            leftTW = rightTW;
            rightTW = leftTW + timeWindow;
            roundCount = roundCount + 1;

            //System.out.println(HelperIO.printJourneys(listHiredVehicles));

        } while (!setWaitingUsers.isEmpty() || roundCount < totalRounds || activeFleet);

        // Print detailed journeys for each vehicle
        if (infoLevel == Simulation.ALL_INFO) {
            System.out.println(HelperIO.printJourneys(listVehicles));

            System.out.println("GEOJSON DATA");
            // Dao dao = Dao.getInstance();
            for (Vehicle v : listVehicles) {
                System.out.println(v.getOrigin().getNetworkId());
                //System.out.println(v.getInfo());

                //System.out.println(v.getJourneyInfo());
                //ServerUtil.printGeoJsonJourney(v, Simulation.);
            }
        }
        // Saving vehicles traces
        sol.saveGeoJsonPerVehicle(listVehicles);

        // Save solution to file (summary of rounds)
        sol.save();

        // Saving user info (how user was serviced, for example, pickup, dropoff, hired vehicle, delay, etc.)
        sol.saveUserInfo(allRequests);


    }


    /**
     * Add user to vehicle and setup visit.
     * Called when best visit is determined.
     */
    public void setup(Visit visit) {

        // Check if rebalancing was interrupted to pick up user
        if (visit.getVehicle().isRebalancing()) {
            interruptRebalancing(visit);
        }

        // Add visit to vehicle (circular)
        visit.getVehicle().setVisit(visit);

        // Vehicle set of users
        visit.getVehicle().setUsers(visit.getSetUsers());

        // Vehicle is not idle
        visit.getVehicle().setRoundsIdle(0);
    }

    private void interruptRebalancing(Visit visit) {

        //System.out.println("STOPPED REBALANCING!" + this);
        Vehicle vehicle = visit.getVehicle();

        Node currentNode = vehicle.getCurrentNode();
        Node middleNode = vehicle.getMiddleNode();
        NodeTargetRebalancing target = (NodeTargetRebalancing) vehicle.getVisit().getTargetNode();

        visit.getVehicle().getJourney().add(target);

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

        double distTraveledKmCurrentMiddle = Dao.getInstance().getDistKm(currentNode, middleNode);

        vehicle.increaseDistanceTraveledRebalancing(distTraveledKmCurrentMiddle);

        //double distTraveledKmCurrentMiddle = Dao.getInstance().getDistKm(currentNode, middleNode);

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
}