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

    private final List<User> listWaitingUsers;
    private final int vehicleCapacity;
    private final SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV;
    private final List<Vehicle> listVehicles;
    private final int maxEdgesRR;
    private final int maxEdgesRV;

    public GraphRV(List<User> listWaitingUsers, List<Vehicle> listVehicles, int vehicleCapacity, int maxEdgesRV, int maxEdgesRR) {
        this.vehicleCapacity = vehicleCapacity;
        this.listWaitingUsers = listWaitingUsers;
        this.maxEdgesRV = maxEdgesRV;
        this.maxEdgesRR = maxEdgesRR;
        this.listVehicles = listVehicles;
        this.graphRV = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        // Which requests can be combined?
        createGraphRVInParellel();
    }

    public List<EdgeRV> getRVEdge(int requestIndexWaitingList) {

        List<EdgeRV> edgesRR = getEdgesRR(requestIndexWaitingList);
        edgesRR = limitNumberOfEdgesRR(edgesRR);

        List<EdgeRV> edgesRV = getEdgesRV(requestIndexWaitingList);
        edgesRV = filterCompanyFleetButKeepHiringEdge(edgesRV);
        edgesRV = limitNumberEdgesRVButKeepHiringEdge(edgesRV);

        edgesRR.addAll(edgesRV);
        return edgesRR;
    }


    private List<EdgeRV> getEdgesRV(int i) {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // RV EDGES ////////////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        User r1 = listWaitingUsers.get(i);
        List<EdgeRV> edgesRV = new LinkedList<>();

        // Loop vehicles
        EdgeRV edgeVehicleHiredToServeUser = null;
        for (Vehicle vehicle : listVehicles) {

            // Try to find at least ONE way pickup request
            EdgeRV rv = createEdgeRV(r1, vehicle);
            if (rv != null) {
                if (rv.isHiringEdgeAndUserHasPromptedHiring()) {
                    edgeVehicleHiredToServeUser = rv;
                } else {
                    edgesRV.add(rv);
                }
            }
        }
        if (edgeVehicleHiredToServeUser != null) {
            edgesRV.add(0, edgeVehicleHiredToServeUser);
        }
        return edgesRV;
    }

    private List<EdgeRV> limitNumberEdgesRV(List<EdgeRV> edgesRV) {
        if (edgesRV.size() > maxEdgesRV) {
            Collections.sort(edgesRV);
            edgesRV = edgesRV.subList(0, maxEdgesRV);
        }
        return edgesRV;
    }

    private List<EdgeRV> limitNumberEdgesRVButKeepHiringEdge(List<EdgeRV> edgesRV) {

        EdgeRV hiringEdge = getHiringEdgeFromFirstPosition(edgesRV);
        edgesRV = limitNumberEdgesRV(edgesRV);

        if (hiringEdge != null) {
            edgesRV.add(0, hiringEdge);
        }

        return edgesRV;
    }

    /**
     * Company vehicles are filtered to avoid over-hiring, but we keep the backup hiring edge
     * TODO - If longer contracts are used, need to be changed
     */
    private List<EdgeRV> filterCompanyFleetButKeepHiringEdge(List<EdgeRV> edgesRV) {

        EdgeRV hiringEdge = getHiringEdgeFromFirstPosition(edgesRV);

        List<EdgeRV> vehicles = new ArrayList<>();
        List<EdgeRV> hired = new ArrayList<>();

        for (EdgeRV edgeRV : edgesRV) {
            if (edgeRV.isHiringEdge()) {
                hired.add(edgeRV);
            } else {
                vehicles.add(edgeRV);
            }
        }

        edgesRV = vehicles;

        if (hiringEdge != null) {
            edgesRV.add(0, hiringEdge);
        }

        return edgesRV;
    }

    private EdgeRV getHiringEdgeFromFirstPosition(List<EdgeRV> edgesRV) {
        EdgeRV hiringEdge = null;
        if (!edgesRV.isEmpty() && edgesRV.get(0).isHiringEdgeAndUserHasPromptedHiring()) {
            hiringEdge = edgesRV.remove(0);
        }
        return hiringEdge;
    }

    /**
     * Sort edges by delay and cap the number of edges to maxEdgesRR.
     * TODO Insert sorted to avoid sorting
     * @param edges RR edges
     * @return Subset of RR edges
     */
    private List<EdgeRV> limitNumberOfEdgesRR(List<EdgeRV> edges) {
        if (edges.size() > maxEdgesRR) {
            Collections.sort(edges);
            edges = edges.subList(0, maxEdgesRR);
        }
        return edges;
    }

    private List<EdgeRV> getEdgesRR(int i) {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // RR EDGES ////////////////////////////////////////////////////////////////////////////////////////////////////
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        List<EdgeRV> edges = new LinkedList<>();

        // Request r1 data
        User r1 = listWaitingUsers.get(i);
        Node pk1 = r1.getNodePk();
        Node dp1 = r1.getNodeDp();


        for (int j = i + 1; j < listWaitingUsers.size(); j++) {

            // Request r2 data
            User r2 = listWaitingUsers.get(j);
            Node pk2 = r2.getNodePk();
            Node dp2 = r2.getNodeDp();

            // Valid sequences for two requests
            List<LinkedList<Node>> sequencesPkDpTwoRequests = Arrays.asList(
                    new LinkedList<>(Arrays.asList(pk1, pk2, dp2, dp1)),
                    new LinkedList<>(Arrays.asList(pk1, pk2, dp1, dp2)),
                    new LinkedList<>(Arrays.asList(pk1, dp1, pk2, dp2)),
                    new LinkedList<>(Arrays.asList(pk2, pk1, dp1, dp2)),
                    new LinkedList<>(Arrays.asList(pk2, pk1, dp2, dp1)),
                    new LinkedList<>(Arrays.asList(pk2, dp2, pk1, dp1)));

            for (LinkedList<Node> validSequence : sequencesPkDpTwoRequests) {
                int delay = Visit.isValidSequenceFeasible(
                        validSequence, validSequence.get(0).getDeparture(),
                        validSequence.get(0).getLoad(),
                        vehicleCapacity,
                        Integer.MAX_VALUE);

            List<LinkedList<Node>> sequencesPkDpTwoRequests = Arrays.asList(seq1, seq2, seq3, seq4);

            for (LinkedList<Node> seq : sequencesPkDpTwoRequests) {
                int delay = Visit.isValidSequence(seq, seq.get(0).getDeparture(), seq.get(0).getLoad(), vehicleCapacity, Integer.MAX_VALUE);
                if (delay >= 0) {
                    edges.add(new EdgeRV(delay, r1, r2));
                    break;
                }
            }
        }
        return edges;
    }

    /**
     * Return an edge connecting requests 1 and 2 if they can be serviced in a visit.
     * All possible permutations are tried out.
     * TODO Cap if cannot pickup requests within TW?
     *
     * @param request1
     * @param request2
     * @return EdgeRV or null if does not exist
     */
    private EdgeRV getRREdgesFromRequests(User request1, User request2) {

        // Pickups
        Node pk1 = request1.getNodePk();
        Node pk2 = request2.getNodePk();

        // Drop-offs
        Node dp1 = request1.getNodeDp();
        Node dp2 = request2.getNodeDp();

        // Valid sequences for two requests = 4 permutations
        List<LinkedList<Node>> sequencesPkDpTwoRequests = Arrays.asList(
                new LinkedList<>(Arrays.asList(pk1, pk2, dp2, dp1)),
                new LinkedList<>(Arrays.asList(pk1, pk2, dp1, dp2)),
                new LinkedList<>(Arrays.asList(pk1, dp1, pk2, dp2)),
                new LinkedList<>(Arrays.asList(pk2, pk1, dp1, dp2)),
                new LinkedList<>(Arrays.asList(pk2, pk1, dp2, dp1)),
                new LinkedList<>(Arrays.asList(pk2, dp2, pk1, dp1)));

        for (LinkedList<Node> validSequence : sequencesPkDpTwoRequests) {
            int delay = Visit.isValidSequenceFeasible(
                    validSequence, validSequence.get(0).getDeparture(),
                    validSequence.get(0).getLoad(),
                    vehicleCapacity,
                    Integer.MAX_VALUE);

            if (delay >= 0) {
                return new EdgeRV(delay, request1, request2);
            }
        }
        return null;
    }

    private EdgeRV createEdgeRV_old(User request, Vehicle vehicle) {

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
                    vehicle.getCapacity(),
                    vehicle.getContractDeadline());


            // If RV exists, there is at least ONE way to pickup up the request.
            // The BEST way will be generated by the RTV graph.
            if (delay >= 0) {
                // Connect vehicle to request
                return new EdgeRV(delay, vehicle, request);
            }
        }
        return null;
    }

    /**
     * Pairwise graph of vehicles and requests. Combine vehicles and requests.
     */
    public void createGraphRVInParellel() {

        // Populating RV graph
        for (User user : listWaitingUsers) {
            graphRV.addVertex(user);
        }

        for (Vehicle vehicle : listVehicles) {
            graphRV.addVertex(vehicle);
        }

        IntStream.range(0, listWaitingUsers.size()).parallel()
                .mapToObj(this::getRVEdge)
                .collect(HashSet<EdgeRV>::new, HashSet::addAll, HashSet::addAll)
                .forEach(o -> {
                    DefaultWeightedEdge e = graphRV.addEdge(o.from, o.target);
                    graphRV.setEdgeWeight(e, o.delay);
                });
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

        List<User> requests = getUsersFromVertexSet();


        for (User request : requests) {
            List<User> targets = getTargetsFromRequest(request);
            requestRequestsMap.put(request, targets);
        }

        return requestRequestsMap;
    }

    private List<User> getTargetsFromRequest(User request) {
        return graphRV.edgesOf(request).stream()
                .filter(edge -> graphRV.getEdgeSource(edge) instanceof User && graphRV.getEdgeSource(edge) != request)
                .map(edgeVR -> (User) graphRV.getEdgeSource(edgeVR)).collect(Collectors.toList());
    }

    public Map<User, List<Vehicle>> getRVList() {

        Map<User, List<Vehicle>> requestRequestsMap = new HashMap<>();

        List<User> requests = getUsersFromVertexSet();

        for (User request : requests) {

            List<Vehicle> vehiclesCanPickup = getVehiclesFromVREdgesOfRequest(request);

            requestRequestsMap.put(request, vehiclesCanPickup);
        }

        return requestRequestsMap;
    }

    private List<Vehicle> getVehiclesFromVREdgesOfRequest(User request) {

        return graphRV.edgesOf(request).stream()
                .filter(edge -> graphRV.getEdgeSource(edge) instanceof Vehicle)
                .map(edgeVR -> (Vehicle) graphRV.getEdgeSource(edgeVR))
                .collect(Collectors.toList());
    }

    private List<User> getUsersFromVertexSet() {
        if (graphRV == null) {
            return Collections.emptyList();
        }
        return graphRV.vertexSet().stream()
                .filter(o -> o instanceof User)
                .map(o -> (User) o)
                .collect(Collectors.toList());
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/// GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public Map<User, List<DefaultWeightedEdge>> getRVEdges() {
        Map<User, List<DefaultWeightedEdge>> requestEdgesRV = new HashMap<>();

        List<User> requests = getUsersFromVertexSet();

        for (User request : requests) {

            List<DefaultWeightedEdge> edgesRV = getRVEdgesFromRequest(request);

            requestEdgesRV.put(request, edgesRV);
        }

        return requestEdgesRV;
    }

    private List<DefaultWeightedEdge> getRVEdgesFromRequest(User request) {
        return graphRV.edgesOf(request).stream()
                .filter(defaultEdge -> graphRV.getEdgeSource(defaultEdge) instanceof Vehicle)
                .collect(Collectors.toList());
    }

    public Map<User, List<DefaultWeightedEdge>> getRREdges() {
        Map<User, List<DefaultWeightedEdge>> requestEdgesRR = new HashMap<>();

        List<User> requests = getUsersFromVertexSet();

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
            System.out.printf("\n########## %s - (edges=%d)%n", user, defaultWeightedEdges.size());
            for (DefaultWeightedEdge edgeRV : defaultWeightedEdges) {
                System.out.printf("%4d - %s%n", (int) graphRV.getEdgeWeight(edgeRV), edgeRV);
            }
        });
    }

    public void printRREdges() {
        System.out.println("------- RR EDGES");
        getRREdges().forEach((user, defaultWeightedEdges) -> {
            System.out.printf("\n########## %s - (edges(source)=%d)%n", user, defaultWeightedEdges.size());
            for (DefaultWeightedEdge edgeRV : defaultWeightedEdges) {
                System.out.printf("%4d - %s%n", (int) graphRV.getEdgeWeight(edgeRV), edgeRV);
            }
        });
    }

    public String toString() {
        double avgTargetRequests = getRRList().values().stream().mapToDouble(List::size).average().orElse(Double.NaN);
        double avgVehicleVertices = getRVList().values().stream().mapToDouble(List::size).average().orElse(Double.NaN);
        return String.format("#nodes = %s, #edges = %s, avg. RR targets = %.2f, avg. RV links = %.2f", this.graphRV.vertexSet().size(), this.graphRV.edgeSet().size(), avgTargetRequests, avgVehicleVertices);
    }

}