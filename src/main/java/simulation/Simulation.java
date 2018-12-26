package simulation;

import dao.Dao;
import helper.HelperIO;
import helper.MethodHelper;
import model.User;
import model.Vehicle;

import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public abstract class Simulation {

    public static final byte ALL_INFO = 1;
    public static final byte ROUND_INFO = 2;
    public static final byte NO_INFO = 0;


    // Solution coming from simulation
    protected Solution sol; //Simulation solution

    /* Rebalancing */
    protected int maxRoundsIdleBeforeRebalance;

    /* Deactivating, hiring */
    protected int maxRoundsIdleBeforeDeactivating;
    protected boolean isAllowedToHire; // Simulation can hire new vehicles as needed
    protected boolean isAllowedToLowerServiceLevel; // Simulation is allowed to

    /*Scenario*/
    protected String serviceRateScenarioLabel;
    protected String segmentationScenarioLabel;

    /* TIME HORIZON */
    protected int timeWindow; // Size of time bins
    protected int timeHorizon; // Total time horizon
    protected int maxDelayExtensionsBeforeHiring; // How many times a customer can have its request deferred before hiring new vehicle

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
    //TODO hot_PK_list


    public Simulation(int initialFleetSize,
                      int vehicleCapacity,
                      int maxNumberOfTrips,
                      int timeWindow,
                      int timeHorizon,
                      int maxRoundsIdleBeforeRebalance,
                      int deactivationFactor,
                      int maxDelayExtensionsBeforeHiring,
                      boolean isAllowedToHire,
                      boolean isAllowedToLowerServiceLevel) {

        /* TIME HORIZON */
        this.timeWindow = timeWindow; // Size of time bins
        this.timeHorizon = timeHorizon; // Total time horizon
        this.maxDelayExtensionsBeforeHiring = maxDelayExtensionsBeforeHiring; // Delay multiplier before hiring new vehicle

        /* REBALANCING */
        this.maxRoundsIdleBeforeRebalance = maxRoundsIdleBeforeRebalance;

        /* DEACTIVATING */
        this.maxRoundsIdleBeforeDeactivating = deactivationFactor * maxRoundsIdleBeforeRebalance;
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


        listVehicles = MethodHelper.createListVehicles(initialFleetSize, vehicleCapacity, true, leftTW); // List of vehicles
        //TODO hot_PK_list
    }

    public abstract Set<User> getServicedUsersDynamicSizedFleet(int currentTime);

    public static void reset() {
        return;
    }

    /**
     * Vehicle can:
     * - Finish rebalance
     * - Start rebalance
     * - Be deactivated
     * - Have parking nodes (origin, stop) updated in departure
     */
    public void updateFleetStatus() {
        ////// 1 - GET FINISHED USERS (before current time) ////////////////////////////////////////////////////////
        Set<Vehicle> setDeactivate = new HashSet<>();

        // Vehicles not servicing users nor rebalancing
        Map<Integer, Vehicle> candidateVehiclesToRebalance = new HashMap<>();

        // Loop vehicles to get set of finished requests and set of active vehicles (servicing users OR rebalancing)
        for (Vehicle v : listVehicles) {

            // Return set of users and update rebalancing vehicles
            Set<User> serviced = v.getServicedUsersUntil(rightTW);

            if (serviced != null) {
                finishedRequests.addAll(serviced);
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

                // Only rebalance if there are still passengers waiting
                //if (!setWaitingUsers.isEmpty()) {

                // Check if it is candidate to rebalance
                if (v.getRoundsIdle() >= maxRoundsIdleBeforeRebalance) {
                    if (v.getRoundsIdle() < maxRoundsIdleBeforeDeactivating) {
                        // System.out.println("REBALANCE - - - "+ v.getId() + " - - - - - " + v + " - " + v.getRoundsIdle() + "----" + maxRoundsIdleBeforeRebalance);
                        candidateVehiclesToRebalance.put(v.getId(), v);
                        // Vehicle can be deactivated
                    } else if (v.canDiscard(maxRoundsIdleBeforeDeactivating)) {
                        // Cannot discard vehicle, it is part of the original fleet
                        setDeactivate.add(v);
                    }
                }
                // }
            }

            v.updateMiddle(rightTW);
        }

        listVehicles.removeAll(setDeactivate);

        /*#********************************************************************************************************/
        ////// REBALANCING /////////////////////////////////////////////////////////////////////////////////////////
        /*#********************************************************************************************************/

        /// Rescheduling empty vehicles ////////////////////////////////////////////////////////////////////////////////
        rebalancingTime = System.nanoTime();
        Method.rebalanceVehicles(candidateVehiclesToRebalance);
        rebalancingTime = (System.nanoTime() - rebalancingTime) / 1000000;

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
                setWaitingUsers,
                finishedRequests,
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
        List<User> rejectedUsers = setWaitingUsers.stream()
                .filter(u -> !u.canBePickedUp(rightTW))
                .collect(Collectors.toList());

        deniedRequests.addAll(rejectedUsers);
        setWaitingUsers.removeAll(rejectedUsers);

        System.out.println("Getting serviced users...");
        ///// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES /////////////////////////////////////////
        Set<User> setScheduledUsers = getServicedUsersDynamicSizedFleet(rightTW);
        System.out.println("Finished.");

        ///// 4 - REMOVE SERVICED USERS FROM WAITING SET////////////////////////////////////////////////////////////////
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
            updateFleetStatus();

            // Pool, service, and reject users /////////////////////////////////////////////////////////////////////////
            System.out.println("Updating demand status...");
            updateDemandStatus();


            printRoundInfo(infoLevel);

            //// UPDATING TW ///////////////////////////////////////////////////////////////////////////////////////////
            leftTW = rightTW;
            rightTW = leftTW + timeWindow;
            roundCount = roundCount + 1;

        } while (!setWaitingUsers.isEmpty() || roundCount < totalRounds || activeFleet);

        // Print detailed journeys for each vehicle
        if (infoLevel == Simulation.ALL_INFO) {
            System.out.println(HelperIO.printJourneys(listVehicles));

            // System.out.println("GEOJSON DATA");
            // Dao dao = Dao.getInstance();
            for (Vehicle v : listVehicles) {
                System.out.println(v.getOrigin().getNetworkId());
                //System.out.println(v.getInfo());

                //System.out.println(v.getJourneyInfo());
                //dao.printGeoJsonJourney(v.getJourney());
            }
        }

        // Save solution to file (summary of rounds)
        sol.save();
        sol.saveUserInfo(finishedRequests);
        //sol.saveGeoJson();
    }
}