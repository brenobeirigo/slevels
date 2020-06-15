package simulation;

import config.Rebalance;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeMiddle;
import org.jgrapht.WeightedGraph;
import org.jgrapht.graph.*;
import org.paukov.combinatorics.CombinatoricsVector;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;

import java.util.*;

import static org.paukov.combinatorics.CombinatoricsFactory.*;

public class SimulationRTV extends Simulation {


    /* RV, RTV */
    private int maxVehReqEdges;
    private int maxReqReqEdges;
    private int maxEdgesRTV;
    private int numberUsersPermute;
    private boolean findBestVisit;
    private int maxNumberPermutations;

    //TODO hot_PK_list

    /* Construct RTV simulation */
    public SimulationRTV(String methodName,
                         int initialFleet,
                         int vehicleMaxCapacity,
                         int maxRequestsIteration,
                         int timeWindow,
                         int timeHorizon,
                         boolean allowRebalancing,
                         int contractDuration,
                         boolean isAllowedToHire,
                         boolean isAllowedToLowerServiceLevel,
                         boolean sortWaitingUsersByClass,
                         String serviceRateScenarioLabel,
                         String segmentationScenarioLabel,
                         Rebalance rebalance) {


        // Build generic Simulation object
        super(initialFleet,
                vehicleMaxCapacity,
                maxRequestsIteration,
                timeWindow,
                timeHorizon,
                allowRebalancing,
                contractDuration,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                sortWaitingUsersByClass,
                rebalance);


        // Service rate and segmentation scenarios
        this.serviceRateScenarioLabel = serviceRateScenarioLabel;
        this.segmentationScenarioLabel = segmentationScenarioLabel;

        // Initialize solution
        sol = new Solution(
                methodName,
                initialFleet,
                maxRequestsIteration,
                vehicleMaxCapacity,
                timeWindow,
                timeHorizon,
                allowRebalancing,
                contractDuration,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                serviceRateScenarioLabel,
                segmentationScenarioLabel,
                rebalance);

        /* RV, RTV */
        maxVehReqEdges = 1000;
        maxReqReqEdges = 1000;
        maxEdgesRTV = 1000;

        /* Permutations */
        numberUsersPermute = 2;
        findBestVisit = false;
        maxNumberPermutations = 100;

    }

    public void updateRR(List<User> listRequests,
                         SimpleGraph<Object, DefaultEdge> graph,
                         int maxNumberOfEdges,
                         int maxVehicleCapacity) {

        for (int i = 0; i < listRequests.size() - 1; i++) {

            // Request r1 data
            User r1 = listRequests.get(i);
            Node pk1 = r1.getNodePk();
            Node dp1 = r1.getNodeDp();

            for (int j = i + 1; j < listRequests.size(); j++) {

                // Request r2 data
                User r2 = listRequests.get(j);
                Node pk2 = r2.getNodePk();
                Node dp2 = r2.getNodeDp();

                LinkedList<Node> seq1 = new LinkedList<>(Arrays.asList(pk1, pk2, dp1, dp2));
                LinkedList<Node> seq2 = new LinkedList<>(Arrays.asList(pk1, pk2, dp2, dp1));

                int delayR1R2 = Math.min(
                        Visit.isValidSequence(seq1, seq1.get(0).getDeparture(), seq1.get(0).getLoad(), maxVehicleCapacity),
                        Visit.isValidSequence(seq2, seq2.get(0).getDeparture(), seq2.get(0).getLoad(), maxVehicleCapacity)
                );

                if (delayR1R2 > 0) {
                    graph.addVertex(r1);
                    graph.addVertex(r2);
                    DefaultEdge edge = graph.addEdge(r1, r2);
                    //graph.setEdgeWeight(edge, delayR1R2);

                }

                LinkedList<Node> seq3 = new LinkedList<>(Arrays.asList(pk2, pk1, dp1, dp2));
                LinkedList<Node> seq4 = new LinkedList<>(Arrays.asList(pk2, pk1, dp2, dp1));

                int delayR2R1 = Math.min(
                        Visit.isValidSequence(seq3, seq3.get(0).getDeparture(), seq3.get(0).getLoad(), maxVehicleCapacity),
                        Visit.isValidSequence(seq4, seq4.get(0).getDeparture(), seq4.get(0).getLoad(), maxVehicleCapacity)
                );
                if (delayR2R1 > 0) {
                    DefaultEdge edge = graph.addEdge(r1, r2);
                    //graph.setEdgeWeight(edge, delayR1R2);
                }


                //TODO undirected edge r1-r2
                // At least one sequence is feasible


                // At least one sequence is feasible


                // Maximum number of connections is reached
                //if (rv.get(r1.getId()).size() >= maxNumberOfEdges) {
                //    break;
                //}

            }
        }
    }

    public LinkedList<Node> addLastVisitedAndMiddleNodesToStart(List<Node> sequence, Vehicle vehicle) {
        // Sequence has to start from last vehicle visited node
        LinkedList<Node> sequenceWithVehicleNode = new LinkedList<>(sequence);

        Node middle = vehicle.getMiddleNode();
        // When vehicle is rebalancing, middle node has to be included
        if (vehicle.isRebalancing()) {

            // If middle node does not exist, discard sequence
            // TODO add the rebalancing target?
            if (middle != null) {
                sequenceWithVehicleNode.add(0, middle);
            } else {
                return null;
            }

        } else if (vehicle.isServicing()) {

            // When next node in visit sequence is not the node in potential visits, vehicle has to break path
            if (vehicle.getVisit().getTargetNode() != sequenceWithVehicleNode.getFirst()) {

                // TODO If next in sequence is middle, is it worth it breaking again???
                assert !(vehicle.getVisit().getTargetNode() instanceof NodeMiddle): "First is middle!!!!" + vehicle.getVisit();

                if (middle != null) {
                    sequenceWithVehicleNode.add(0, middle);
                } else {
                    // Cannot break, vehicle will be available in the next iteration
                    return null;
                }
            }
        }

        sequenceWithVehicleNode.add(0, vehicle.getLastVisitedNode());


        return sequenceWithVehicleNode;
    }

    /**
     * In RV graph, create edges connecting vehicles to requests ( <= maxNumberOfEdges)
     *
     * @param listVehicles     List of all vehicles
     * @param listRequests     List of all requests
     * @param maxNumberOfEdges Max number of vehicles that can serve an user
     */
    public void updateRV(List<Vehicle> listVehicles,
                         List<User> listRequests,
                         SimpleGraph<Object, DefaultEdge> graphRV,
                         int maxNumberOfEdges) {

        // Check if vehicle can visit request
        for (User request : listRequests) {

            // Count of edges connecting requests to candidate vehicles
            int edgesRV = 0;

            // Loop vehicles
            for (Vehicle vehicle : listVehicles) {

                // Stop connecting requests to vehicles if edge count was reached
                if (edgesRV++ >= maxNumberOfEdges) {
                    break;
                }
                Generator<Node> gen = getGeneratorOfNodeSequence(new HashSet<>(Arrays.asList(request)), vehicle);

                for (ICombinatoricsVector<Node> combinationUsersPickupsAndDeliveries : gen) {

                    List<Node> sequencePickupsAndDeliveries = combinationUsersPickupsAndDeliveries.getVector();

                    LinkedList<Node> sequenceFromVehiclePositionToLastDelivery = addLastVisitedAndMiddleNodesToStart(sequencePickupsAndDeliveries, vehicle);

                    if (sequenceFromVehiclePositionToLastDelivery == null)
                        continue;

                    int delay = Visit.isValidSequence(
                            sequenceFromVehiclePositionToLastDelivery,
                            vehicle.getDepartureCurrent(),
                            vehicle.getCurrentLoad(),
                            vehicle.getCapacity());


                    if (delay >= 0) {

                        // System.out.println(vehicle.getLastVisitedNode() + "(" +vehicle.getLastVisitedNode()+ ") - Departure: " + vehicle.getDepartureCurrent() + "(" + currentTime + "), delay=" + delay + " - seq:" + sequenceFromVehiclePositionToLastDelivery + " - Visit:" + vehicle.getVisit());

                        // Connect vehicle to request
                        graphRV.addEdge(vehicle, request);
                        // graphRV.setEdgeWeight(graphRV.addEdge(vehicle, request), delay);

                        // Stop after picking up
                        continue;
                    }
                }
            }
        }
    }

    /**
     * Pairwise graph of vehicles and requests. Combine vehicles and requests.
     *
     * @param listWaitingUsers
     * @param listVehicles
     * @return
     */
    public SimpleGraph<Object, DefaultEdge> getRVGraph(List<User> listWaitingUsers,
                                                       List<Vehicle> listVehicles,
                                                       int maxVehReqEdges,
                                                       int maxReqReqEdges) {


        // Create RV graph (r1, r2) and (v, r)
        SimpleGraph<Object, DefaultEdge> graphRV = new SimpleGraph<>(DefaultEdge.class);

        for (User u : listWaitingUsers) {
            graphRV.addVertex(u);
        }

        for (Vehicle v : listVehicles) {
            graphRV.addVertex(v);
        }

        // Add request-request edges
        updateRR(listWaitingUsers, graphRV, maxReqReqEdges, vehicleCapacity);

        // A request r and a vehicle v are connected if the request can be served by the vehicle while satisfying the
        // constraints Z, as given by travel(v, r). Every vehicle is conected to "maxNumberOfEdges" users.
        updateRV(listVehicles, listWaitingUsers, graphRV, maxVehReqEdges);


        return graphRV;
    }

    public Generator getNodeSequenceGeneratorFromCombinedUsers(Visit visit1, Visit visit2, Vehicle vehicle) {
        List<User> users = new LinkedList<>();
        Set<Node> uniqueNodes = new HashSet<>();

        for (User u : visit1.getRequests()) {
            uniqueNodes.add(u.getNodePk());
            uniqueNodes.add(u.getNodeDp());
            //vector.addValue(u.getNodePk());
            //vector.addValue(u.getNodeDp());
        }

        for (User u : visit2.getRequests()) {
            uniqueNodes.add(u.getNodePk());
            uniqueNodes.add(u.getNodeDp());
            //vector.addValue(u.getNodePk());
            //vector.addValue(u.getNodeDp());
        }

        if (vehicle.getVisit() != null && !vehicle.isRebalancing()) {

            for (Node plannedStop : vehicle.getVisit().getSequenceVisits()) {
                uniqueNodes.add(plannedStop);
            }

            if (vehicle.getVisit().getSequenceVisits().getFirst() instanceof NodeMiddle) {
                System.out.println(vehicle.getMiddleNode());
                System.out.println("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX" + vehicle.getVisit());
            }
        }

        ICombinatoricsVector<Node> vector = new CombinatoricsVector<>(uniqueNodes);
        Generator<Node> gen = createPermutationGenerator(vector);
        return gen;
    }

    /**
     * Generator of all combinations of stops from the request list and vehicle stops (passenger delivery nodes).
     *
     * @param requests
     * @param vehicle
     * @return
     */
    public Generator getGeneratorOfNodeSequence(Set<User> requests, Vehicle vehicle) {

        // TODO here we assume the requests include vehicle requests to
        ICombinatoricsVector<Node> vector = new CombinatoricsVector<>();
        for (User user : requests) {
            vector.addValue(user.getNodePk());
            vector.addValue(user.getNodeDp());
        }

        // Passengers have been picked up
        if (vehicle.isServicing()) {
            for (User passenger : vehicle.getVisit().getPassengers()) {
                vector.addValue(passenger.getNodeDp());
            }
        }

        Generator<Node> gen = createPermutationGenerator(vector);
        return gen;
    }


    private void addRequestTripVehicleEdges(WeightedGraph<Object, DefaultWeightedEdge> graphRTV, Vehicle vehicle, Set<User> requests, Visit visit) {
        // Add best visit to RTV graph
        graphRTV.addVertex(visit);
        graphRTV.addEdge(visit, vehicle);

        for (User request : requests) {
            graphRTV.addEdge(request, visit);
        }
        assert visit.getRequests() != null && !visit.getRequests().isEmpty() : String.format("VISIT %s", visit);
    }

    private Visit getBestVisitFor(Vehicle vehicle, Set<User> requests) {

        Generator<Node> gen = getGeneratorOfNodeSequence(requests, vehicle);

        // A single request can be inserted in a vehicle in multiple ways. Only the best (lowest delay) visit is
        // inserted in the RTV graph.

        Visit visit = null;
        int lowestDelay = Integer.MAX_VALUE;
        for (ICombinatoricsVector<Node> combination : gen) {

            List<Node> sequence = combination.getVector();

            LinkedList<Node> sequenceFromVehiclePositionToLastDelivery = addLastVisitedAndMiddleNodesToStart(sequence, vehicle);

            if (sequenceFromVehiclePositionToLastDelivery == null)
                continue;

            int delay = Visit.isValidSequence(
                    sequenceFromVehiclePositionToLastDelivery,
                    vehicle.getDepartureCurrent(),
                    vehicle.getCurrentLoad(),
                    vehicle.getCapacity()
            );

            if (delay >= 0 && delay < lowestDelay) {

                lowestDelay = delay;

                // Remove last visited node from sequence
                sequenceFromVehiclePositionToLastDelivery.poll();

                // Setup new visit
                visit = new Visit(sequenceFromVehiclePositionToLastDelivery, delay);
                //System.out.println("     # comb " + sequence + " - " + delay + " = " + visit);
            }
        }

        if (visit != null) {
            // Finish visit configuration
            visit.setVehicle(vehicle);
            visit.getRequests().addAll(requests);

            if (vehicle.getVisit() != null) {
                visit.getRequests().addAll(vehicle.getVisit().getRequests());
                visit.setPassengers(vehicle.getVisit().getPassengers());
            }
        }

        return visit;
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
     * @param graphRV
     * @param listVehicles
     * @return
     */
    public WeightedGraph<Object, DefaultWeightedEdge> getRTVGraph(
            SimpleGraph<Object, DefaultEdge> graphRV,
            List<User> listRequests,
            List<Vehicle> listVehicles,
            List<TreeSet<Visit>> allVisits) {

        WeightedGraph<Object, DefaultWeightedEdge> graphRTV = new WeightedMultigraph<>(DefaultWeightedEdge.class);

        // Populate graph with request vertex
        for (User user : listRequests) {
            graphRTV.addVertex(user);
        }

        for (Vehicle vehicle : listVehicles) {
            graphRTV.addVertex(vehicle);

            // All requests not picked up by vehicle also integrate RTV graph
            if (vehicle.isServicing())
                for (User user : vehicle.getVisit().getRequests()) {
                    graphRTV.addVertex(user);
                }

            // Feasible visits of size k={1,2,3, ..., capacity(vehicle)}
            List<List<Visit>> feasibleVisitsCurrentVehicleAtLevel = new ArrayList();
            for (int i = 0; i < vehicle.getCapacity(); i++) {
                feasibleVisitsCurrentVehicleAtLevel.add(new ArrayList<>());
            }

            // System.out.println("VEHICLE = " + vehicle);

            //**********************************************************************************************************
            // Adding feasible visits of size = 1 **********************************************************************
            //**********************************************************************************************************
            for (DefaultEdge edge : graphRV.edgesOf(vehicle)) {

                User request = (User) graphRV.getEdgeTarget(edge);

                // Try ALL insertions of request in vehicle visit sequence

                Visit visit = getBestVisitFor(vehicle, new HashSet<>(Arrays.asList(request)));

                if (visit != null) {
                    addRequestTripVehicleEdges(graphRTV, vehicle, new HashSet<>(Arrays.asList(request)), visit);
                    feasibleVisitsCurrentVehicleAtLevel.get(0).add(visit);
                }
            }

            /*
            // Print visits
            for (Visit visit : feasibleVisitsCurrentVehicleAtLevel.get(0)) {
                User request1 = visit.getRequests().iterator().next();
                System.out.println(request1 + ") " + visit + " = " + vehicle);
            }*/


            //**********************************************************************************************************
            // Adding feasible visits of size = 2 **********************************************************************
            //**********************************************************************************************************
            for (int i = 0; i < feasibleVisitsCurrentVehicleAtLevel.get(0).size() - 1; i++) {
                for (int j = i + 1; j < feasibleVisitsCurrentVehicleAtLevel.get(0).size(); j++) {

                    Visit visit1 = feasibleVisitsCurrentVehicleAtLevel.get(0).get(i);
                    Visit visit2 = feasibleVisitsCurrentVehicleAtLevel.get(0).get(j);

                    //TODO not really... There can be multiple requests if vehicle has already serviced someone before
                    // There is only one user in level 0 visits
                    User request1 = visit1.getRequests().iterator().next();
                    User request2 = visit2.getRequests().iterator().next();

                    //TODO visit1 and visit2 share the same request when vehicle has already serviced other users. Fixed using SET


                    // If RV edge exists, it is possible to pickup u1 and u2

                    if (graphRV.getEdge(request1, request2) != null) {

                        System.out.println(String.format("%d[%s] %d[%s] (%d)", i, request1, j, request2, feasibleVisitsCurrentVehicleAtLevel.get(0).size()));

                        // System.out.println("     # CAR " + vehicle + " - {" + u1 + ", " + u2 + "}");

                        // Generator of Node sequences
//                        System.out.println(
//                                String.format(
//                                        "Generator 1 - (%s) %s - (%s) %s",
//                                        request1,
//                                        visit1.getSequenceVisits(),
//                                        request2,
//                                        visit2.getSequenceVisits()));

                        Set<User> requests = new HashSet<>(visit1.getRequests());
                        requests.addAll(visit2.getRequests());

                        System.out.println(String.format("Best visit: %s: requests = %s", vehicle, requests));
                        Visit visit = getBestVisitFor(vehicle, requests);
                        System.out.println("Best:" + visit);

                        if (visit != null) {
                            addRequestTripVehicleEdges(graphRTV, vehicle, requests, visit);
                            feasibleVisitsCurrentVehicleAtLevel.get(2).add(visit);
                        }
                    }
                }
            }

            for (int i = 0; i < 2; i++) {
                allVisits.get(i).addAll(feasibleVisitsCurrentVehicleAtLevel.get(i));
            }
        }

        return graphRTV;
    }

    /**
     * Greedy assign of users to vehicles (preference to larger trips)
     * Starting from the largest vehicle capacity, tries to realize candidate visits.
     * Repeats the process until capacity 1 is reached and all visits have been evaluted.
     *
     * @param visitsVehicleCapacity Map of ordered visits associated to a trip length (<=max. vehicle capacity)
     * @return Set of users assigned
     */
    public Set<User> greedyAssignment(
            WeightedGraph<Object, DefaultWeightedEdge> graphRTV,
            List<TreeSet<Visit>> visitsVehicleCapacity) {

        //System.out.println("GREEDY");

        // Set of requests scheduled to vehicles
        Set<User> requestOk = new HashSet<>();

        // Set of vehicles assigned to visits
        Set<Vehicle> vehicleOk = new HashSet<>();

        // Set of visits chosen in round
        Set<Visit> greedy = new HashSet<>();

        // Loop visits starting from the longest (combining more requests)
        for (int k = visitsVehicleCapacity.size() - 1; k >= 0; k--) {

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

                    // Remove visit from graph
                    graphRTV.removeVertex(candidateVisit);

                    // Get vehicle associated to visit
                    Vehicle vehicle = candidateVisit.getVehicle();

                    // Jump to next visit if a visit was already assigned to a vehicle
                    if (vehicleOk.contains(vehicle)) {
                        continue;
                    }

                    // Get requests associated to visit
                    Set<User> requests = candidateVisit.getRequests();

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

                    // Remove vehicle and users matched from graph
                    for (User user : requests) {
                        graphRTV.removeVertex(user);
                    }
                    graphRTV.removeVertex(vehicle);

                    // #################################################################################################
                    // ######## Materialize visit ######################################################################
                    // #################################################################################################

                    System.out.println(candidateVisit);
                    setup(candidateVisit);
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
        SimpleGraph<Object, DefaultEdge> graphRV = getRVGraph(
                setWaitingUsers,
                listVehicles,
                maxVehReqEdges,
                maxReqReqEdges
        );

        List<TreeSet<Visit>> allVisits = new ArrayList<>();
        allVisits.add(new TreeSet<>());
        allVisits.add(new TreeSet<>());

        // Create request-trip-vehicle (RTV) structure
        WeightedGraph<Object, DefaultWeightedEdge> graphRTV = getRTVGraph(
                graphRV,
                setWaitingUsers,
                listVehicles,
                allVisits
        );

        Set<User> setScheduledUsers = greedyAssignment(graphRTV, allVisits);

        return setScheduledUsers;
    }
}