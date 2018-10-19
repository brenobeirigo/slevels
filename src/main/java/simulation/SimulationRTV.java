package simulation;

import config.Config;
import dao.Dao;
import helper.HelperIO;
import helper.MethodHelper;
import model.User;
import model.Vehicle;
import model.Visit;
import model.graph.Graph;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;

import java.util.*;

public class SimulationRTV {

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
    private Set<User> setWaitingUsers; // Requests whose pickup time is lower than the current time
    private Set<User> deniedRequests; // Requests with expired pickup time
    private Set<User> finishedRequests; // Requests whose DP node was visited
    private List<Vehicle> listVehicles; // List of vehicles
    //TODO hot_PK_list


    public SimulationRTV() {


        /* TIME HORIZON */
        timeHorizon = 30; // Size of time bins
        total_horizon = 24 * 3600; // Total time horizon

        /* VEHICLE INFO */
        nOfVehicles = 1000; // Fleet size
        vehicleCapacity = 3; // Number of seats (1 - 4)

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
        setWaitingUsers = new HashSet<>(); // Requests whose pickup time is lower than the current time
        deniedRequests = new HashSet<>(); // Requests with expired pickup time
        finishedRequests = new HashSet<>(); // Requests whose DP node was visited
        listVehicles = MethodHelper.createListVehicles(nOfVehicles, vehicleCapacity, true); // List of vehicles
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
                listUsersTW = Dao.getInstance().getListTrips(timeHorizon, maxNumberOfTrips);

                // Add pooled requests into waiting list
                setWaitingUsers.addAll(listUsersTW);

                // Store all requests
                for (User e : listUsersTW) {
                    allRequests.put(e.getId(), e);
                }
            }

            Map<Integer, List<Integer>> graphRV = getRVGraph(setWaitingUsers, listVehicles);

            //Graph RTV = getRTV(graphRV,setWaitingUsers,listVehicles);

            Map<Integer, TreeSet<Visit>> visitsVehicleCapacity = getRTV(graphRV, setWaitingUsers, listVehicles);
            //System.out.println("RV GRAPH:");
            //System.out.println(graphRV);
            //System.out.println(RTV);
            /*
            Graph<Integer, DefaultEdge> G = set_rv_edges(setWaitingUsers, listVehicles);
            BronKerboschCliqueFinder a = new BronKerboschCliqueFinder(G);
            System.out.println("Cliques:");
            a.forEach(click -> System.out.println(click));
            */

            /*#*********************************************************************************************************
             ////// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES ///////////////////////////////////
            /*#********************************************************************************************************/

            // FIRST COME FIRST SERVE
            Set<User> setScheduledUsers = greedyAssignment(visitsVehicleCapacity);

            //System.out.println("VISITED:"+visitsVehicleCapacity);
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
             */

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

    /**
     * Pairwise graph of vehicles and requests. Combine vehicles and requests.
     *
     * @param setWaitingUsers
     * @param listVehicles
     * @return
     */
    public Map<Integer, List<Integer>> getRVGraph(Set<User> setWaitingUsers, List<Vehicle> listVehicles) {

        User[] listRequests = setWaitingUsers.toArray(new User[0]);

        // Pairwise graph of vehicles and requests (adjacent matrix)
        Map<Integer, List<Integer>> rv = new HashMap<>();

        //Graph G = new Graph();

        // A request r and a vehicle v are connected if(the request
        // can be served by the vehicle while satisfying the
        // constraints Z, as given by travel(v, r).

        //G.addVertex(null);

        // Loop requests
        for (User r : listRequests) {

            // Initialize adjacent list of all requests
            rv.put(r.getId(), new ArrayList<>());

            // -1 represents no assignment
            rv.get(r.getId()).add(-1);
        }

        // Loop vehicles
        for (Vehicle v : listVehicles) {

            // Initialize adjacent matrix of vehicles
            rv.put(v.getId(), new ArrayList<>());

            // Check if vehicle can visit request
            for (User r : listRequests) {

                //TODO: maybe the load of the vehicle does need to be considered
                // if request does not fit vehicle
                if (r.getNumPassengers() + v.getLoad() > v.getCapacity()) {
                    continue;
                }

                // Find the best visit departing from vehicle
                // current step

                //TODO: current time in vehicle current node should be >= experiment current node
                // if best visit is found, there is and edge connecting vehicle v to request r
                if (Method.feasibleSequence(new Node[]{v.getCurrentNode(), r.getNodePk()})) {
                    //System.out.println("Adding"+ v, r, " - Best visit:"+ best_visit)
                    //G.addEdge(v.getId(),r.getId());
                    rv.get(v.getId()).add(r.getId());
                }
            }
        }

        int countPairwise = 0;

        Set<String> validPair = new HashSet<>();
        int countImpossible = 0;

        for (int i = 0; i < listRequests.length - 1; i++) {
            Node pk1 = listRequests[i].getNodePk();
            Node dp1 = listRequests[i].getNodeDp();
            for (int j = i + 1; j < listRequests.length; j++) {
                Node pk2 = listRequests[j].getNodePk();
                Node dp2 = listRequests[j].getNodeDp();

                if (pk1.getEarliest() >= dp2.getLatest()) {
                    countImpossible++;
                    continue;
                }
                if (pk2.getEarliest() >= dp1.getLatest()) {
                    countImpossible++;
                    continue;
                }

                //TODO parallelize checks
                // There are 4 ways of combining pks and dps (considering ridesharing), i.e.:
                // pk1-dp1-pk2-dp2 and pk2-dp2-pk1-dp1 are excluded
                Node[] seq1 = new Node[]{pk1, pk2, dp1, dp2};
                Node[] seq2 = new Node[]{pk1, pk2, dp2, dp1};
                Node[] seq3 = new Node[]{pk2, pk1, dp1, dp2};
                Node[] seq4 = new Node[]{pk2, pk1, dp2, dp1};
                if (Method.feasibleSequence(seq1) ||
                        Method.feasibleSequence(seq2) ||
                        Method.feasibleSequence(seq3) ||
                        Method.feasibleSequence(seq4)) {

                    // At least one sequence is feasible
                    rv.get(listRequests[i].getId()).add(listRequests[j].getId());

                    countPairwise++;
                }
            }
        }
        return rv;
    }

    /**
     * The request-trip-vehicle RTV-graph contains edges "e(r, T)", between a request "r" and a trip "T" and feasible
     * edges "e(T, v)", between a trip "T" and a vehicle "v". Namely,
     * <p>
     * ∃ e(r, T) ⇔ r ∈ T
     * ∃ e(T, v) ⇔ travel(v, T) = "valid"
     * <p>
     * Lemma 1 (Cliques). A trip "T" can be feasible only if a clique in the RV-graph exists for all requests in "T" and
     * some vehicle "v". Namely, if "T" is valid, then,
     * <p>
     * ∃ v ∈ V such that ∀ r1, r2 ∈ T, e(r1, r2) and e(r1, v) exist
     * <p>
     * Lemma 2 (Sub-feasibility). A trip "T" can be feasible only if there exists a vehicle "v" for which, for all
     * "r ∈ T", the sub-trips "T' = T\r" are feasible (a sub-trip " T' " contains all the requests ofT but one). Namely,
     * <p>
     * T feasible ⇒ ∃ v ∈ V such that ∀ r ∈ T, e(T \ r, v) exists.
     * <p>
     * Therefore, a trip T only needs to by checked for existence if there exists a vehicle v for which all of its
     * sub-trips T' present an edge e(T', v) in the RTV-graph.
     *
     * @param rv
     * @param list_requests
     * @param list_vehicles
     * @return
     */
    public Map<Integer, TreeSet<Visit>> getRTV(Map<Integer, List<Integer>> rv,
                                               Set<User> list_requests,
                                               List<Vehicle> list_vehicles) {


        Graph RTV = new Graph("RTV");

        // < Vehicle size, Visit> -> Vehicle sizes are levels
        Map<Integer, TreeSet<Visit>> visitsVehicleCapacity = new HashMap<>();

        // Start lists for all levels within vehicle v
        for (int i = 1; i <= this.vehicleCapacity; i++) {
            visitsVehicleCapacity.put(i, new TreeSet<>());
        }

        // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
        // &&&&&&& ADD TRIPS OF SIZE 1 &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
        // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
        for (Vehicle v : list_vehicles) {

            /*
            TODO: Implement timeout
            In the computation of the RTV-graph we specify a maximum amount of time, per vehicle, to explore potential
            trips and add edges to the graph. In particular, we used a timeout of 0.2 seconds per vehicle.
             */

            //TODO only use locked requests
            // Only requests already visited must stay in vehicle
            //v.getListUsers().clear();
            //v.getListUsers().addAll(v.getEnroute());
            //v.getVisit().get

            // Trip levels inside vehicle v. E.g.: 1 -> {[r1], [r2]}, 2 -> {[r1, r2], [r2, r3]}
            Map<Integer, List<Set<User>>> tripsVehicleLevel = new HashMap<>();

            // Start lists for all levels within vehicle v
            for (int i = 1; i <= v.getCapacity(); i++) {
                tripsVehicleLevel.put(i, new ArrayList<>());
            }

            // Loop trips of size 1 in RV
            for (int r : rv.get(v.getId())) {

                // Unitary set for level 1
                Set<User> trip1 = new HashSet<>();

                // Add request at trip1
                trip1.add(allRequests.get(r));

                //######################################################################################################
                // If there is a valid visit:
                // Add edge:
                // v---trip1, trip1--r
                Visit visit = RTV.addEdge(v, trip1);
                if (visit != null) {
                    tripsVehicleLevel.get(1).add(trip1);
                    visitsVehicleCapacity.get(1).add(visit);
                    visit.setSetUsers(trip1);
                }
                //######################################################################################################
            }
            // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            // &&&&&& ADD TRIPS OF SIZE 2 &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&

            // Combine requests of level 1
            for (int i = 0; i < tripsVehicleLevel.get(1).size() - 1; i++) {

                // Unitary set with a request r1
                Set<User> r1 = tripsVehicleLevel.get(1).get(i);

                for (int j = i + 1; j < tripsVehicleLevel.get(1).size(); j++) {

                    // Unitary set with a request r2
                    Set<User> r2 = tripsVehicleLevel.get(1).get(j);

                    // Set of size 2 [r1, r2]
                    Set<User> trip2 = new HashSet<>();
                    trip2.addAll(r1);
                    trip2.addAll(r2);

                    //##################################################################################################
                    // If there is a valid visit:
                    // Add edges:
                    // v---trip2, trip2--r1, trip2--r2
                    Visit visit = RTV.addEdge(v, trip2);

                    if (visit != null) {
                        tripsVehicleLevel.get(2).add(trip2);
                        visitsVehicleCapacity.get(2).add(visit);
                        visit.setSetUsers(trip2);
                    }
                    //##################################################################################################
                }
            }

            // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            // &&&&&&&&&&& ADD TRIPS OF SIZE K >= 3 &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            for (int k = 3; k <= v.getCapacity(); k++) {

                // If there is no previous level, stop execution
                if (tripsVehicleLevel.get(k - 1).isEmpty()) {
                    break;
                }

                // Loop trips at level k-2
                for (int i = 0; i < tripsVehicleLevel.get(k - 2).size() - 1; i++) {

                    // Trip at level (k-2)
                    Set<User> tripI = tripsVehicleLevel.get(k - 2).get(i);

                    // Loop trips at level k-1
                    nextTripJ:
                    for (int j = i + 1; j < tripsVehicleLevel.get(k - 1).size(); j++) {

                        // Trip at level (k-1)
                        Set<User> tripJ = tripsVehicleLevel.get(k - 1).get(j);

                        // Crete trip of size k (combining k-1 and k-2)
                        Set<User> tripK = new HashSet<>(tripI);
                        tripK.addAll(tripJ);

                        /* Filter trips of size < k. E.g.:
                         - [r1]+[r1,r2] = [r1,r2] = 2 NOT VALID
                         - [r1]+[r2,r3] = [r1,r2,r3] = 3 */
                        if (tripK.size() < k) {
                            continue;
                        }

                        //System.out.println(k+" -- "+tripK);
                        //System.out.println("Trips in "+(k-1)+" - "+ trips.get(k-1));

                        /* A trip T only needs to by checked for existence if there exists a vehicle v for which all
                         of its sub-trips T' present an edge e(T', v) in the RTV-graph.                           */
                        for (User removeReq : tripK) {

                            // Create sub-trip (without "removeReq")
                            Set<User> sub = new HashSet<>(tripK);
                            sub.remove(removeReq);

                            // If sub-trip not in precedent level
                            if (!tripsVehicleLevel.get(k - 1).contains(sub)) {
                                // Invalid, go to next trip j
                                continue nextTripJ;
                            }
                        }

                        //##########################################################################################
                        // If there is a valid visit:
                        // Add edges:
                        // v---tripK, tripK--r1, tripK--r2, ..., tripK--rk
                        Visit visit = RTV.addEdge(v, tripK);

                        // If found valid visit
                        if (visit != null) {
                            // Add trip at level k
                            tripsVehicleLevel.get(k).add(tripK);
                            visitsVehicleCapacity.get(k).add(visit);
                            visit.setSetUsers(tripK);
                        }
                        //##########################################################################################
                    }
                }
            }
            //System.out.println(v+">>Trips:");
            //System.out.println(trips);
            //trips.forEach((level,setAtLevel)-> setAtLevel.forEach(trip->System.out.println(level+"-"+trip)));
        }

        //System.out.println(">>Visits per level:");
        //visitsVehicleCapacity.forEach((level,setAtLevel)-> setAtLevel.forEach(visit->System.out.println(level+"-"+visit)));


        return visitsVehicleCapacity;
    }

    /**
     * Greedy assign of users to vehicles (preference to larger trips)
     * Starting from the largest vehicle capacity, tries to realize candidate visits.
     * Repeats the process until capacity 1 is reached and all visits have been evaluted.
     *
     * @param visitsVehicleCapacity Map of ordered visits associated to a trip length (<=max. vehicle capacity)
     * @return Set of users assigned
     */
    public Set<User> greedyAssignment(Map<Integer, TreeSet<Visit>> visitsVehicleCapacity) {

        // Set of requests scheduled to vehicles
        Set<User> requestOk = new HashSet<>();

        // Set of vehicles assigned to visits
        Set<Vehicle> vehicleOk = new HashSet<>();

        // Set of visits chosen in round
        Set<Visit> greedy = new HashSet<>();

        // Loop visits starting from the longest (combining more requests)
        for (int k = this.vehicleCapacity; k >= 1; k--) {

            // Set of ordered visits (shortest delay) in level k
            TreeSet<Visit> visitsLevelK = visitsVehicleCapacity.get(k);

            // If there are visits in level k
            if (visitsLevelK != null) {

                // Loop all visits
                nextVisit:
                while (!visitsLevelK.isEmpty()) {

                    // Get current best candidate visit
                    Visit candidateVisit = visitsLevelK.pollFirst();

                    // Get vehicle associated to visit
                    Vehicle vehicle = candidateVisit.getVehicle();

                    // Jump to next visit if a visit was already assigned to a vehicle
                    if (vehicleOk.contains(vehicle)) {
                        continue;
                    }

                    // Get requests associated to visit
                    Set<User> requests = candidateVisit.getSetUsers();

                    //System.out.println("Candidates: "+ requests + " -- "+ vehicle);

                    // Jump to next visit if any request in candidate visit was already assigned

                    for (User r : requests) {
                        if (requestOk.contains(r)) {
                            continue nextVisit;
                        }
                    }

                    // System.out.println("Assigning...");
                    // Update requests, vehicles, and greedy solution
                    requestOk.addAll(requests);
                    vehicleOk.add(vehicle);
                    greedy.add(candidateVisit);

                    // ######## Materialize visit
                    // Add visit to vehicle (circular)
                    candidateVisit.getVehicle().setVisit(candidateVisit);

                    // User u belongs to vehicle
                    candidateVisit.getVehicle().getListUsers().addAll(requests);
                }
            }
        }

        return requestOk;
    }

    public void initRTV() {
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
                listUsersTW = Dao.getInstance().getListTrips(timeHorizon, maxNumberOfTrips);

                // Add pooled requests into waiting list
                setWaitingUsers.addAll(listUsersTW);

                // Store all requests
                for (User e : listUsersTW) {
                    allRequests.put(e.getId(), e);
                }
            }


            int countPairwise = 0;
            User[] reqs = setWaitingUsers.toArray(new User[0]);
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


            /*#*******************************************************************************************************
             ////// 3 - ASSIGN WAITING USERS (previous + current round)  TO VEHICLES ///////////////////////////////////
             */

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

            /*#*******************************************************************************************************
             ///// Print round information  ///////////////////////////////////////////////////////////////////////////
             */

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