package model.graph;

import com.google.common.collect.Sets;
import model.User;
import model.Vehicle;
import model.Visit;
import model.VisitStop;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import simulation.Method;
import simulation.ResultAssignment;
import simulation.Solution;

import java.util.*;
import java.util.stream.Collectors;

public class GraphRTV {

    private final int maxVehReqEdges = 30;
    // Populate list of feasible trips with current trips
    private List<List<Visit>> feasibleTrips;
    private SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRTV;
    private int maxVehicleCapacity;
    private List<Vehicle> listVehicles;
    private List<User> allRequests;
    private GraphRV graphRV;
    private Map<String, Long> runTimes;
    private long timeout;

    public GraphRTV(List<User> allRequests, List<Vehicle> listVehicles, int maxVehicleCapacity, double timeout) {

        runTimes = new HashMap<>();
        this.timeout = (long) (timeout * 1000000000);

        // Populate list of feasible trips with current trips
        this.feasibleTrips = new ArrayList<>();
        this.maxVehicleCapacity = maxVehicleCapacity;
        this.graphRTV = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        this.allRequests = allRequests;
        this.listVehicles = listVehicles;

        // REQUEST - VEHICLE (RV)
        this.runTimes.put(Solution.TIME_CREATE_RV, System.nanoTime());
        this.graphRV = new GraphRV(allRequests, listVehicles, maxVehicleCapacity, maxVehReqEdges);
        this.runTimes.put(Solution.TIME_CREATE_RV, System.nanoTime() - this.runTimes.get(Solution.TIME_CREATE_RV));
        //System.out.println(String.format("# RV created (%.2f sec) - %s", (this.runTimes.get(Solution.TIME_CREATE_RV)) / 1000000000.0, this.graphRV.statsRV()));
        System.out.println(String.format("# 1) RV created (%.2f sec) - RV stats: %s", (this.runTimes.get(Solution.TIME_CREATE_RV)) / 1000000000.0, this.graphRV));
        //graphRV.printRVEdges();
        //graphRV.printRREdges();
        //this.runTimes.put(Solution.TIME_CREATE_RV + "2", System.nanoTime());
        //GraphRV graphRV = new GraphRV(allRequests, listVehicles, vehicleCapacity);
        //graphRV.keepFastestRVLinks(maxVehReqEdges);
        //this.runTimes.put(Solution.TIME_CREATE_RV + "2", System.nanoTime() - this.runTimes.get(Solution.TIME_CREATE_RV + "2"));
        //System.out.println(String.format("# 2) RV created (%.2f sec) - RV stats: %s", (this.runTimes.get(Solution.TIME_CREATE_RV + "2")) / 1000000000.0, graphRV));

        //this.graphRV.keepFastestRVLinks(maxVehReqEdges);
        //System.out.println(String.format("# RV created (%.2f sec) - RV stats: %s", (this.runTimes.get(Solution.TIME_CREATE_RV)) / 1000000000.0, this.graphRV));
        //graphRV.printRVEdges();
        //graphRV.printRREdges();

        // Add the data of current trips in the RTV graph
        this.initDataStructures();

        // Create request-trip-vehicle (RTV) structure
        this.runTimes.put(Solution.TIME_CREATE_RTV, System.nanoTime());
        this.buildGraph();
        this.runTimes.put(Solution.TIME_CREATE_RTV, System.nanoTime() - this.runTimes.get(Solution.TIME_CREATE_RTV));
        System.out.println(String.format("# 2) RTV created (%.2f sec) - %s", (this.runTimes.get(Solution.TIME_CREATE_RTV) / 1000000000.0), this.getSummaryFeasibleTripsLevel()));
    }

    public GraphRTV(GraphRV graphRV, int maxVehicleCapacity, List<User> allRequests, List<Vehicle> listVehicles) {

        // Populate list of feasible trips with current trips
        this.feasibleTrips = new ArrayList<>();
        this.maxVehicleCapacity = maxVehicleCapacity;
        this.graphRTV = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        this.allRequests = allRequests;
        this.listVehicles = listVehicles;
        this.graphRV = graphRV;

        // Add the data of current trips in the RTV graph
        this.initDataStructures();

        // Create request-trip-vehicle (RTV) structure
        this.buildGraph();
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

            if (vehicle.isRebalancing()) {
                feasibleTrips.get(0).add(vehicle.getVisit());
                addRequestTripVehicleEdges(vehicle, new HashSet<>(), vehicle.getVisit());
            }

            if (vehicle.isParked()) {
                Visit visitStayParked = new VisitStop(vehicle);
                addRequestTripVehicleEdges(vehicle, new HashSet<>(), visitStayParked);
                feasibleTrips.get(0).add(visitStayParked);
            }

            if (vehicle.isServicing()) {

                if (!vehicle.isCarryingPassengers()) {
                    Visit visitStopAtClosestNode = vehicle.getRebalanceVisit();
                    addRequestTripVehicleEdges(vehicle, new HashSet<>(), visitStopAtClosestNode);
                    feasibleTrips.get(0).add(visitStopAtClosestNode);
                }
            }
        }
    }

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

                    addRequestTripVehicleEdges(vehicle, vehicle.getVisit().getRequests(), vehicle.getVisit());
                    feasibleTrips.get(Math.max(vehicle.getVisit().getRequests().size() - 1, 0)).add(vehicle.getVisit());
                }
            }
        }
    }

    private void addRequestTripVehicleEdges(
            Vehicle vehicle,
            Set<User> requests,
            Visit visit) {

        // Add best visit to RTV graph
        graphRTV.addVertex(visit);
        graphRTV.addEdge(visit, vehicle);

        Map<User, Integer> userDelayMap = visit.getUserDelayPairs();

        for (User request : requests) {
            DefaultWeightedEdge requestVisitEdge = graphRTV.addEdge(request, visit);
            graphRTV.setEdgeWeight(requestVisitEdge, userDelayMap.get(request));
        }

        // assert visit.getRequests() != null && !visit.getRequests().isEmpty() : String.format("VISIT %s", visit);
    }


    private void addRequestTripVehicleEdgesWithWeights(
            Vehicle vehicle,
            Set<User> requests,
            Visit visit) {

        // Add best visit to RTV graph
        graphRTV.addVertex(visit);
        graphRTV.addEdge(visit, vehicle);

        Map<User, Integer> userDelayMap = visit.getUserDelayPairs();

        for (User request : requests) {

            DefaultWeightedEdge requestVisitEdge = graphRTV.addEdge(request, visit);
            graphRTV.setEdgeWeight(requestVisitEdge, userDelayMap.get(request));
            // double a = graphRTV.getEdgeWeight(requestVisitEdge);
            // System.out.println(request + "-" + a);
        }

        // assert visit.getRequests() != null && !visit.getRequests().isEmpty() : String.format("VISIT %s", visit);
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

    public void printDetailedVisitsLevel() {

        System.out.println("### Vertices:");
        for (Object o : this.graphRTV.vertexSet()) {
            System.out.println(o);
        }
        for (int i = 0; i < feasibleTrips.size(); i++) {
            System.out.println(String.format("#### RTV LEVEL %d", i));
            for (Visit visit : feasibleTrips.get(i)) {
                System.out.println(String.format(" - %s", visit));
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

        /*this.listVehicles.parallelStream()
                .map(this::getFeasibleTripsVehicle)
                .collect(
                        ArrayList::new,
                        (listOfLevels, visits) -> {
                            for (int i = 0; i < visits.size(); i++) {
                                this.feasibleTrips.get(i).addAll(visits.get(i));
                            }
                        }, (lists, lists2) -> {
                        });

        this.feasibleTrips.forEach(visits -> visits.forEach(visit -> addRequestTripVehicleEdges(this.graphRTV, visit.getVehicle(), visit.getRequests(), visit)));
*/
        long processFeasibleTripsParallel = System.nanoTime();
        Map<Vehicle, List<List<Visit>>> allFeasibleTrips = this.listVehicles.parallelStream()
                .collect(Collectors.toMap(o -> o, this::getFeasibleTripsVehicle));

        allFeasibleTrips.forEach((vehicle, levelVisits) -> {
                    addVisitWithPassengers(vehicle, levelVisits);
                    for (int i = 0; i < levelVisits.size(); i++) {
                        levelVisits.get(i).forEach(visit -> addRequestTripVehicleEdges(
                                visit.getVehicle(),
                                visit.getRequests(),
                                visit));

                        this.feasibleTrips.get(i).addAll(levelVisits.get(i));
                    }
                }
        );

        System.out.println(String.format("# Processing fleet parallel (%.2f sec)...", ((System.nanoTime() - processFeasibleTripsParallel) / 1000000000.0)));


        /*long processFeasibleTripsSingle = System.nanoTime();
        for (Vehicle vehicle : this.listVehicles) {
            long processFeasibleTrips = System.nanoTime();
            List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel = getFeasibleTripsVehicle(vehicle);
        }
        System.out.println(String.format("# Processing fleet single (%.2f sec)...", ((System.nanoTime() - processFeasibleTripsSingle) / 1000000000.0)));
        */
        /*for (Vehicle vehicle : this.listVehicles) {
            // long processFeasibleTrips = System.nanoTime();
            List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel = getFeasibleTripsVehicle(vehicle);

            for (int i = 0; i < vehicle.getCapacity(); i++) {
                for (Visit visit : feasibleVisitsCurrentVehicleAtLevel.get(i)) {
                    addRequestTripVehicleEdges(visit.getVehicle(), visit.getRequests(), visit);
                }
                this.feasibleTrips.get(i).addAll(feasibleVisitsCurrentVehicleAtLevel.get(i));
            }

            //System.out.println(String.format("# Processing vehicle %s (%.2f sec)...", vehicle, ((System.nanoTime() - processFeasibleTrips) / 1000000000.0)));
        }*/
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

        // Process times out after an interval
        long startTime = System.nanoTime();

        // Feasible visits of size k={1,2,3, ..., capacity(vehicle)}
        List<List<Visit>> feasibleVisitsAtLevel = new ArrayList<>();
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
            addRTVEdgeAtLevel(vehicle, new HashSet<>(Arrays.asList(request)), feasibleVisitsAtLevel, 0);
        }

        //**********************************************************************************************************
        // Adding feasible visits of size = 2 **********************************************************************
        //**********************************************************************************************************

        if(vehicle.getCapacity() >= 2) {

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
                        if (addRTVEdgeAtLevel(vehicle, requests, feasibleVisitsAtLevel, 1)) {
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
                            if (addRTVEdgeAtLevel(vehicle, combinedTrip, feasibleVisitsAtLevel, k)) {
                                feasibleTripsAtLevel.get(k).add(combinedTrip);
                            }
                        }
                    }
                }
            }
        }

        return feasibleVisitsAtLevel;
    }
    

    private boolean allSubTripsAreFeasible(Set<Set<User>> feasibleTrips, Set<User> combinedRequestList) {

        for (User request : combinedRequestList) {

            // Sub trip MUST be part of last level
            Set<User> subTrip = new HashSet<>(combinedRequestList);

            subTrip.remove(request);
            //System.out.println("-testing=" + subTrip);

            if (!feasibleTrips.contains(subTrip))
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

    private void addVisitWithPassengers(Vehicle vehicle, List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel) {
        if (vehicle.isCarryingPassengers()) {
            Visit visitWithoutRequests = Method.getBestVisitFor(vehicle, new HashSet<>());

            // Add best visit to RTV graph
            assert visitWithoutRequests != null : String.format("Cannot find visit for vehicle %s (carrying passengers) with current visit = %s", vehicle, vehicle.getVisit());

            feasibleVisitsCurrentVehicleAtLevel.get(0).add(visitWithoutRequests);
            graphRTV.addVertex(visitWithoutRequests);
            graphRTV.addEdge(visitWithoutRequests, vehicle);
        }
    }

    private boolean addRTVEdgeAtLevel(Vehicle vehicle, Set<User> requests, List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel, int level) {

        Visit visit = Method.getBestVisitFor(vehicle, requests);

        if (visit != null) {
            feasibleVisitsCurrentVehicleAtLevel.get(level).add(visit);
            return true;
        }
        return false;
    }


    public List<Visit> getListOfSortedVisitsFromVehicle(Vehicle vehicleCarryingPassenger) {
        return graphRTV
                .edgesOf(vehicleCarryingPassenger)
                .stream().map(o -> graphRTV.getEdgeSource(o))
                .map(o -> (Visit) o)
                .filter(o -> !o.getPassengers().isEmpty())
                .sorted(Comparator.comparing(visit -> ((Visit) visit).getRequests().size())
                        .reversed()
                        .thenComparing(o -> ((Visit) o).getDelay()))
                .collect(Collectors.toList());
    }

    public List<Visit> getListOfVisitsFromVehicle(Vehicle vehicle) {
        return graphRTV
                .edgesOf(vehicle)
                .stream().map(o -> graphRTV.getEdgeSource(o))
                .map(o -> (Visit) o)
                .collect(Collectors.toList());
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

    public double getWeightFromRequestVisitEdge(User request, Visit visit) {
        return this.graphRTV.getEdgeWeight(this.graphRTV.getEdge(request, visit));
    }

    public List<Visit> getListOfVisitsFromUser(User request) {
        return graphRTV
                .edgesOf(request)
                .stream().map(o -> graphRTV.getEdgeTarget(o))
                .map(o -> (Visit) o)
                .collect(Collectors.toList());
    }

    public void removeOkVerticesRTV(ResultAssignment result) {
        for (Visit visit : result.getVisitsOK()) {
            this.graphRTV.removeVertex(visit);
        }
        for (Vehicle vehicle : result.getVehiclesOK()) {
            this.graphRTV.removeVertex(vehicle);
        }
        for (User request : result.getRequestsOK()) {
            this.graphRTV.removeVertex(request);
        }
    }

    public boolean allRequestsUnmatched(Visit visit) {

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
        return (int) graphRTV.vertexSet().stream().filter(o -> o instanceof Visit).count();
    }

    public List<Visit> getAllVisits() {
        return graphRTV.vertexSet().stream().filter(o -> o instanceof Visit).map(o -> (Visit) o).collect(Collectors.toList());
    }

    public int getVisitCountSetVertex() {
        return graphRTV.vertexSet().stream().filter(o -> o instanceof Visit).collect(Collectors.toSet()).size();
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

    public void removeVisit(Visit visit) {

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

    public List<User> getAllRequests() {
        return allRequests;
    }

    public List<Vehicle> getListVehicles() {
        return listVehicles;
    }

    public List<Vehicle> getListVehiclesFromRTV() {
        return this.listVehicles.stream().filter(vehicle -> !this.graphRTV.edgesOf(vehicle).isEmpty()).collect(Collectors.toList());
    }

}

