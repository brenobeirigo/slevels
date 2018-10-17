package simulation;


import config.Config;
import dao.Dao;
import helper.HelperIO;
import helper.MethodHelper;
import model.User;
import model.Vehicle;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;

import java.util.*;

public class Simulation {

    // Mark start execution time
    private long t1;
    private Solution sol; //Simulation solution

    /* TIME HORIZON */
    private int timeHorizon; // Size of time bins
    private int total_horizon; // Total time horizon

    /* VEHICLE INFO */
    private int nOfVehicles; // Fleet size
    private int vehicleCapacity; // Number of seats

    /* METHOD CONFIGURATION*/
    private int maxPermutationsFCFS;
    private boolean allPermutations;
    private boolean stopAtFirstBest;
    private boolean checkInParallel;

    /* POOLING DATA */
    private int maxNumberOfTrips; //How many trips are pooled in time horizon
    private int totalRounds; // How many rounds of time horizon will be pooled
    private int time_slot;
    private int start_timestamp; // (00:00:00) Initial timestamp for pooling data
    private int leftTW, rightTW; // Left and right time windows (rightTW = current time)
    private boolean run_ending_rounds; // Keep running rounds until all vehicles finish the requests
    private int countRounds;

    /* SETS OF VEHICLES AND REQUESTS */
    private Map<Integer, User> allRequests; // Dictionary of all users
    private List<User> setWaitingUsers; // Requests whose pickup time is lower than the current time
    private Set<User> deniedRequests; // Requests with expired pickup time
    private Set<User> finishedRequests; // Requests whose DP node was visited
    private List<Vehicle> listVehicles; // List of vehicles
    //TODO hot_PK_list


    public Simulation() {


        /* TIME HORIZON */
        timeHorizon = 30; // Size of time bins
        total_horizon = 600; // Total time horizon

        /* VEHICLE INFO */
        nOfVehicles = 1; // Fleet size
        vehicleCapacity = 1; // Number of seats (1 - 4)

        /* METHOD CONFIGURATION*/
        maxPermutationsFCFS = 5;
        allPermutations = true;
        stopAtFirstBest = true;
        checkInParallel = false;

        /* POOLING DATA */
        maxNumberOfTrips = 100; //How many trips are pooled in time horizon
        totalRounds = total_horizon / timeHorizon; // How many rounds of time horizon will be pooled
        time_slot = totalRounds * timeHorizon;
        start_timestamp = 0; // (00:00:00) Initial timestamp for pooling data
        leftTW = this.start_timestamp; // Left and right time windows (rightTW = current time)
        rightTW = 0;
        run_ending_rounds = true; // Keep running rounds until all vehicles finish the requests
        countRounds = 0;

        /* SETS OF VEHICLES AND REQUESTS */
        allRequests = new HashMap<>(); // Dictionary of all users
        setWaitingUsers = new ArrayList<>(); // Requests whose pickup time is lower than the current time
        deniedRequests = new HashSet<>(); // Requests with expired pickup time
        finishedRequests = new HashSet<>(); // Requests whose DP node was visited
        listVehicles = MethodHelper.createListVehicles(nOfVehicles, vehicleCapacity); // List of vehicles
        //TODO hot_PK_list

        // Mark start execution time
        t1 = System.nanoTime();
        sol = new Solution(nOfVehicles, maxNumberOfTrips, vehicleCapacity);

    }


    public void init() {
        /* Declare empty waiting list
        Repeat:
            leftTW:
                 - Eliminate serviced requests from waiting list
                 - Eliminate denied requests from waiting list
            RightTW:
            - Fill waiting list with collected requests in TW
            - Assign requests to vehicles (using optimization method)
            - Remove assigned vehicles from waiting list */

        // Loop number of rounds
        while (countRounds < totalRounds || run_ending_rounds) {

            // Wall time start of round
            long startWalltime = System.nanoTime();

            // Update current time
            rightTW = leftTW + timeHorizon;

            // Update previous current time
            leftTW = rightTW;


            /**********************************************************************************************************/
            ////// 1 - GET FINISHED USERS (before current time) ////////////////////////////////////////////////////////
            /**********************************************************************************************************/

            int remainingPassengers = 0;
            int active_vehicles = 0;

            Set<Vehicle> setActiveVehicles = new HashSet<>();

            // Loop vehicles to get set of finished requests and set of active vehicles (servicing users)
            for (Vehicle v : listVehicles) {

                // Update vehicle's requests according with rightmost bound of time windows
                Set<User> roundFinishedRequests = v.getServicedUsersUntil(rightTW);

                // if requests in vehicle v are finished
                if (roundFinishedRequests != null) {

                    // Add requests to the final list of finished
                    finishedRequests.addAll(roundFinishedRequests);
                }

                // If there are passengers after the update
                if (!v.getListUsers().isEmpty()) {

                    // Update the number of remaining passengers
                    remainingPassengers = remainingPassengers + v.getListUsers().size();

                    // Vehicles en-route
                    setActiveVehicles.add(v);
                }
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
                if (!(v.getCurrentNode() instanceof NodeDP
                        || v.getCurrentNode() instanceof NodePK)
                        && v.getListUsers().isEmpty()) {
                    v.getCurrentNode().setArrival(Math.max(rightTW, v.getCurrentNode().getArrival()));
                }
            }

            /**********************************************************************************************************/
            ////// 2 - Eliminate waiting users that can no longer be picked up /////////////////////////////////////////
            /**********************************************************************************************************/

            // Get requests whose latest times are up
            Set<User> setTimeUpRequest = new HashSet<>();

            // Latest pickup time of request expired
            for (User u : setWaitingUsers) {
                if (rightTW > u.getNodePk().getLatest())

                    //TODO: Here TW windows get flexible
                    setTimeUpRequest.add(u);

            }

            // Set of requests that have expired
            deniedRequests.addAll(setTimeUpRequest);

            // Remove time up requests from waiting set
            setWaitingUsers.removeAll(setTimeUpRequest);

            /**********************************************************************************************************/
            ////// 3 - GET USERS INSIDE TW /////////////////////////////////////////////////////////////////////////////
            /**********************************************************************************************************/

            // List of pooled users inside TW (only filled if countRounds < totalRounds)
            List<User> listUsersTW = new ArrayList<>();

            // After the number of rounds stop pooling requests but finish waiting requests
            if (countRounds < totalRounds) {

                // Dictionary of pooled requests inside time slot
                listUsersTW = Dao.getInstance().getListTrips(timeHorizon, maxNumberOfTrips);

                // Add pooled requests into waiting list
                setWaitingUsers.addAll(listUsersTW);

                // Store all requests
                for (User e : listUsersTW) {
                    allRequests.put(e.getId(), e);
                }
            }


            int countPairwise = 0;
            User[] reqs = setWaitingUsers.toArray(new User[setWaitingUsers.size()]);
            Set<String> validPair = new HashSet<>();
            int countImpossible = 0;
            for (int i = 0; i < reqs.length - 1; i++) {
                Node pk1 = reqs[i].getNodePk();
                Node dp1 = reqs[i].getNodeDp();
                for (int j = i + 1; j < reqs.length; j++) {
                    Node pk2 = reqs[j].getNodePk();
                    Node dp2 = reqs[j].getNodeDp();

                    if (pk1.getEarliest() >= dp2.getLatest()) {
                        countImpossible++;
                        continue;
                    }
                    if (pk2.getEarliest() >= dp1.getLatest()) {
                        countImpossible++;
                        continue;
                    }

                    Node[] seq1 = new Node[]{pk1, pk2, dp1, dp2};
                    Node[] seq2 = new Node[]{pk1, pk2, dp2, dp1};
                    Node[] seq3 = new Node[]{pk1, pk2, dp1, dp2};
                    Node[] seq4 = new Node[]{pk1, pk2, dp2, dp1};
                    if (Method.feasibleSequence(seq1) ||
                            Method.feasibleSequence(seq2) ||
                            Method.feasibleSequence(seq3) ||
                            Method.feasibleSequence(seq4)) {
                        //System.out.println(r1+"-"+r2);


                        countPairwise++;
                    }


                }

            }
            System.out.println("Valid combinations: " + countImpossible + "/" + countPairwise + "/" + setWaitingUsers.size() * setWaitingUsers.size());


            /**********************************************************************************************************
             ////// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES ///////////////////////////////////
             **********************************************************************************************************/

            // FIRST COME FIRST SERVE
            Set<User> setScheduledUsers = Method.getSolutionFCFS(
                    setWaitingUsers,
                    listVehicles,
                    allPermutations,
                    stopAtFirstBest,
                    rightTW,
                    maxPermutationsFCFS,
                    checkInParallel);

            // Remove scheduled requests from pool of waiting
            setWaitingUsers.removeAll(setScheduledUsers);

            // if there are no remaining passengers and service in all vehicles is finished
            if (countRounds >= totalRounds && remainingPassengers == 0) {

                System.out.println("####" + countRounds + " -- " + totalRounds);

                // All passengers have been attended, stop rounds
                run_ending_rounds = false;
            }

            // Update round count
            countRounds = countRounds + 1;

            /**********************************************************************************************************
             ///// Print round information  ///////////////////////////////////////////////////////////////////////////
             **********************************************************************************************************/

            // Print the time window reading
            System.out.println(HelperIO.getHeaderTW(start_timestamp,
                    time_slot,
                    leftTW,
                    rightTW,
                    listUsersTW,
                    allRequests,
                    nOfVehicles,
                    timeHorizon,
                    countRounds,
                    totalRounds
            ));

            // Print round statistics
            System.out.println(sol.getRoundStatistics(rightTW,
                    vehicleCapacity,
                    listVehicles,
                    finishedRequests,
                    deniedRequests,
                    allRequests,
                    (System.nanoTime() - startWalltime) / 1000000));


            // Print vehicle details
            System.out.println(HelperIO.getVehicleInfo(listVehicles,
                    rightTW,
                    true,
                    true,
                    true));
        }

        // Save solution to file
        sol.save();

        // Print detailed journeys for each vehicle
        System.out.println(HelperIO.printJourneys(listVehicles));

        //Final execution time
        long t2 = System.nanoTime();
        System.out.println("TOTAL TIME: " + Config.sec2TStamp((int) (t2 - t1) / 1000000000));
    }
}