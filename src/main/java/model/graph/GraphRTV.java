/** Create request-vehicle (RV) structure
 *
 *                       /-----P1
 *         V1---- TRIP[2]
 *           \           \-----P2
 *            \          /-----P1
 *              \ TRIP[3] -----P2
 *                       \-----P3
 */
package model.graph;

import model.User;
import model.Vehicle;
import model.Visit;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import simulation.Method;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GraphRTV {

    public List<List<Visit>> getFeasibleTrips() {
        return feasibleTrips;
    }

    public void setFeasibleTrips(List<List<Visit>> feasibleTrips) {
        this.feasibleTrips = feasibleTrips;
    }

    // Populate list of feasible trips with current trips
    private List<List<Visit>> feasibleTrips;

    private SimpleGraph<Object, DefaultEdge> graphRTV;

    private int vehicleCapacity;


    private List<Vehicle> listVehicles;

    private List<User> allRequests;


    private GraphRV graphRV;

    public Set<Object> vertexSet() {
        return graphRTV.vertexSet();
    }

    public int getVisitCount() {
        return (int) graphRTV.vertexSet().stream().filter(o -> o instanceof Visit).count();
    }

    public int getVisitCountSetVertex() {
        return graphRTV.vertexSet().stream().filter(o -> o instanceof Visit).collect(Collectors.toSet()).size();
    }

    public int getVehicleCapacity() {
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

    public GraphRTV(GraphRV graphRV, int vehicleCapacity, List<User> allRequests, List<Vehicle> listVehicles) {

        // Populate list of feasible trips with current trips
        this.feasibleTrips = new ArrayList<>();
        this.vehicleCapacity = vehicleCapacity;
        this.graphRTV = new SimpleGraph<>(DefaultEdge.class);
        this.allRequests = allRequests;
        this.listVehicles = listVehicles;
        this.graphRV = graphRV;

        // Add the data of current trips in the RTV graph
        long start = System.nanoTime();
        initDataStructures(allRequests, listVehicles, graphRTV, feasibleTrips);
        System.out.println(String.format(" - Initializing data structures (%.2f sec)", (System.nanoTime() - start) / 1000000000.0));

        // this.printFeasibleTrips("\n##################### Current visits ##############################");

        // Create request-trip-vehicle (RTV) structure
        start = System.nanoTime();
        this.buildGraph();
        System.out.println(String.format(" - Building graph (%.2f sec)", (System.nanoTime() - start) / 1000000000.0));

        //this.printFeasibleTrips("\n##################### Current visits + RTV visits #################");

    }

    private void initDataStructures(List<User> requests, List<Vehicle> vehicles, SimpleGraph<Object, DefaultEdge> graphRTV, List<List<Visit>> feasibleTrips) {

        for (int i = 0; i < vehicleCapacity; i++) {
            feasibleTrips.add(new ArrayList<>());
        }

        // Populate graph with request vertex
        for (User request : requests) {
            graphRTV.addVertex(request);
        }

        // Add current visits
        for (Vehicle vehicle : vehicles) {

            graphRTV.addVertex(vehicle);

            if (vehicle.isServicing()) {

                // Add RTV edges for current trips
                if (!vehicle.getVisit().getRequests().isEmpty()) {

                    // All requests not picked up by vehicle also integrate RTV graph
                    for (User user : vehicle.getVisit().getRequests()) {
                        graphRTV.addVertex(user);
                    }

                    System.out.println(String.format("Adding %s to level %d -- visit = %s", vehicle, vehicle.getVisit().getRequests().size() - 1, vehicle.getVisit()));

                    addRequestTripVehicleEdges(graphRTV, vehicle, vehicle.getVisit().getRequests(), vehicle.getVisit());
                    feasibleTrips.get(Math.max(vehicle.getVisit().getRequests().size() - 1, 0)).add(vehicle.getVisit());
                }
            }
        }
    }

    private void addRequestTripVehicleEdges(
            SimpleGraph<Object, DefaultEdge> graphRTV,
            Vehicle vehicle,
            Set<User> requests,
            Visit visit) {

        // Add best visit to RTV graph
        graphRTV.addVertex(visit);
        graphRTV.addEdge(visit, vehicle);

        for (User request : requests) {
            graphRTV.addEdge(request, visit);
        }

        assert visit.getRequests() != null && !visit.getRequests().isEmpty() : String.format("VISIT %s", visit);
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

    public void buildGraph() {

        /*
        List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel2 = new ArrayList<>();
        for (int i = 0; i < vehicleCapacity; i++) {
            feasibleVisitsCurrentVehicleAtLevel2.add(new ArrayList<>());
        }

        this.listVehicles.stream()
                .map(this::getFeasibleTripsVehicle)
                .collect(
                        ArrayList::new,
                        (listOfLevels, visits) -> {
                            for (int i = 0; i < visits.size(); i++) {
                                feasibleVisitsCurrentVehicleAtLevel2.get(i).addAll(visits.get(i));
                            }
                            }, (lists, lists2) -> { });*/

        for (Vehicle vehicle : this.listVehicles) {

            List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel = getFeasibleTripsVehicle(vehicle);

            for (int i = 0; i < vehicle.getCapacity(); i++) {
                this.feasibleTrips.get(i).addAll(feasibleVisitsCurrentVehicleAtLevel.get(i));
            }
        }
    }

    /**!
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

     * @param vehicle
     * @return
     */
    private List<List<Visit>> getFeasibleTripsVehicle(Vehicle vehicle) {
        // Feasible visits of size k={1,2,3, ..., capacity(vehicle)}
        List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel = new ArrayList<>();
        for (int i = 0; i < vehicle.getCapacity(); i++) {
            feasibleVisitsCurrentVehicleAtLevel.add(new ArrayList<>());
        }

        //**********************************************************************************************************
        // Adding feasible visits of size = 1 **********************************************************************
        //**********************************************************************************************************
        for (DefaultEdge edge : this.graphRV.edgesOf(vehicle)) {

            User request = (User) this.graphRV.getEdgeTarget(edge);

            // Try ALL insertions of request in vehicle visit sequence
            addRTVEdgeAtLevel(vehicle, new HashSet<>(Arrays.asList(request)), feasibleVisitsCurrentVehicleAtLevel, 0);
        }

        //**********************************************************************************************************
        // Adding feasible visits of size = 2 **********************************************************************
        //**********************************************************************************************************
        for (int i = 0; i < feasibleVisitsCurrentVehicleAtLevel.get(0).size() - 1; i++) {
            for (int j = i + 1; j < feasibleVisitsCurrentVehicleAtLevel.get(0).size(); j++) {

                Visit visit1 = feasibleVisitsCurrentVehicleAtLevel.get(0).get(i);
                Visit visit2 = feasibleVisitsCurrentVehicleAtLevel.get(0).get(j);


                User request1 = visit1.getRequests().iterator().next();
                User request2 = visit2.getRequests().iterator().next();

                assert visit1.getRequests().size() == 1 : String.format("More than 1 request: %s", visit1.getRequests());
                assert visit2.getRequests().size() == 1 : String.format("More than 1 request: %s", visit2.getRequests());

                //TODO visit1 and visit2 share the same request when vehicle has already serviced other users. Fixed using SET

                // If RV edge exists, there is a possible trip including request1 and request2
                if (this.graphRV.getEdge(request1, request2) != null) {

                    Set<User> requests = new HashSet<>(visit1.getRequests());
                    requests.addAll(visit2.getRequests());
                    addRTVEdgeAtLevel(vehicle, requests, feasibleVisitsCurrentVehicleAtLevel, 1);
                }
            }
        }

        // Add TV edge (create visit with passengers only)
        addVisitWithPassengers(vehicle, feasibleVisitsCurrentVehicleAtLevel);
        return feasibleVisitsCurrentVehicleAtLevel;
    }

    private void addVisitWithPassengers(Vehicle vehicle, List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel) {
        if (vehicle.isCarryingPassengers()){
            Visit visitWithoutRequests = Method.getBestVisitFor(vehicle, new HashSet<>());

            // Add best visit to RTV graph
            assert visitWithoutRequests != null : String.format("Cannot find visit for vehicle %s (carrying passengers) with current visit = %s", vehicle, vehicle.getVisit());

            feasibleVisitsCurrentVehicleAtLevel.get(0).add(visitWithoutRequests);
            graphRTV.addVertex(visitWithoutRequests);
            graphRTV.addEdge(visitWithoutRequests, vehicle);
        }
    }

    private void addRTVEdgeAtLevel(Vehicle vehicle, Set<User> requests, List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel, int level) {
        // System.out.println(String.format("Best visit: %s: requests = %s", vehicle, requests));
        Visit visit = Method.getBestVisitFor(vehicle, requests);
        //System.out.println("Best:" + visit);

        if (visit != null) {
            addRequestTripVehicleEdges(this.graphRTV, vehicle, requests, visit);
            // System.out.println(String.format("2 - Adding %s to level %d -- visit = %s", visit.getVehicle(), visit.getRequests().size() - 1, visit));
            feasibleVisitsCurrentVehicleAtLevel.get(level).add(visit);
        }
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

    public Set<DefaultEdge> edgesOf(Object o) {
        return graphRTV.edgesOf(o);
    }

    public boolean removeVertex(Object o) {
        return graphRTV.removeVertex(o);
    }

    public Object getEdgeTarget(DefaultEdge edge) {
        return graphRTV.getEdgeTarget(edge);
    }
}

