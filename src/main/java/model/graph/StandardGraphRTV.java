package model.graph;

import com.google.common.collect.Sets;
import dao.Dao;
import dao.Logging;
import helper.Runtime;
import model.User;
import model.Vehicle;
import model.VisitObj;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import simulation.Method;
import simulation.matching.ResultAssignment;

import java.util.*;
import java.util.stream.Collectors;

public class StandardGraphRTV implements GraphRTV {

    // Populate list of feasible trips with current trips
    private List<List<VisitObj>> feasibleTrips;
    private SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRTV;
    private int maxVehicleCapacity;
    private Set<Vehicle> listVehicles;
    private Set<User> allRequests;
    private GraphRV graphRV;
    private long timeout;

    public Map<Vehicle, Set<VisitObj>> getVehicleVisitsMap() {
        return null;
    }

    @Override
    public Map<User, Set<VisitObj>> getUserVisitsMap() {
        return null;
    }

    public StandardGraphRTV(Set<User> allRequests, Set<Vehicle> listVehicles, int maxVehicleCapacity, double timeout, int maxVehReqEdges, int maxReqReqEdges) {
        Logging.logger.info("{}", String.format("# Matching - RTV - Graph (VR=%d,RR=%d) - #Requests: %d  / #Vehicles: %d (%d) - timeout: %.2f sec", maxVehReqEdges, maxReqReqEdges, allRequests.size(), listVehicles.size(), maxVehicleCapacity, timeout));
        this.timeout = (long) (timeout * 1000000000);

        // Populate list of feasible trips with current trips
        this.feasibleTrips = new ArrayList<>();
        this.maxVehicleCapacity = maxVehicleCapacity;
        this.graphRTV = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        this.allRequests = allRequests;
        this.listVehicles = listVehicles;

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


        Logging.logger.info("{}", String.format(
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

    public List<List<VisitObj>> getFeasibleTrips() {
        return feasibleTrips;
    }

    public void setFeasibleTrips(List<List<VisitObj>> feasibleTrips) {
        this.feasibleTrips = feasibleTrips;
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
            graphRTV.addVertex(request);
        }

        // Add current visits
        for (Vehicle vehicle : this.listVehicles) {

            graphRTV.addVertex(vehicle);

            if (vehicle.isServicing()) {

                // Add RTV edges for current trips
                if (!vehicle.getVisit().getRequests().isEmpty()) {

                    // All requests not picked up by vehicle also integrate RTV graph
                    for (User user : vehicle.getVisit().getRequests()) {
                        graphRTV.addVertex(user);
                    }

                    addRequestTripVehicleEdges(vehicle.getVisit());
                    // Level depends on number of requests
                    int levelIndex = Math.max(vehicle.getVisit().getRequests().size() - 1, 0);
                    feasibleTrips.get(levelIndex).add(vehicle.getVisit());
                }
            }
        }
    }

    /**
     * Add vertices and edges to RTV graph.
     * Add:
     *  - ( visit : vehicle ) edge
     *  - ( request : visit ) edges for each request covered by the visit. These edges have weights equal to user
     *    pickup delays.
     *
     * @param visit VisitObj (vehicle, route, requests, passengers)
     */
    private void addRequestTripVehicleEdges(VisitObj visit) {

        // Add best visit to RTV graph if not added before. Visits are the same if vehicles and routes are equal.
        // For example, if the setup visit was already there, similar draft visits do not need to be added.
        if (graphRTV.addVertex(visit)) {
            graphRTV.addEdge(visit, visit.getVehicle());

            Map<User, Integer> userDelayMap = visit.getUserPickupDelayMap();

            for (User request : visit.getRequests()) {
                DefaultWeightedEdge requestVisitEdge = graphRTV.addEdge(request, visit);
                int weight = userDelayMap.get(request);
                graphRTV.setEdgeWeight(requestVisitEdge, weight);
            }
        }

        // assert visit.getRequests() != null && !visit.getRequests().isEmpty() : String.format("VISIT %s", visit);
    }


    private void addRequestTripVehicleEdgesWithWeights(
            Vehicle vehicle,
            Set<User> requests,
            VisitObj visit) {

        // Add best visit to RTV graph
        graphRTV.addVertex(visit);
        graphRTV.addEdge(visit, vehicle);

        Map<User, Integer> userDelayMap = visit.getUserPickupDelayMap();

        for (User request : requests) {

            DefaultWeightedEdge requestVisitEdge = graphRTV.addEdge(request, visit);
            graphRTV.setEdgeWeight(requestVisitEdge, userDelayMap.get(request));
            // double a = graphRTV.getEdgeWeight(requestVisitEdge);
            // Logging.logger.info(request + "-" + a);
        }

        // assert visit.getRequests() != null && !visit.getRequests().isEmpty() : String.format("VISIT %s", visit);
    }


    public void printFeasibleTrips(String label) {

        Logging.logger.info(label);

        for (int nOfRquests = 0; nOfRquests < feasibleTrips.size(); nOfRquests++) {

            Logging.logger.info("\n##################### n. requests: " + (nOfRquests + 1));
            // Sort per vehicle
            List<VisitObj> sortedVisits = feasibleTrips.get(nOfRquests).stream().sorted((Comparator.comparing(o -> o.getVehicle().toString()))).collect(Collectors.toList());
            for (VisitObj v : sortedVisits) {
                Logging.logger.info(v.toString());
            }
        }
    }

    public void printDetailedVisitsLevel() {

        Logging.logger.info("### Vertices:");
        for (Object o : this.graphRTV.vertexSet()) {
            Logging.logger.info(o.toString());
        }
        for (int i = 0; i < feasibleTrips.size(); i++) {
            Logging.logger.info("{}", String.format("#### RTV LEVEL %d", i));
            for (VisitObj visit : feasibleTrips.get(i)) {
                Logging.logger.info("{}", String.format(" - %s", visit));
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
        Map<Vehicle, List<List<VisitObj>>> allFeasibleTrips = this.listVehicles.stream().parallel()
                .collect(Collectors.toMap(o -> o, this::getFeasibleTripsVehicle));
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_RTV_FEASIBLE_TRIPS);

        Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_RTV_POPULATE_GRAPH);
        allFeasibleTrips.forEach((vehicle, levelVisits) -> {

            //**********************************************************************************************************
            // Add visit with no requests ******************************************************************************
            //**********************************************************************************************************
            addOnlyPassengerVisitOfOnDutyVehicle(vehicle, levelVisits);


            //**********************************************************************************************************
            // Add dummy stop visit to assure every vehicle can be assigned to a visit *********************************
            //**********************************************************************************************************

                    for (int i = 0; i < levelVisits.size(); i++) {
                        levelVisits.get(i).forEach(this::addRequestTripVehicleEdges);
                        this.feasibleTrips.get(i).addAll(levelVisits.get(i));
                    }});
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_RTV_POPULATE_GRAPH);

        Logging.logger.info("{}", String.format("    - %6.2f s - Finding feasible trips\n    - %6.2f s - Populating graph\n",
                Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_FEASIBLE_TRIPS),
                Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_POPULATE_GRAPH)));
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
    private List<List<VisitObj>> getFeasibleTripsVehicle(Vehicle vehicle) {

        // Which points can a vehicle access in less than user pickup time?
        // Logging.logger.info("{}", String.format(" %s (%d) - %4d/%4d\n", vehicle, vehicle.getLastVisitedNode().getNetworkId(), Dao.getInstance().getReachability().get(vehicle.getLastVisitedNode().getNetworkId()).size(), Dao.getInstance().getDistMatrix().length));

        // Process times out after an interval
        long startTime = System.nanoTime();

        // Feasible visits of size k={1,2,3, ..., capacity(vehicle)}
        List<List<VisitObj>> feasibleVisitsAtLevel = new ArrayList<>();

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

                    VisitObj visit1 = feasibleVisitsAtLevel.get(0).get(i);
                    VisitObj visit2 = feasibleVisitsAtLevel.get(0).get(j);

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

            List<VisitObj> feasibleVisitsPreviousLevel = feasibleVisitsAtLevel.get(k - 1);
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

    private boolean isSubtripFeasible(List<VisitObj> feasibleVisits, Set<User> subTrip) {
        for (VisitObj feasibleVisit : feasibleVisits) {
            if (feasibleVisit.getRequests().equals(subTrip)) {
                return true;
            }
        }
        return false;
    }

    private void addOnlyPassengerVisitOfOnDutyVehicle(Vehicle vehicle, List<List<VisitObj>> feasibleVisitsCurrentVehicleAtLevel) {
        if (vehicle.isCarryingPassengers()) {
            VisitObj visitWithoutRequests = Method.getBestVisitFromPDPermutationsSummarized(vehicle, new HashSet<>());

            // Add best visit to RTV graph
            assert visitWithoutRequests != null : String.format("Cannot find visit for vehicle %s (carrying passengers) with current visit = %s", vehicle, vehicle.getVisit());

            feasibleVisitsCurrentVehicleAtLevel.get(0).add(visitWithoutRequests);
            graphRTV.addVertex(visitWithoutRequests);
            graphRTV.addEdge(visitWithoutRequests, vehicle);
        }
    }

    /**
     * Add RTV Edge with best visit found for vehicle and requests.
     * @param vehicle
     * @param requests
     * @param feasibleVisitsCurrentVehicleAtLevel
     * @return True, if there is a feasible visit where requests can be picked up by the vehicle.
     */
    private boolean addRTVEdgeAtLevel(Vehicle vehicle, Set<User> requests, List<VisitObj> feasibleVisitsCurrentVehicleAtLevel) {
        VisitObj bestVisit = Method.getBestVisitFromPDPermutationsSummarized(vehicle, requests);

        if (bestVisit != null) {
            feasibleVisitsCurrentVehicleAtLevel.add(bestVisit);
            return true;
        }
        return false;
    }


    public List<VisitObj> getListOfSortedVisitsFromVehicle(Vehicle vehicleCarryingPassenger) {
        return graphRTV
                .edgesOf(vehicleCarryingPassenger)
                .stream().map(o -> graphRTV.getEdgeSource(o))
                .map(o -> (VisitObj) o)
                .filter(o -> !o.getPassengers().isEmpty())
                .sorted(Comparator.comparing(visit -> ((VisitObj) visit).getRequests().size())
                        .reversed()
                        .thenComparing(o -> ((VisitObj) o).getDelay()))
                .collect(Collectors.toList());
    }

    public Set<VisitObj> getListOfVisitsFromVehicle(Vehicle vehicle) {
        Set<VisitObj> visits = graphRTV
                .edgesOf(vehicle)
                .stream().map(o -> graphRTV.getEdgeSource(o))
                .map(o -> (VisitObj) o)
                .collect(Collectors.toSet());
        return visits;

    }

    @Override
    public Map<Integer, Integer> getPickupLocationCandidateVehicleCountMap(){
        Map<Integer, Integer> pickupLocationCandidateVehicleCountMap = new HashMap<>();
        for (User user : allRequests) {
            graphRV.getVehiclesFromVREdgesOfRequest(user);
            pickupLocationCandidateVehicleCountMap.computeIfPresent(user.getNodePk().getNetworkId(), (userNetworkID, count) -> count + 1);
        }
        return pickupLocationCandidateVehicleCountMap;
    }
    @Override
    public GraphRV getGraphRV() {
        return this.graphRV;
    }

    public Set<DefaultWeightedEdge> edgesOf(Object o) {
        return graphRTV.edgesOf(o);
    }

    public boolean removeVertex(Object o) {
        return graphRTV.removeVertex(o);
    }

    public Object getEdgeTarget(DefaultWeightedEdge edge) {
        return graphRTV.getEdgeTarget(edge);
    }

    public double getWeightFromRequestVisitEdge(User request, VisitObj visit) {
        double weight = this.graphRTV.getEdgeWeight(this.graphRTV.getEdge(request, visit));
        assert Double.compare(weight, getWeightFromRequestVisitEdge2(request, visit))==0;
        return weight;
    }

    public double getWeightFromRequestVisitEdge2(User request, VisitObj visit) {
        return visit.getUserPickupDelayMap().get(request);
    }

    public Set<VisitObj> getListOfVisitsFromUser(User request) {
        Set<VisitObj> visits = graphRTV
                .edgesOf(request)
                .stream().map(o -> graphRTV.getEdgeTarget(o))
                .map(o -> (VisitObj) o)
                .collect(Collectors.toSet());
        return visits;
    }

    public Set<Vehicle> getVehiclesFromUser(User request) {
        return graphRTV
                .edgesOf(request)
                .stream().map(o -> graphRTV.getEdgeTarget(o))
                .map(o -> ((VisitObj) o).getVehicle())
                .collect(Collectors.toSet());
    }

    public Set<Vehicle> getHiredVehiclesFromUser(User request) {
        return graphRTV
                .edgesOf(request)
                .stream().map(o -> graphRTV.getEdgeTarget(o))
                .map(o -> ((VisitObj) o).getVehicle())
                .filter(Vehicle::isHired).collect(Collectors.toSet());
    }

    public void removeOkVerticesRTV(ResultAssignment result) {
        for (VisitObj visit : result.getVisitsOK()) {
            this.graphRTV.removeVertex(visit);
        }
        for (Vehicle vehicle : result.getVehiclesOK()) {
            this.graphRTV.removeVertex(vehicle);
        }
        for (User request : result.getRequestsOK()) {
            this.graphRTV.removeVertex(request);
        }
    }

    public boolean allRequestsUnmatched(VisitObj visit) {

        for (User request : visit.getRequests()) {

            // If user is not in graph, it means another trip picked up user
            if (!containsVertex(request)) {
                return false;
            }
        }
        return true;
    }

    public Set<Object> vertexSet() {
        return graphRTV.vertexSet();
    }

    public int getVisitCount() {
        return (int) graphRTV.vertexSet().stream().filter(o -> o instanceof VisitObj).count();
    }

    public Set<VisitObj> getAllVisits() {
        Set<VisitObj> visits = graphRTV.vertexSet().stream().filter(o -> o instanceof VisitObj).map(o -> (VisitObj) o).collect(Collectors.toSet());
        return visits;
    }

    public int getVisitCountSetVertex() {
        return graphRTV.vertexSet().stream().filter(o -> o instanceof VisitObj).collect(Collectors.toSet()).size();
    }

    public int getMaxVehicleCapacity() {
        return (int) graphRTV.vertexSet().stream().filter(o -> o instanceof Vehicle).count();
    }

    public int getUserCountVertex() {
        return (int) graphRTV.vertexSet().stream().filter(o -> o instanceof User).count();
    }

    public int getTotalVertex() {
        return graphRTV.vertexSet().size();
    }

    public int getFeasibleVisitCount() {
        return feasibleTrips.stream().map(visits -> visits.size()).collect(Collectors.summingInt(value -> value.intValue()));
    }

    public void removeVisit(VisitObj visit) {

        // Remove vehicle and users matched from graph
        for (User user : visit.getRequests()) {
            graphRTV.removeVertex(user);
        }

        // Remove visit from graph
        graphRTV.removeVertex(visit);
        graphRTV.removeVertex(visit.getVehicle());
    }

    public boolean containsVertex(Object vertex) {
        return graphRTV.containsVertex(vertex);
    }

    public Set<User> getAllRequests() {
        return allRequests;
    }

    public Set<Vehicle> getListVehicles() {
        return listVehicles;
    }

    @Override
    public void printAllVisitsPerVehicle() {

    }

    public Set<Vehicle> getListVehiclesFromRTV() {
        Set<Vehicle> vehicles = this.listVehicles.stream().filter(vehicle -> !this.graphRTV.edgesOf(vehicle).isEmpty()).collect(Collectors.toSet());
        return vehicles;
    }
}

