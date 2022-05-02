package model.graph;

import com.google.common.collect.Sets;
import dao.Dao;
import helper.Runtime;
import model.User;
import model.Vehicle;
import model.Visit;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import simulation.Method;
import simulation.matching.ResultAssignment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ParallelGraphRTV implements GraphRTV{

    // Populate list of feasible trips with current trips
    private List<List<Visit>> feasibleTrips;
    private int maxVehicleCapacity;
    private Set<Vehicle> listVehicles;
    private Set<User> allRequests;
    private GraphRV graphRV;
    private long timeout;
    public ConcurrentHashMap.KeySetView<Visit, Boolean> allVisits;
    public ConcurrentHashMap<Vehicle, Set<Visit>>  mapVehicleVisits;
    public ConcurrentHashMap<User, Set<Visit>> mapUserVisits;
    public ConcurrentHashMap<User, Set<Vehicle>> mapUserVehicles;


    public ParallelGraphRTV(Set<User> allRequests, Set<Vehicle> listVehicles, int maxVehicleCapacity, double timeout, int maxVehReqEdges, int maxReqReqEdges) {
        System.out.println(String.format("# Matching - RTV - Graph (VR=%d,RR=%d) - #Requests: %d  / #Vehicles: %d (%d) - timeout: %.2f sec", maxVehReqEdges, maxReqReqEdges, allRequests.size(), listVehicles.size(), maxVehicleCapacity, timeout));
        this.timeout = (long) (timeout * 1000000000);

        // Populate list of feasible trips with current trips
        this.feasibleTrips = new ArrayList<>();
        this.maxVehicleCapacity = maxVehicleCapacity;
        this.allRequests = allRequests;
        this.listVehicles = listVehicles;
        mapVehicleVisits = new ConcurrentHashMap<>();
        mapUserVisits = new ConcurrentHashMap<>();
        mapUserVehicles = new ConcurrentHashMap<>();
        allVisits = ConcurrentHashMap.newKeySet();

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // REQUEST - VEHICLE (RV) //////////////////////////////////////////////////////////////////////////////////////

        Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_CREATE_RV);
        this.graphRV = new GraphRV(allRequests, listVehicles, maxVehicleCapacity, maxVehReqEdges, maxReqReqEdges);
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_CREATE_RV);

        //graphRV.printRVEdges();
        //graphRV.printRREdges();

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // REQUEST - TRIP - VEHICLE (RTV) //////////////////////////////////////////////////////////////////////////////

        // Add the data (visit, vehicle, and user vertices) of current trips in the RTV graph
        Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_RTV_INIT);
        this.initDataStructures();
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_RTV_INIT);

        // Create request-trip-vehicle (RTV) structure
        Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_RTV_BUILDING_TOTAL);
        this.buildGraph();
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_RTV_BUILDING_TOTAL);


        System.out.println(String.format(
                "\n\n# Matching - RTV"
                        + "\n    - %6.2f s - RV Creation        (%s)"
                        + "\n    - %6.2f s - RTV Initialization "
                        + "\n    - %6.2f s - RTV Building       (%s)",
                Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_CREATE_RV),
                this.graphRV,
                Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_INIT),
                Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_BUILDING_TOTAL),
                this.getSummaryFeasibleTripsLevel()));
    }

    public List<List<Visit>> getFeasibleTrips() {
        return feasibleTrips;
    }

    public void setFeasibleTrips(List<List<Visit>> feasibleTrips) {
        this.feasibleTrips = feasibleTrips;
    }

    /**
     * Vehicles stopped (i.e., current node is of type NodeST and visit is null) have to be added to the RTV graph
     * to assure every vehicle is assigned to a trip. We create a dummy trip (VisitStop), to allow this to happen.
     * <p>
     * Notice that some rebalancing vehicles CANNOT be associated to any trip (infeasible visits).
     */
    public void addStopVisits() {

        // Add current visits
        for (Vehicle vehicle : listVehicles) {
            addStopVisit(vehicle);
        }
    }

    /**
     * Add stop visit to RTV for vehicle moving (rebalancing, cruising)
     * @param vehicle Vehicle to create stop visit
     */
    public void addStopVisit(Vehicle vehicle) {
        Visit stop = vehicle.getStopVisit();

        // Vehicles carrying passengers don't stop
        if (stop != null) {
            feasibleTrips.get(0).add(stop);
            computeVisit(stop);
        }
    }

    /**
     * Populate RTV graph with the setup visits.
     * - Add vertices for users and vehicles
     * - Add vertices for passengers
     * - Add visit vertices for current trips
     */
    private void initDataStructures() {

        for (int i = 0; i < maxVehicleCapacity; i++) {
            this.feasibleTrips.add(new ArrayList<>());
        }

        // Populate graph with request vertex
        for (User request : this.allRequests) {
            mapUserVisits.put(request, ConcurrentHashMap.newKeySet());
            mapUserVehicles.put(request, ConcurrentHashMap.newKeySet());
        }

        // Add current visits
        for (Vehicle vehicle : this.listVehicles) {

            mapVehicleVisits.put(vehicle, new HashSet<>());

            if (vehicle.isServicing()) {

                // Add RTV edges for current trips
                if (!vehicle.getVisit().getRequests().isEmpty()) {
                    computeVisit(vehicle.getVisit());
                    // Level depends on number of requests
                    int levelIndex = Math.max(vehicle.getVisit().getRequests().size() - 1, 0);
                    feasibleTrips.get(levelIndex).add(vehicle.getVisit());
                }
            }
        }
    }



    public void printFeasibleTrips(String label) {

        System.out.println(label);

        for (int nOfRquests = 0; nOfRquests < feasibleTrips.size(); nOfRquests++) {

            System.out.println("\n##################### n. requests: " + (nOfRquests + 1));
            // Sort per vehicle
            List<Visit> sortedVisits = feasibleTrips.get(nOfRquests).stream().sorted((Comparator.comparing(o -> o.getVehicle().toString()))).collect(Collectors.toList());
            for (Visit v : sortedVisits) {
                System.out.println(v);
            }
        }
    }


    public String getSummaryFeasibleTripsLevel() {
        List<String> builder = new ArrayList<>();

        for (int i = 0; i < feasibleTrips.size(); i++) {
            builder.add(String.format("L%d = %d", i, feasibleTrips.get(i).size()));
        }

        return "feasible trips = [" + String.join(", ", builder) + "]";
    }

    public void buildGraph() {

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // FIND FEASIBLE TRIPS /////////////////////////////////////////////////////////////////////////////////////////

        Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_RTV_FEASIBLE_TRIPS);
        Map<Vehicle, List<List<Visit>>> allFeasibleTrips = this.listVehicles.stream().parallel()
                .collect(Collectors.toMap(o -> o, this::getFeasibleTripsVehicle));
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_RTV_FEASIBLE_TRIPS);

        Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_RTV_POPULATE_GRAPH);
        allFeasibleTrips.forEach((vehicle, levelVisits) -> {

            //**********************************************************************************************************
            // Add visit where there are no requests *******************************************************************
            //**********************************************************************************************************

            Visit noRequestsVisit = null;
            if (vehicle.isParked() || vehicle.isRebalancing()) {
                // Vehicle continues its path (dummy "do nothing" visit i.e., visit is null)
                // TODO passthrough visit (keep current visit)
                noRequestsVisit = vehicle.getVisitStop();

            } else if (vehicle.isCruisingToPickup()) {
                // Interrupt cruising, drop requests, and rebalance to closest middle
                noRequestsVisit = vehicle.getVisitRelocationToMiddle();

            } else if (vehicle.isCarryingPassengers()) {
                // Only passengers are kept
                noRequestsVisit = Method.getBestVisitFromPDPermutationsSummarized(vehicle, new HashSet<>());
            }

            this.feasibleTrips.get(0).add(noRequestsVisit);
            this.computeVisit(noRequestsVisit);


            for (int i = 0; i < levelVisits.size(); i++) {
                this.feasibleTrips.get(i).addAll(levelVisits.get(i));
            }
        });
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_RTV_POPULATE_GRAPH);

        System.out.printf("    - %6.2f s - Finding feasible trips\n    - %6.2f s - Populating graph\n",
                Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_FEASIBLE_TRIPS),
                Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_POPULATE_GRAPH));
    }

    /**
     * !
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
     * @param vehicle
     * @return
     */
    private List<List<Visit>> getFeasibleTripsVehicle(Vehicle vehicle) {

        // Which points can a vehicle access in less than user pickup time?
        // System.out.printf(" %s (%d) - %4d/%4d\n", vehicle, vehicle.getLastVisitedNode().getNetworkId(), Dao.getInstance().getReachability().get(vehicle.getLastVisitedNode().getNetworkId()).size(), Dao.getInstance().getDistMatrix().length);

        // Process times out after an interval
        long startTime = System.nanoTime();

        // Feasible visits of size k={1,2,3, ..., capacity(vehicle)}
        List<List<Visit>> feasibleVisitsAtLevel = new ArrayList<>();

        // Feasible trips of size k={1,2,3, ..., capacity(vehicle)}
        // A trip is feasible if there is ANY feasible visit featuring its users.
        List<Set<Set<User>>> feasibleTripsAtLevel = new ArrayList<>();

        for (int i = 0; i < vehicle.getCapacity(); i++) {
            feasibleVisitsAtLevel.add(new LinkedList<>());
            feasibleTripsAtLevel.add(new HashSet<>());
        }

        //**********************************************************************************************************
        // Adding feasible visits of size = 1 **********************************************************************
        //**********************************************************************************************************
        for (DefaultWeightedEdge edge : this.graphRV.edgesOf(vehicle)) {

            // Interrupt processing
            if (System.nanoTime() - startTime >= timeout)
                return feasibleVisitsAtLevel;

            User request = (User) this.graphRV.getEdgeTarget(edge);

            // Try ALL insertions of request in vehicle visit sequence
            addRTVEdgeAtLevel(vehicle, new HashSet<>(Arrays.asList(request)), feasibleVisitsAtLevel.get(0));
        }

        //**********************************************************************************************************
        // Adding feasible visits of size = 2 **********************************************************************
        //**********************************************************************************************************

        if (vehicle.getCapacity() >= 2) {

            for (int i = 0; i < feasibleVisitsAtLevel.get(0).size() - 1; i++) {
                for (int j = i + 1; j < feasibleVisitsAtLevel.get(0).size(); j++) {

                    // Interrupt processing
                    if (System.nanoTime() - startTime >= timeout)
                        return feasibleVisitsAtLevel;

                    Visit visit1 = feasibleVisitsAtLevel.get(0).get(i);
                    Visit visit2 = feasibleVisitsAtLevel.get(0).get(j);

                    User request1 = visit1.getRequests().iterator().next();
                    User request2 = visit2.getRequests().iterator().next();

                    assert visit1.getRequests().size() == 1 : String.format("More than 1 request: %s", visit1.getRequests());
                    assert visit2.getRequests().size() == 1 : String.format("More than 1 request: %s", visit2.getRequests());

                    // If RV edge exists, there is a possible trip including request1 and request2
                    if (this.graphRV.getEdge(request1, request2) != null) {

                        Set<User> requests = new HashSet<>(visit1.getRequests());
                        requests.addAll(visit2.getRequests());
                        if (addRTVEdgeAtLevel(vehicle, requests, feasibleVisitsAtLevel.get(1))) {
                            feasibleTripsAtLevel.get(1).add(requests);
                        }
                    }
                }
            }
        }

        for (int k = 2; k < vehicle.getCapacity(); k++) {

            Set<Set<User>> tripsAlreadyAddedToCurrentLevel = new HashSet<>();

            // Interrupt processing
            if (System.nanoTime() - startTime >= timeout)
                return feasibleVisitsAtLevel;

            List<Visit> feasibleVisitsPreviousLevel = feasibleVisitsAtLevel.get(k - 1);
            Set<Set<User>> feasibleTripsPreviousLevel = feasibleTripsAtLevel.get(k - 1);

            for (int i = 0; i < feasibleVisitsPreviousLevel.size() - 1; i++) {
                for (int j = i + 1; j < feasibleVisitsPreviousLevel.size(); j++) {

                    Set<User> combinedTrip = Sets.union(
                            feasibleVisitsPreviousLevel.get(i).getRequests(),
                            feasibleVisitsPreviousLevel.get(j).getRequests()
                    );

                    if (!tripsAlreadyAddedToCurrentLevel.contains(combinedTrip) && combinedTrip.size() == k + 1) {
                        tripsAlreadyAddedToCurrentLevel.add(combinedTrip);

                        if (allSubTripsAreFeasible(feasibleTripsPreviousLevel, combinedTrip)) {
                            if (addRTVEdgeAtLevel(vehicle, combinedTrip, feasibleVisitsAtLevel.get(k))) {
                                feasibleTripsAtLevel.get(k).add(combinedTrip);
                            }
                        }
                    }
                }
            }
        }

        return feasibleVisitsAtLevel;
    }

    /**
     * Determine if all sub-trips featuring $k-1$ users are feasible (i.e., users can be combined).
     * If so, trip of size $k$ may also be feasible.
     * Example:
     * Trip featuring users (u1, u2, u3) is feasible if:
     * - Trip (u1, u2) is feasible
     * - Trip (u1, u3) is feasible
     * - Trip (u2, u3) is feasible
     *  A trip is feasible, if there is any visit where users can be successfully picked up and delivered.
     *  Example:
     *  Trip (u1, u2) is feasible if there is a feasible visit in:
     *   - 1,1',2,2'
     *   - 1,2,1',2'
     *   ...
     *   - 2,2',1,1'
     * @param usersFeasibleTripsPreviousLevel Set of feasible trips of size $k-1$.
     * @param usersCandidateTripCurrentLevel Candidate trip of size $k$.
     * @return true if all sub-trips of candidate trip are feasible (i.e., featured in previous level)
     */
    private boolean allSubTripsAreFeasible(Set<Set<User>> usersFeasibleTripsPreviousLevel, Set<User> usersCandidateTripCurrentLevel) {

        for (User request : usersCandidateTripCurrentLevel) {

            // Create sub-trip from candidate trip with one fewer request
            Set<User> subTrip = new HashSet<>(usersCandidateTripCurrentLevel);
            subTrip.remove(request);

            if (!usersFeasibleTripsPreviousLevel.contains(subTrip))
                return false;
        }
        return true;
    }

    private boolean isSubtripFeasible(List<Visit> feasibleVisits, Set<User> subTrip) {
        for (Visit feasibleVisit : feasibleVisits) {
            if (feasibleVisit.getRequests().equals(subTrip)) {
                return true;
            }
        }
        return false;
    }

    private void addOnlyPassengerVisitOfOnDutyVehicle(Vehicle vehicle, List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel) {
        if (vehicle.isCarryingPassengers()) {
            Visit visitWithoutRequests = Method.getBestVisitFromPDPermutationsSummarized(vehicle, new HashSet<>());

            // Add best visit to RTV graph
            assert visitWithoutRequests != null : String.format("Cannot find visit for vehicle %s (carrying passengers) with current visit = %s", vehicle, vehicle.getVisit());

            feasibleVisitsCurrentVehicleAtLevel.get(0).add(visitWithoutRequests);
            computeVisit(visitWithoutRequests);
        }
    }

    /**
     * Add RTV Edge with best visit found for vehicle and requests.
     * @param vehicle
     * @param requests
     * @param feasibleVisitsCurrentVehicleAtLevel
     * @return True, if there is a feasible visit where requests can be picked up by the vehicle.
     */
    private boolean addRTVEdgeAtLevel(Vehicle vehicle, Set<User> requests, List<Visit> feasibleVisitsCurrentVehicleAtLevel) {
        Visit bestVisit = Method.getBestVisitFromPDPermutationsSummarized(vehicle, requests);

        if (bestVisit != null) {
            feasibleVisitsCurrentVehicleAtLevel.add(bestVisit);
            computeVisit(bestVisit);
            return true;
        }
        return false;
    }

    private void computeVisit(Visit visit) {
        allVisits.add(visit);
        mapVehicleVisits.get(visit.getVehicle()).add(visit);
        visit.genUserPickupDelayMap();
        visit.getRequests().stream().forEach(user -> {
            mapUserVisits.get(user).add(visit);
            mapUserVehicles.get(user).add(visit.getVehicle());
        });
    }

    public Set<Visit> getListOfVisitsFromVehicle(Vehicle vehicle) {
        return mapVehicleVisits.get(vehicle);
    }

//    public Set<DefaultWeightedEdge> edgesOf(Object o) {
//        return graphRTV.edgesOf(o);
//    }
//
//    public boolean removeVertex(Object o) {
//        return graphRTV.removeVertex(o);
//    }
//
//    public Object getEdgeTarget(DefaultWeightedEdge edge) {
//        return graphRTV.getEdgeTarget(edge);
//    }

    public double getWeightFromRequestVisitEdge(User request, Visit visit) {
        return visit.userDelayMap.get(request);
    }

    public Set<Visit> getListOfVisitsFromUser(User request) {
        return mapUserVisits.get(request);
    }

    public Set<Vehicle> getVehiclesFromUser(User request) {
        return mapUserVehicles.get(request);
    }

    public Set<Vehicle> getHiredVehiclesFromUser(User request) {
        return mapUserVehicles.get(request).stream().filter(Vehicle::isHired).collect(Collectors.toSet());
    }

    @Override
    public Set<Vehicle> getListVehicles() {
        return mapVehicleVisits.keySet();
    }

    public Set<Visit> getAllVisits() {
        return allVisits;
    }
    public Set<User> getAllRequests() {
        return mapUserVehicles.keySet();
    }

    public Set<Vehicle> getListVehiclesFromRTV() {
        return mapVehicleVisits.keySet();
    }

    public int getFeasibleVisitCount() {
        return feasibleTrips.stream().map(visits -> visits.size()).collect(Collectors.summingInt(value -> value.intValue()));
    }

}

