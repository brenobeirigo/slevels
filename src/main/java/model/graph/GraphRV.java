package model.graph;

import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;
import simulation.Method;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GraphRV {


    private final List<User> allRequests;
    public int vehicleCapacity;
    SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV;

    public GraphRV(List<User> allRequests, List<Vehicle> listVehicles, int vehicleCapacity) {

        this.vehicleCapacity = vehicleCapacity;

        this.allRequests = allRequests;

        // Which requests can be cobined?
        // What happens with visits with passengers:
        graphRV = getRVGraph(
                allRequests,
                listVehicles,
                vehicleCapacity
        );
    }

    public GraphRV(List<User> allRequests, List<Vehicle> listVehicles, int vehicleCapacity, int maxEdgesRV) {

        this.vehicleCapacity = vehicleCapacity;

        this.allRequests = allRequests;

        // Which requests can be cobined?
        // What happens with visits with passengers:
        graphRV = getGraphRVParallel(
                allRequests,
                listVehicles,
                vehicleCapacity,
                maxEdgesRV
        );
    }

    /**
     * Sort RV edges according to pickup delays and keep only maxRVLinks
     *
     * @param maxRVLinks
     */
    public void keepFastestRVLinks(int maxRVLinks) {
        this.allRequests.forEach(request -> removeLongestDelayRVLinks(maxRVLinks, request));
    }

    public void updateRR(List<User> listRequests,
                         SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV,
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
                LinkedList<Node> seq3 = new LinkedList<>(Arrays.asList(pk2, pk1, dp1, dp2));
                LinkedList<Node> seq4 = new LinkedList<>(Arrays.asList(pk2, pk1, dp2, dp1));

                for (LinkedList<Node> seq : Arrays.asList(seq1, seq2, seq3, seq4)) {
                    int delay = Visit.isValidSequence(seq, seq.get(0).getDeparture(), seq.get(0).getLoad(), maxVehicleCapacity);
                    if (delay > 0) {
                        graphRV.addEdge(r1, r2);
                        break;
                    }
                }
            }
        }
    }

    public List<EdgeRV> getRVEdge(int i, List<User> listRequests,
                                  List<Vehicle> listVehicles,
                                  int maxVehicleCapacity,
                                  int maxRVEdges) {

        List<EdgeRV> edges = new LinkedList<>();

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // RR EDGES ////////////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
            LinkedList<Node> seq3 = new LinkedList<>(Arrays.asList(pk2, pk1, dp1, dp2));
            LinkedList<Node> seq4 = new LinkedList<>(Arrays.asList(pk2, pk1, dp2, dp1));

            for (LinkedList<Node> seq : Arrays.asList(seq1, seq2, seq3, seq4)) {
                int delay = Visit.isValidSequence(seq, seq.get(0).getDeparture(), seq.get(0).getLoad(), maxVehicleCapacity);
                if (delay >= 0) {
                    edges.add(new EdgeRV(delay, r1, r2));
                    break;
                }
            }
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // RV EDGES ////////////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        List<EdgeRV> edgesRV = new ArrayList<>();

        // Loop vehicles
        for (Vehicle vehicle : listVehicles) {

            // Try to find at least ONE way pickup request
            EdgeRV rv = createEdgeRV(r1, vehicle);
            if (rv != null) {
                edgesRV.add(rv);
            }
        }

        if (edgesRV.size() > maxRVEdges) {
            Collections.sort(edgesRV);
            edges.addAll(edgesRV.subList(0, maxRVEdges));

        } else {
            edges.addAll(edgesRV);
        }

        return edges;
    }

    /**
     * In RV graph, create edges connecting vehicles to requests ( <= maxNumberOfEdges)
     *
     * @param listVehicles List of all vehicles
     * @param listRequests List of all requests
     */
    public void updateRV(List<Vehicle> listVehicles,
                         List<User> listRequests,
                         SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV) {


        Set<Integer> lowestDelays = new HashSet<>();

        // Check if vehicle can visit request
        for (User request : listRequests) {

            // Loop vehicles
            for (Vehicle vehicle : listVehicles) {

                // Try to find at least ONE way pickup request
                createEdge(graphRV, request, vehicle);

            }
        }
    }

    private void createEdge(SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV, User request, Vehicle vehicle) {

        Generator<Node> gen = Method.getGeneratorOfNodeSequence(new HashSet<>(Collections.singletonList(request)), vehicle);

        for (ICombinatoricsVector<Node> combinationUsersPickupsAndDeliveries : gen) {

            List<Node> sequencePickupsAndDeliveries = combinationUsersPickupsAndDeliveries.getVector();

            LinkedList<Node> sequenceFromVehiclePositionToLastDelivery = Method.addLastVisitedAndMiddleNodesToStart(sequencePickupsAndDeliveries, vehicle);

            if (sequenceFromVehiclePositionToLastDelivery == null)
                continue;

            int delay = Visit.isValidSequence(
                    sequenceFromVehiclePositionToLastDelivery,
                    vehicle.getDepartureCurrent(),
                    vehicle.getCurrentLoad(),
                    vehicle.getCapacity());


            if (delay >= 0) {

                // Connect vehicle to request
                DefaultWeightedEdge edge = graphRV.addEdge(vehicle, request);
                graphRV.setEdgeWeight(edge, delay);

                // If RV exists, there is at least ONE way to pickup up the request.
                // The BEST way will be generated by the RTV graph.
                break;

                // System.out.println(vehicle.getLastVisitedNode() + "(" +vehicle.getLastVisitedNode()+ ") - Departure: " + vehicle.getDepartureCurrent() + "(" + currentTime + "), delay=" + delay + " - seq:" + sequenceFromVehiclePositionToLastDelivery + " - Visit:" + vehicle.getVisit());
            }
        }
        // Impossible to create edge
    }

    private EdgeRV createEdgeRV(User request, Vehicle vehicle) {

        Generator<Node> gen = Method.getGeneratorOfNodeSequence(new HashSet<>(Collections.singletonList(request)), vehicle);

        for (ICombinatoricsVector<Node> combinationUsersPickupsAndDeliveries : gen) {

            List<Node> sequencePickupsAndDeliveries = combinationUsersPickupsAndDeliveries.getVector();

            LinkedList<Node> sequenceFromVehiclePositionToLastDelivery = Method.addLastVisitedAndMiddleNodesToStart(sequencePickupsAndDeliveries, vehicle);

            if (sequenceFromVehiclePositionToLastDelivery == null)
                continue;

            int delay = Visit.isValidSequence(
                    sequenceFromVehiclePositionToLastDelivery,
                    vehicle.getDepartureCurrent(),
                    vehicle.getCurrentLoad(),
                    vehicle.getCapacity());


            if (delay >= 0) {

                // Connect vehicle to request
                return new EdgeRV(delay, vehicle, request);
                // If RV exists, there is at least ONE way to pickup up the request.
                // The BEST way will be generated by the RTV graph.


                // System.out.println(vehicle.getLastVisitedNode() + "(" +vehicle.getLastVisitedNode()+ ") - Departure: " + vehicle.getDepartureCurrent() + "(" + currentTime + "), delay=" + delay + " - seq:" + sequenceFromVehiclePositionToLastDelivery + " - Visit:" + vehicle.getVisit());
            }
        }
        return null;
    }

    /**
     * Pairwise graph of vehicles and requests. Combine vehicles and requests.
     *
     * @param listWaitingUsers
     * @param listVehicles
     * @return
     */
    public SimpleWeightedGraph<Object, DefaultWeightedEdge> getRVGraph(List<User> listWaitingUsers,
                                                                       List<Vehicle> listVehicles,
                                                                       int vehicleCapacity) {


        // Create RV graph (r1, r2) and (v, r)
        SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        // Populating RV grapht
        for (User user : listWaitingUsers) {
            graphRV.addVertex(user);
        }

        for (Vehicle vehicle : listVehicles) {
            graphRV.addVertex(vehicle);
        }

        // Add request-request edges
        updateRR(listWaitingUsers, graphRV, vehicleCapacity);

        // A request r and a vehicle v are connected if the request can be served by the vehicle while satisfying the
        // constraints Z, as given by travel(v, r). Every vehicle is connected to "maxNumberOfEdges" users.
        updateRV(listVehicles, listWaitingUsers, graphRV);

        return graphRV;
    }

    /**
     * Pairwise graph of vehicles and requests. Combine vehicles and requests.
     *
     * @param listWaitingUsers
     * @param listVehicles
     * @return
     */
    public SimpleWeightedGraph<Object, DefaultWeightedEdge> getGraphRVParallel(List<User> listWaitingUsers,
                                                                               List<Vehicle> listVehicles,
                                                                               int vehicleCapacity,
                                                                               int maxEdgesRV) {


        // Create RV graph (r1, r2) and (v, r)
        SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        // Populating RV grapht
        for (User user : listWaitingUsers) {
            graphRV.addVertex(user);
        }

        for (Vehicle vehicle : listVehicles) {
            graphRV.addVertex(vehicle);
        }

        IntStream.range(0, listWaitingUsers.size()).parallel()
                .mapToObj(value -> getRVEdge(value, listWaitingUsers, listVehicles, vehicleCapacity, maxEdgesRV))
                .collect(HashSet<EdgeRV>::new, HashSet::addAll, HashSet::addAll)
                .forEach(o -> {
                    DefaultWeightedEdge e = graphRV.addEdge(o.from, o.target);
                    graphRV.setEdgeWeight(e, o.delay);
                });

        return graphRV;
    }

    private void removeLongestDelayRVLinks(int maxVehReqEdges, User request) {
        List<Vehicle> vehicles = graphRV.edgesOf(request).stream().filter(
                defaultWeightedEdge -> graphRV.getEdgeSource(defaultWeightedEdge) instanceof Vehicle)
                .sorted(Comparator.comparing(graphRV::getEdgeWeight))
                .map(defaultWeightedEdge -> (Vehicle) graphRV.getEdgeSource(defaultWeightedEdge))
                .collect(Collectors.toList());

        if (vehicles.size() > maxVehReqEdges) {
            List<Vehicle> toRemove = vehicles.subList(maxVehReqEdges, vehicles.size());
            for (Vehicle vehicle : toRemove) {
                graphRV.removeEdge(vehicle, request);
            }
        }
    }

    public Object getEdgeTarget(DefaultWeightedEdge edge) {
        return this.graphRV.getEdgeTarget(edge);
    }

    public Set<DefaultWeightedEdge> edgesOf(Vehicle vehicle) {
        return graphRV.edgesOf(vehicle);

    }

    public DefaultWeightedEdge getEdge(User request1, User request2) {
        return graphRV.getEdge(request1, request2);
    }

    public Map<User, List<User>> getRRList() {

        Map<User, List<User>> requestRequestsMap = new HashMap<>();

        List<User> requests = graphRV.vertexSet().stream()
                .filter(o -> o instanceof User)
                .map(o -> (User) o)
                .collect(Collectors.toList());

        for (User request : requests) {

            List<User> targets = graphRV.edgesOf(request).stream()
                    .filter(edge -> graphRV.getEdgeSource(edge) instanceof User)
                    .map(edgeRR -> (User) graphRV.getEdgeTarget(edgeRR))
                    .collect(Collectors.toList());

            requestRequestsMap.put(request, targets);
        }

        return requestRequestsMap;
    }

    public Map<User, List<Vehicle>> getRVList() {

        Map<User, List<Vehicle>> requestRequestsMap = new HashMap<>();

        List<User> requests = graphRV.vertexSet().stream()
                .filter(o -> o instanceof User)
                .map(o -> (User) o)
                .collect(Collectors.toList());

        for (User request : requests) {

            List<Vehicle> vehiclesCanPickup = graphRV.edgesOf(request).stream()
                    .filter(edge -> graphRV.getEdgeSource(edge) instanceof Vehicle)
                    .map(edgeVR -> (Vehicle) graphRV.getEdgeSource(edgeVR))
                    .collect(Collectors.toList());

            requestRequestsMap.put(request, vehiclesCanPickup);
        }

        return requestRequestsMap;
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/// GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Map<User, List<DefaultWeightedEdge>> getRVEdges() {
        Map<User, List<DefaultWeightedEdge>> requestEdgesRV = new HashMap<>();

        List<User> requests = graphRV.vertexSet().stream()
                .filter(o -> o instanceof User)
                .map(o -> (User) o)
                .collect(Collectors.toList());

        for (User request : requests) {

            List<DefaultWeightedEdge> edgesRV = graphRV.edgesOf(request).stream()
                    .filter(defaultEdge -> graphRV.getEdgeSource(defaultEdge) instanceof Vehicle)
                    .collect(Collectors.toList());

            requestEdgesRV.put(request, edgesRV);
        }

        return requestEdgesRV;
    }

    public Map<User, List<DefaultWeightedEdge>> getRREdges() {
        Map<User, List<DefaultWeightedEdge>> requestEdgesRR = new HashMap<>();

        List<User> requests = graphRV.vertexSet().stream()
                .filter(o -> o instanceof User)
                .map(o -> (User) o)
                .collect(Collectors.toList());

        for (User request : requests) {

            List<DefaultWeightedEdge> edgesRV = graphRV.edgesOf(request).stream()
                    .filter(defaultEdge -> graphRV.getEdgeSource(defaultEdge) instanceof User)
                    .collect(Collectors.toList());

            requestEdgesRR.put(request, edgesRV);
        }

        return requestEdgesRR;
    }

    public void printRVEdges() {
        System.out.println("------- RV EDGES");
        getRVEdges().forEach((user, defaultWeightedEdges) -> {
            System.out.println(String.format("\n########## %s - (edges=%d)", user, defaultWeightedEdges.size()));
            for (DefaultWeightedEdge edgeRV : defaultWeightedEdges) {
                System.out.println(String.format("%4d - %s", (int) graphRV.getEdgeWeight(edgeRV), edgeRV));
            }
        });
    }

    public void printRREdges() {
        System.out.println("------- RR EDGES");
        getRREdges().forEach((user, defaultWeightedEdges) -> {
            System.out.println(String.format("\n########## %s - (edges(source)=%d)", user, defaultWeightedEdges.size()));
            for (DefaultWeightedEdge edgeRV : defaultWeightedEdges) {
                System.out.println(String.format("%4d - %s", (int) graphRV.getEdgeWeight(edgeRV), edgeRV));
            }
        });
    }

    public String toString() {
        double avgTargetRequests = getRRList().values().stream().mapToDouble(List::size).average().orElse(Double.NaN);
        double avgVehicleVertices = getRVList().values().stream().mapToDouble(List::size).average().orElse(Double.NaN);
        return String.format("#nodes = %s, #edges = %s, avg. RR targets = %.2f, avg. RV links = %.2f", this.graphRV.vertexSet().size(), this.graphRV.edgeSet().size(), avgTargetRequests, avgVehicleVertices);
    }

    class EdgeRV implements Comparable<EdgeRV> {

        private final Integer delay;
        private final Object from;
        private final Object target;

        public EdgeRV(int delay, Object from, Object target) {
            this.delay = delay;
            this.from = from;
            this.target = target;
        }

        @Override
        public int compareTo(EdgeRV o) {
            return this.delay.compareTo(o.delay);
        }
    }

}