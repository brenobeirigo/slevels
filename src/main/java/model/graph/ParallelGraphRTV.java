package model.graph;

import com.google.common.collect.Sets;
import dao.Dao;
import dao.Logging;
import helper.Runtime;
import model.Vehicle;
import model.demand.User;
import model.visit.VisitObj;
import org.jgrapht.graph.DefaultWeightedEdge;
import simulation.Environment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ParallelGraphRTV implements GraphRTV {

    private final Environment environment;
    // Populate list of feasible trips with current trips
    private List<List<VisitObj>> feasibleTrips;
    private int maxVehicleCapacity;
    private Set<Vehicle> listVehicles;
    private Set<User> allRequests;
    private GraphRV graphRV;
    private long timeout;
    public ConcurrentHashMap.KeySetView<VisitObj, Boolean> allVisits;

    public Map<Vehicle, Set<VisitObj>> getVehicleVisitsMap() {
        return mapVehicleVisits;
    }

    public ConcurrentHashMap<Vehicle, Set<VisitObj>> mapVehicleVisits;
    public ConcurrentHashMap<User, Set<VisitObj>> mapUserVisits;

    public Map<User, Set<VisitObj>> getUserVisitsMap() {
        return mapUserVisits;
    }

    public Map<User, Set<Vehicle>> getUserVehiclesMap() {
        return mapUserVehicles;
    }

    public ConcurrentHashMap<User, Set<Vehicle>> mapUserVehicles;
    private int count;


    @Override
    public Map<Integer, Integer> getPickupLocationCandidateVehicleCountMap() {
        Map<Integer, Integer> pickupLocationCandidateVehicleCountMap = new HashMap<>();
        for (Map.Entry<User, Set<Vehicle>> e : mapUserVehicles.entrySet()) {
            pickupLocationCandidateVehicleCountMap.putIfAbsent(e.getKey().getNodePk().getNetworkId(), 0);
            pickupLocationCandidateVehicleCountMap.computeIfPresent(e.getKey().getNodePk().getNetworkId(), (nid, count) -> count + e.getValue().size());
        }
        return pickupLocationCandidateVehicleCountMap;
    }

    @Override
    public GraphRV getGraphRV() {
        return graphRV;

    }

    public ParallelGraphRTV(Set<User> allRequests, Set<Vehicle> listVehicles, int maxVehicleCapacity, double timeout, int maxVehReqEdges, int maxReqReqEdges, Environment env, int currentTime) {
        Logging.logger.info("{}", String.format("# Matching - RTV - Graph (VR=%d,RR=%d) - #Requests: %d  / #Vehicles: %d (%d) - timeout: %.2f sec", maxVehReqEdges, maxReqReqEdges, allRequests.size(), listVehicles.size(), maxVehicleCapacity, timeout));
        this.timeout = (long) (timeout * 1000000000);
        this.environment = env;
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
        this.graphRV = new GraphRV(allRequests, listVehicles, maxVehicleCapacity, maxVehReqEdges, maxReqReqEdges, env, currentTime);
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


        Logging.logger.info("# Matching - RTV");
        Logging.logger.info("    - {} s - RV Creation        ({})", Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_CREATE_RV), this.graphRV);
        Logging.logger.info("    - {} s - RTV Initialization ", Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_INIT));
        Logging.logger.info("    - {} s - RTV Building       ({})", Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_BUILDING_TOTAL), this.getSummaryFeasibleTripsLevel());
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
        Map<Vehicle, List<List<VisitObj>>> allFeasibleTrips = this.listVehicles.stream().parallel().collect(Collectors.toMap(o -> o, this::getFeasibleTripsVehicle));
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_RTV_FEASIBLE_TRIPS);

        Dao.getInstance().getRunTimes().startTimerFor(Runtime.TIME_RTV_POPULATE_GRAPH);
        allFeasibleTrips.forEach((vehicle, levelVisits) -> {

            //**********************************************************************************************************
            // Add visit where there are no requests *******************************************************************
            //**********************************************************************************************************

            VisitObj noRequestsVisit = null;

            if (vehicle.isParked() || vehicle.isRebalancing()) {
                // Vehicle continues its path (dummy "do nothing" visit i.e., visit is null)
                // TODO passthrough visit (keep current visit)
                noRequestsVisit = vehicle.getVisitStop();

            } else if (vehicle.isCruisingToPickup()) {
                // Interrupt cruising, drop requests, and rebalance to closest middle
                noRequestsVisit = this.environment.getVisitRelocationToMiddle(vehicle);

            } else if (vehicle.isCarryingPassengers()) {
                // Only passengers are kept
                noRequestsVisit = Environment.getBestVisitFromPDPermutationsSummarized(vehicle, new HashSet<>(), environment);
            }

            assert noRequestsVisit != null;
            // Quick fix
//            if (noRequestsVisit != null){
//                this.feasibleTrips.get(0).add(noRequestsVisit);
//                this.computeVisit(noRequestsVisit);
//            }


            for (int i = 0; i < levelVisits.size(); i++) {
                this.feasibleTrips.get(i).addAll(levelVisits.get(i));
            }
        });
        Dao.getInstance().getRunTimes().endTimerFor(Runtime.TIME_RTV_POPULATE_GRAPH);

        Logging.logger.info("    - {} s - Finding feasible trips", Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_FEASIBLE_TRIPS));
        Logging.logger.info("    - {} s - Populating graph", Dao.getInstance().getRunTimes().getExecutionTimeSecFor(Runtime.TIME_RTV_POPULATE_GRAPH));
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
            if (System.nanoTime() - startTime >= timeout) return feasibleVisitsAtLevel;

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
                    if (System.nanoTime() - startTime >= timeout) return feasibleVisitsAtLevel;

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
            if (System.nanoTime() - startTime >= timeout) return feasibleVisitsAtLevel;

            List<VisitObj> feasibleVisitsPreviousLevel = feasibleVisitsAtLevel.get(k - 1);
            Set<Set<User>> feasibleTripsPreviousLevel = feasibleTripsAtLevel.get(k - 1);

            for (int i = 0; i < feasibleVisitsPreviousLevel.size() - 1; i++) {
                for (int j = i + 1; j < feasibleVisitsPreviousLevel.size(); j++) {

                    Set<User> combinedTrip = Sets.union(feasibleVisitsPreviousLevel.get(i).getRequests(), feasibleVisitsPreviousLevel.get(j).getRequests());

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
     * A trip is feasible, if there is any visit where users can be successfully picked up and delivered.
     * Example:
     * Trip (u1, u2) is feasible if there is a feasible visit in:
     * - 1,1',2,2'
     * - 1,2,1',2'
     * ...
     * - 2,2',1,1'
     *
     * @param usersFeasibleTripsPreviousLevel Set of feasible trips of size $k-1$.
     * @param usersCandidateTripCurrentLevel  Candidate trip of size $k$.
     * @return true if all sub-trips of candidate trip are feasible (i.e., featured in previous level)
     */
    private boolean allSubTripsAreFeasible(Set<Set<User>> usersFeasibleTripsPreviousLevel, Set<User> usersCandidateTripCurrentLevel) {

        for (User request : usersCandidateTripCurrentLevel) {

            // Create sub-trip from candidate trip with one fewer request
            Set<User> subTrip = new HashSet<>(usersCandidateTripCurrentLevel);
            subTrip.remove(request);

            if (!usersFeasibleTripsPreviousLevel.contains(subTrip)) return false;
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
            VisitObj visitWithoutRequests = Environment.getBestVisitFromPDPermutationsSummarized(vehicle, new HashSet<>(), environment);

            // Add best visit to RTV graph
            assert visitWithoutRequests != null : String.format("Cannot find visit for vehicle %s (carrying passengers) with current visit = %s", vehicle, vehicle.getVisit());

            feasibleVisitsCurrentVehicleAtLevel.get(0).add(visitWithoutRequests);
            computeVisit(visitWithoutRequests);
        }
    }

    /**
     * Add RTV Edge with best visit found for vehicle and requests.
     *
     * @param vehicle
     * @param requests
     * @param feasibleVisitsCurrentVehicleAtLevel
     * @return True, if there is a feasible visit where requests can be picked up by the vehicle.
     */
    private boolean addRTVEdgeAtLevel(Vehicle vehicle, Set<User> requests, List<VisitObj> feasibleVisitsCurrentVehicleAtLevel) {
        VisitObj bestVisit = Environment.getBestVisitFromPDPermutationsSummarized(vehicle, requests, environment);

        if (bestVisit != null) {
            feasibleVisitsCurrentVehicleAtLevel.add(bestVisit);
            computeVisit(bestVisit);
            return true;
        }
        return false;
    }

    private void computeVisit(VisitObj visit) {
        allVisits.add(visit);
        mapVehicleVisits.get(visit.getVehicle()).add(visit);
        visit.genUserPickupDelayMap(environment);
        visit.getRequests().stream().forEach(user -> {
            mapUserVisits.get(user).add(visit);
            mapUserVehicles.get(user).add(visit.getVehicle());
        });
    }

    public Set<VisitObj> getListOfVisitsFromVehicle(Vehicle vehicle) {
        return mapVehicleVisits.get(vehicle);
    }

    public void printAllVisitsPerVehicle() {

        Map<Integer, Integer> pickupLocationCandidateVehicleCountMap = this.getPickupLocationCandidateVehicleCountMap();

        for (Map.Entry<Integer, Integer> e : pickupLocationCandidateVehicleCountMap.entrySet()) {
            Logging.logger.info("Network ID: {} - Accessible: {}", e.getKey(), e.getValue());
        }

        for (Map.Entry<Vehicle, Set<VisitObj>> e : this.mapVehicleVisits.entrySet()) {
            Logging.logger.info("----------------------------------------------------------------------");
            Logging.logger.info(e.getKey().toString());
            Logging.logger.info(e.getKey().getVisit().toString());


            for (VisitObj visit : e.getValue()) {

//                if (v.getTargetNode() != null) {
//                    int networkId = v.getTargetNode().getNetworkId();
//                    Logging.logger.info(" Target:" + v.getTargetNode() + "  -  " + networkId);
//                    int count = pickupLocationCandidateVehicleCountMap.getOrDefault(networkId, 0);
//                    Logging.logger.info("Count at :" + v.getTargetNode() + "(" + networkId + "): " + count);
//                }

//                Logging.logger.info("**********************************************************************");
//                VehicleState vSnap = new VehicleState(visit, Environment.rightTW,  Environment.timeHorizon, 0);
//                Logging.logger.info("  Construct:" + vSnap);
//                Logging.logger.info("----------------------------------------------------------------------");
//                //Logging.logger.info("       Post:" + VehicleState.realize(vSnap, Environment.timeWindowSec));
//                Logging.logger.info("----------------------------------------------------------------------");
//                VehicleState vSnapShort = VehicleState.getVisitState(visit, Environment.rightTW, Environment.timeWindowSec, Environment.timeHorizon);
//
//                Logging.logger.info("     Static:" + vSnapShort);
//                Logging.logger.info("----------------------------------------------------------------------");
//                Logging.logger.info("1xTW Static:" + VehicleState.realize(vSnapShort, Environment.rightTW, 1 * Environment.timeWindowSec, pickupLocationCandidateVehicleCountMap));
//                Logging.logger.info("----------------------------------------------------------------------");
//                Logging.logger.info("2xTW Static:" + VehicleState.realize(vSnapShort, Environment.rightTW, 2 * Environment.timeWindowSec, pickupLocationCandidateVehicleCountMap));
//                Logging.logger.info("----------------------------------------------------------------------");
//                Logging.logger.info("3xTW Static:" + VehicleState.realize(vSnapShort, Environment.rightTW, 3 * Environment.timeWindowSec, pickupLocationCandidateVehicleCountMap));
//                Logging.logger.info("----------------------------------------------------------------------");
//                Logging.logger.info("4xTW Static:" + VehicleState.realize(vSnapShort, Environment.rightTW,4 * Environment.timeWindowSec, pickupLocationCandidateVehicleCountMap));
            }
        }
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

    public double getWeightFromRequestVisitEdge(User request, VisitObj visit) {
        return visit.getUserPickupDelayMap(this.environment).get(request);
    }

    public Set<VisitObj> getListOfVisitsFromUser(User request) {
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

    public Set<VisitObj> getAllVisits() {
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

