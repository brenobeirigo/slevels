package simulation;

import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;

import java.util.*;

public class SimulationRTV extends Simulation {


    /* RV, RTV */
    private int maxVehReqEdges;
    private int maxReqReqEdges;
    private int maxEdgesRTV;
    private int numberUsersPermute;
    private boolean findBestVisit;
    private int maxNumberPermutations;

    //TODO hot_PK_list

    public SimulationRTV(int initialFleet,
                         int vehicleMaxCapacity,
                         int maxRequestsIteration,
                         int timeWindow,
                         int timeHorizon,
                         String serviceRateScenarioLabel,
                         String segmentationScenarioLabel) {

        // Build generic Simulation object
        super(initialFleet,
                vehicleMaxCapacity,
                maxRequestsIteration,
                timeWindow,
                timeHorizon,
                10,
                2, 2, false,
                false);

        // Service rate and segmentation scenarios
        this.serviceRateScenarioLabel = serviceRateScenarioLabel;
        this.segmentationScenarioLabel = segmentationScenarioLabel;

        /* RV, RTV */
        maxVehReqEdges = 1000;
        maxReqReqEdges = 1000;
        maxEdgesRTV = 1000;

        /* Permutations */
        numberUsersPermute = 2;
        findBestVisit = false;
        maxNumberPermutations = 100;

        // Initialize solution
        sol = new Solution("RTV", initialFleetSize, maxNumberOfTrips, vehicleCapacity, timeWindow, timeHorizon, 10, 2, 2, false, false);
    }

    public void updateRR(User[] listRequests,
                         Map<Integer, List<Integer>> rv,
                         int maxNumberOfEdges) {

        // Loop requests
        for (User r : listRequests) {

            // Initialize adjacent list of all requests
            rv.put(r.getId(), new ArrayList<>());

            // -1 represents no assignment
            //rv.get(r.getId()).add(-1);
        }

        for (int i = 0; i < listRequests.length - 1; i++) {

            // Request r1 data
            User r1 = listRequests[i];
            Node pk1 = r1.getNodePk();
            Node dp1 = r1.getNodeDp();

            for (int j = i + 1; j < listRequests.length; j++) {

                // Request r2 data
                User r2 = listRequests[j];
                Node pk2 = listRequests[j].getNodePk();
                Node dp2 = listRequests[j].getNodeDp();

                if (pk1.getEarliest() >= dp2.getLatest()) {
                    continue;
                }
                if (pk2.getEarliest() >= dp1.getLatest()) {
                    continue;
                }

                //TODO parallelize checks
                // There are 4 ways of combining pks and dps (considering ridesharing), i.e.:
                // pk1-dp1-pk2-dp2 and pk2-dp2-pk1-dp1 are excluded


                /*
                // Choose any sequence

                Node[] seq1 = new Node[]{pk1, pk2, dp1, dp2};
                int d1 = Method.getDelayFrom(seq1);

                Node[] seq2 = new Node[]{pk1, pk2, dp2, dp1};
                int d2 = Method.getDelayFrom(seq2);

                Node[] seq3 = new Node[]{pk2, pk1, dp1, dp2};
                int d3 = Method.getDelayFrom(seq3);

                Node[] seq4 = new Node[]{pk2, pk1, dp2, dp1};
                int d4 = Method.getDelayFrom(seq4);


                rv.get(listRequests[i].getId()).add(listRequests[j].getId());
                */

                Node[] seq1 = new Node[]{pk1, pk2, dp1, dp2};
                Node[] seq2 = new Node[]{pk1, pk2, dp2, dp1};
                Node[] seq3 = new Node[]{pk2, pk1, dp1, dp2};
                Node[] seq4 = new Node[]{pk2, pk1, dp2, dp1};

                if (Method.feasibleSequence(seq1) ||
                        Method.feasibleSequence(seq2) ||
                        Method.feasibleSequence(seq3) ||
                        Method.feasibleSequence(seq4)) {

                    //TODO undirected edge r1-r2
                    // At least one sequence is feasible
                    rv.get(r1.getId()).add(r2.getId());

                    // At least one sequence is feasible
                    rv.get(r2.getId()).add(r1.getId());


                    // Maximum number of connections is reached
                    if (rv.get(r1.getId()).size() >= maxNumberOfEdges) {
                        break;
                    }

                }

            }
        }
    }

    /**
     * In RV graph, create edges connecting vehicles to requests ( <= maxNumberOfEdges)
     *
     * @param listVehicles     List of all vehicles
     * @param listRequests     List of all requests
     * @param rv               Request vehicle graph
     * @param maxNumberOfEdges Max number of vehicles that can serve an user
     */
    public void updateRV(List<Vehicle> listVehicles,
                         User[] listRequests,
                         Map<Integer, List<Integer>> rv,
                         int maxNumberOfEdges) {

        // Loop vehicles
        for (Vehicle v : listVehicles) {

            // Initialize adjacent matrix of vehicles
            rv.put(v.getId(), new ArrayList<>());
        }

        // Check if vehicle can visit request
        for (User r : listRequests) {

            // Count of edges connecting requests to candidate vehicles
            int edgesRV = 0;

            // Loop vehicles
            for (Vehicle v : listVehicles) {

                //TODO: maybe the load of the vehicle does need to be considered
                // if request does not fit vehicle
                if (r.getNumPassengers() + v.getLoad() > v.getCapacity()) {
                    continue;
                }

                // Find the best visit departing from vehicle current step
                //TODO: current time in vehicle current node should be >= experiment current node
                // if best visit is found, there is and edge connecting vehicle v to request r
                if (Method.feasibleSequence(new Node[]{v.getCurrentNode(), r.getNodePk()})) {
                    // System.out.println("Adding"+ v, r, " - Best visit:"+ best_visit)
                    // G.addEdge(v.getId(),r.getId());
                    rv.get(v.getId()).add(r.getId());

                    // Stop connecting requests to vehicles if edge count was reached
                    if (++edgesRV >= maxNumberOfEdges) {

                        break;
                    }
                }
            }
        }
    }

    /**
     * Pairwise graph of vehicles and requests. Combine vehicles and requests.
     *
     * @param setWaitingUsers
     * @param listVehicles
     * @return
     */
    public Map<Integer, List<Integer>> getRVGraph(List<User> setWaitingUsers,
                                                  List<Vehicle> listVehicles,
                                                  int maxVehReqEdges,
                                                  int maxReqReqEdges) {

        // Convert set of waiting users into list
        User[] listRequests = setWaitingUsers.toArray(new User[0]);

        // Pairwise graph of vehicles and requests (adjacent matrix)
        Map<Integer, List<Integer>> rv = new HashMap<>();

        //Graph G = new Graph();
        //G.addVertex(null);

        // Add request-request edges
        updateRR(listRequests, rv, maxReqReqEdges);

        // A request r and a vehicle v are connected if the request can be served by the vehicle while satisfying the
        // constraints Z, as given by travel(v, r). Every vehicle is conected to "maxNumberOfEdges" users.
        updateRV(listVehicles, listRequests, rv, maxVehReqEdges);

        /*
        System.out.println("PRINT RV:");
        for (int i = 0; i < listRequests.length - 1; i++) {
            System.out.println(listRequests[i].getId()+" - "+rv.get(listRequests[i].getId()));
        }
        */

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
     * @param list_vehicles
     * @return
     */
    public Map<Integer, TreeSet<Visit>> getRTV(Map<Integer, List<Integer>> rv,
                                               List<Vehicle> list_vehicles) {


        //Graph RTV = new Graph("RTV");

        // < Vehicle size, Visit> -> Vehicle sizes are levels
        Map<Integer, TreeSet<Visit>> visitsVehicleCapacity = new HashMap<>();

        // Start lists for all levels within vehicle v
        for (int i = 1; i <= this.vehicleCapacity; i++) {
            visitsVehicleCapacity.put(i, new TreeSet<>());
        }

        for (Vehicle v : list_vehicles) {

            /*
            TODO: Implement timeout
            In the computation of the RTV-graph we specify a maximum amount of time, per vehicle, to explore potential
            trips and add edges to the graph. In particular, we used a timeout of 0.2 seconds per vehicle.
             */

            //TODO only use locked requests
            // Only requests already visited must stay in vehicle
            //v.getUsers().clear();
            //v.getUsers().addAll(v.getEnroute());
            //v.getVisitByPermutation().get

            // Trip levels inside vehicle v. E.g.: 1 -> {[r1], [r2]}, 2 -> {[r1, r2], [r2, r3]}
            Map<Integer, List<Set<User>>> tripsVehicleLevel = new HashMap<>();

            // Start lists for all levels within vehicle v
            for (int i = 1; i <= v.getCapacity(); i++) {
                tripsVehicleLevel.put(i, new ArrayList<>());
            }

            // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            // &&&&&&& ADD TRIPS OF SIZE 1 &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            for (int r : rv.get(v.getId())) {

                // Unitary set for level 1
                Set<User> trip1 = new HashSet<>();

                // Add request at trip1
                trip1.add(allRequests.get(r));

                //######################################################################################################
                // If there is a valid visit:
                // Add edge:
                // v---trip1, trip1--r
                //Visit visit = RTV.addEdge(v, trip1);

                // Try to find a valid visit for trip (100 permutations tested)
                //Visit visit = Method.getVisitByPermutation(trip1, v, false, 100);

                // Get best insertion for trip with 1 users
                Visit visit = Method.getBestInsertionNoAddFirst(trip1, v, rightTW, numberUsersPermute, findBestVisit, maxNumberPermutations);

                // System.out.println("VISIT: " + trip1 +" ["+visit+"]");
                if (visit != null) {

                    // TODO: Filling visit with users (has to change after changing enroute vehicles)
                    // Update set of users in visit
                    visit.setSetUsers(trip1);
                    //visit.getSetUsers().addAll(v.getUsers());

                    // Add trip at level 1
                    tripsVehicleLevel.get(1).add(trip1);

                    // Add visit to the capacity level 1
                    visitsVehicleCapacity.get(1).add(visit);

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
                    //Visit visit = RTV.addEdge(v, trip2);
                    //TODO Rethink RTV use

                    // Try to find a valid visit for trip (100 permutations tested)
                    //Visit visit = Method.getVisitByPermutation(trip2, v, false, 100);


                    // Get best insertion for trip with K users
                    Visit visit = Method.getBestInsertionNoAddFirst(trip2, v, rightTW, numberUsersPermute, findBestVisit, maxNumberPermutations);

                    //System.out.println(trip2+ " = "+ visit);

                    if (visit != null) {

                        //System.out.println(trip2+ " = "+ visit);

                        // Update set of users in visit
                        visit.setSetUsers(trip2);

                        // Add trip at level 2
                        tripsVehicleLevel.get(2).add(trip2);

                        // Add visit to the capacity level 2
                        visitsVehicleCapacity.get(2).add(visit);

                    }
                    //##################################################################################################
                }
            }

            //System.out.println("LEVEL 2");
            //System.out.println(visitsVehicleCapacity.get(2));

            // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            // &&&&&&&&&&& ADD TRIPS OF SIZE K >= 3 &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            // &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&
            for (int k = 3; k <= v.getCapacity(); k++) {

                // Keep unique trips for level k. For example, trip [1,2,3] can be a result of:
                // [1,2] +[2,3] = [1,2,3]
                // [3,1] +[1,2] = [1,2,3]
                // [1,3] +[3,2] = [1,2,3]
                // Notice some might be invalid (visit was not found)
                Set<Set<User>> uniqueTripsK = new HashSet<>();

                // If there is no previous level, stop execution
                if (tripsVehicleLevel.get(k - 1).isEmpty()) {
                    break;
                }

                //int sizePrecedentLevel = tripsVehicleLevel.get(k - 1).size();
                //System.out.println("Testing "+ (sizePrecedentLevel * (sizePrecedentLevel-1)));

                maxNumberLevel:
                // Get all (i,j) trip pairs from precedent level
                for (int i = 0; i < tripsVehicleLevel.get(k - 1).size() - 1; i++) {

                    // Trip at level (k-2)
                    Set<User> tripI = tripsVehicleLevel.get(k - 1).get(i);

                    // Loop trips at level k-2
                    nextTripJ:
                    for (int j = i + 1; j < tripsVehicleLevel.get(k - 1).size(); j++) {

                        // Max. number of trips in level k
                        if (uniqueTripsK.size() > maxEdgesRTV) {
                            break maxNumberLevel;
                        }

                        // Trip at level (k-2)
                        Set<User> tripJ = tripsVehicleLevel.get(k - 1).get(j);

                        // Crete trip of size k (combining k-1 and k-2)
                        Set<User> tripK = new HashSet<>(tripI);
                        tripK.addAll(tripJ);

                        // System.out.println(String.format("%s + %s = %s", tripI, tripJ, tripK));

                        /* Filter trips of size < k. E.g. for k = 3:
                         - [r1,r3]+[r1,r2] = [r1,r2,r3] OK! (size=3)
                         - [r1,r4]+[r2,r3] = [r1,r2,r3,r4] FAIL (size=4) */
                        if (tripK.size() != k) {
                            continue;
                        }

                        //System.out.println("TRIP K:" + tripK);

                        //System.out.println(String.format("%s + %s = %s", tripI, tripJ, tripK));

                        // TripK was already checked before
                        if (!uniqueTripsK.add(tripK)) {
                            continue;
                        }

                        // System.out.println(String.format("%s + %s = %s", tripI, tripJ, tripK));

                        //System.out.println(k+" -- "+tripK);
                        //System.out.println("Trips in "+(k-1)+" - "+ trips.get(k-1));

                        /* A trip T only needs to by checked for existence if there exists a vehicle v for which all
                         of its sub-trips T' present an edge e(T', v) in the RTV-graph.                           */
                        for (User removeReq : tripK) {

                            // Create sub-trip (without "removeReq")
                            Set<User> sub = new HashSet<>(tripK);
                            sub.remove(removeReq);

                            // System.out.println(tripsVehicleLevel + " -- " + sub);

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
                        // Visit visit = RTV.addEdge(v, tripK);
                        // Try to find a valid visit for trip (100 permutations tested)
                        //Visit visit = Method.getVisitByPermutation(tripK, v, false, 100);

                        // Get best insertion for trip with K users
                        Visit visit = Method.getBestInsertionNoAddFirst(tripK, v, rightTW, numberUsersPermute, findBestVisit, maxNumberPermutations);

                        //System.out.println("VISITSK:");
                        //System.out.println(visit);
                        //System.out.println(visit2);

                        //System.out.println(tripK + " = " + visit);

                        // If found valid visit
                        if (visit != null) {

                            //System.out.println(visit);

                            // Update set of users in visit (tripK + vehicle Users)
                            visit.setSetUsers(tripK);
                            //visit.getSetUsers().addAll(v.getUsers());

                            // Add trip at level k
                            tripsVehicleLevel.get(k).add(tripK);

                            // Add visit to the capacity level k
                            visitsVehicleCapacity.get(k).add(visit);

                        }
                        //##########################################################################################
                    }
                }
            }

            //System.out.println(v+">>Trips:");
            //System.out.println(tripsVehicleLevel);
            //tripsVehicleLevel.forEach((level,setAtLevel)-> setAtLevel.forEach(trip->System.out.println(level+"-"+trip)));
        }

        System.out.println(">>Visits per level:");
        visitsVehicleCapacity.forEach((level, setAtLevel) -> System.out.println(level + "-" + setAtLevel.size()));
        //visitsVehicleCapacity.forEach((level,setAtLevel)-> setAtLevel.forEach(visit->System.out.println(level+"-"+visit+" - " + visit.getVehicle())));

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
        //System.out.println("GREEDY");

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

            //System.out.println(k+" - "+visitsLevelK);

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

                    // Jump to next visit if any request in candidate visit was already assigned
                    for (User r : requests) {
                        if (requestOk.contains(r)) {
                            continue nextVisit;
                        }
                    }

                    // Update requests, vehicles, and greedy solution
                    requestOk.addAll(requests);
                    vehicleOk.add(vehicle);
                    greedy.add(candidateVisit);

                    // #################################################################################################
                    // ######## Materialize visit ######################################################################
                    // #################################################################################################

                    candidateVisit.setup();
                }
            }
        }
        return requestOk;
    }

    /**
     * Method: On-demand high-capacity ride-sharing via dynamic trip-vehicle assignment
     * Authors: Javier Alonso-Mora,
     * Samitha Samaranayake,
     * Alex Wallar,
     * Emilio Frazzoli,
     * and Daniela Rus
     *
     * @return Scheduled users
     */
    public Set<User> getServicedUsersDynamicSizedFleet(int currentTime) {

        // Create request-vehicle (RV) structure
        Map<Integer, List<Integer>> graphRV = getRVGraph(setWaitingUsers, listVehicles, maxVehReqEdges, maxReqReqEdges);

        /*
        System.out.println("RV GRAPH:");
        for(Map.Entry<Integer, List<Integer>> e:graphRV.entrySet()){
            System.out.println("#" + e.getKey() + ": " + e.getValue()  );
        }
        */

        // Create request-trip-vehicle (RTV) structure
        Map<Integer, TreeSet<Visit>> visitsVehicleCapacity = getRTV(graphRV, listVehicles);


        System.out.println(">>>>>>>>>>>>>>> RTV GRAPH:");
        for (Map.Entry<Integer, TreeSet<Visit>> e : visitsVehicleCapacity.entrySet()) {
            System.out.println("#" + e.getKey() + ":" + e.getValue().size());
            //for (Visit v:e.getValue()) {
            //    System.out.println(v);
            //}
        }

        // Assign users to vehicles using greedy algorithm
        Set<User> setScheduledUsers = greedyAssignment(visitsVehicleCapacity);

        return setScheduledUsers;
    }
}