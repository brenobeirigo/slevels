package simulation;

import config.Config;
import dao.Dao;
import helper.HelperIO;
import helper.MethodHelper;
import model.User;
import model.Vehicle;
import model.node.NodeOrigin;
import model.node.NodeStop;

import java.util.*;

public abstract class Simulation {

    // Mark getServicedUsers execution time
    protected long t1;
    protected Solution sol; //Simulation solution

    /* TIME HORIZON */
    protected int timeHorizon; // Size of time bins
    protected int totalHorizon; // Total time horizon

    /* VEHICLE INFO */
    protected int nOfVehicles; // Fleet size
    protected int vehicleCapacity; // Number of seats

    /* POOLING DATA */
    protected int maxNumberOfTrips; //How many trips are pooled in time horizon
    protected int totalRounds; // How many rounds of time horizon will be pooled
    protected int time_slot;
    protected int start_timestamp; // (00:00:00) Initial timestamp for pooling data
    protected int leftTW, rightTW; // Left and right time windows (rightTW = current time)
    protected boolean run_ending_rounds; // Keep running rounds until all vehicles finish the requests
    protected int countRounds;

    /* SETS OF VEHICLES AND REQUESTS */
    protected Map<Integer, User> allRequests; // Dictionary of all users
    protected Set<User> setWaitingUsers; // Requests whose pickup time is lower than the current time
    protected Set<User> deniedRequests; // Requests with expired pickup time
    protected Set<User> finishedRequests; // Requests whose DP node was visited
    protected List<Vehicle> listVehicles; // List of vehicles
    //TODO hot_PK_list


    public Simulation() {

        /* TIME HORIZON */
        timeHorizon = 30; // Size of time bins
        totalHorizon = 3600; // Total time horizon

        /* VEHICLE INFO */
        nOfVehicles = 2000; // Fleet size
        vehicleCapacity = 10; // Number of seats (1 - 4)

        /* POOLING DATA */
        maxNumberOfTrips = 300; //How many trips are pooled in time horizon
        totalRounds = totalHorizon / timeHorizon; // How many rounds of time horizon will be pooled
        time_slot = totalRounds * timeHorizon;
        start_timestamp = 0; // (00:00:00) Initial timestamp for pooling data
        leftTW = this.start_timestamp; // Left and right time windows (rightTW = current time)
        rightTW = 0;
        run_ending_rounds = true; // Keep running rounds until all vehicles finish the requests
        countRounds = 0;

        /* SETS OF VEHICLES AND REQUESTS */
        allRequests = new HashMap<>(); // Dictionary of all users
        setWaitingUsers = new HashSet<>(); // Requests whose pickup time is lower than the current time
        deniedRequests = new HashSet<>(); // Requests with expired pickup time
        finishedRequests = new HashSet<>(); // Requests whose DP node was visited
        listVehicles = MethodHelper.createListVehicles(nOfVehicles, vehicleCapacity, true); // List of vehicles
        //TODO hot_PK_list

        // Mark getServicedUsers execution time
        t1 = System.nanoTime();

        // Initialize solution
    }

    //TODO hot_PK_list

    public abstract Set<User> getServicedUsers(int currentTime);

    public void run() {
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

            // Wall time getServicedUsers of round
            long startWalltime = System.nanoTime();

            // Update current time
            rightTW = leftTW + timeHorizon;

            // Update previous current time
            leftTW = rightTW;


            /*#*******************************************************************************************************/
            ////// 1 - GET FINISHED USERS (before current time) ////////////////////////////////////////////////////////
            /*#*******************************************************************************************************/

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
                if (!v.getUsers().isEmpty()) {

                    // Update the number of remaining passengers
                    remainingPassengers = remainingPassengers + v.getUsers().size();

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

                if (v.getCurrentNode() instanceof NodeStop || v.getCurrentNode() instanceof NodeOrigin) {
                    if (v.getUsers().isEmpty()) {
                        v.setDepartureCurrent(Math.max(rightTW, v.getDepartureCurrent()));
                    }
                }
            }

            /*#*******************************************************************************************************/
            ////// 2 - Eliminate waiting users that can no longer be picked up /////////////////////////////////////////
            /*#*******************************************************************************************************/

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

            /*#*******************************************************************************************************/
            ////// 3 - GET USERS INSIDE TW /////////////////////////////////////////////////////////////////////////////
            /*#*******************************************************************************************************/

            // List of pooled users inside TW (only filled if countRounds < totalRounds)
            List<User> listUsersTW = new ArrayList<>();

            // After the number of rounds stop pooling requests but finish waiting requests
            if (countRounds < totalRounds) {

                // Dictionary of pooled requests inside time slot
                listUsersTW = Dao.getInstance().getListTrips(timeHorizon, vehicleCapacity, maxNumberOfTrips);

                // Add pooled requests into waiting list
                setWaitingUsers.addAll(listUsersTW);

                // Store all requests
                for (User e : listUsersTW) {
                    allRequests.put(e.getId(), e);
                }
            }

            /*#*********************************************************************************************************
             ////// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES ///////////////////////////////////
            /*#********************************************************************************************************/

            //##########################################################################################################
            //##########################################################################################################
            Set<User> setScheduledUsers = getServicedUsers(rightTW);
            //##########################################################################################################
            //##########################################################################################################

            System.out.println("Scheduled in round:" + setScheduledUsers.size() + "/" + setWaitingUsers.size());
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

            /*#*******************************************************************************************************
             ///// Print round information  ///////////////////////////////////////////////////////////////////////////
            /*#********************************************************************************************************/

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
                    setWaitingUsers,
                    finishedRequests,
                    deniedRequests,
                    listUsersTW,
                    allRequests,
                    (System.nanoTime() - startWalltime) / 1000000));


            // Print vehicle details
            System.out.println(HelperIO.getVehicleInfo(listVehicles,
                    rightTW,
                    false,
                    false,
                    false));
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