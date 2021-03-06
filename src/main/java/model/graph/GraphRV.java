package model.graph;

import dao.Dao;
import dao.Logging;
import model.Leg;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import util.pdcombinatorics.PDPermutations;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class GraphRV {

    private final List<User> listWaitingUsers;
    private final int vehicleCapacity;
    private final SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV;
    private final Set<Vehicle> listVehicles;
    private final int maxEdgesRR;
    private final int maxEdgesRV;

    public GraphRV(Set<User> listWaitingUsers, Set<Vehicle> listVehicles, int vehicleCapacity, int maxEdgesRV, int maxEdgesRR) {
        this.vehicleCapacity = vehicleCapacity;
        this.listWaitingUsers = new ArrayList<>(listWaitingUsers);
        this.maxEdgesRV = maxEdgesRV;
        this.maxEdgesRR = maxEdgesRR;
        this.listVehicles = listVehicles;
        this.graphRV = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        // Which requests can be combined?
        createGraphRVInParallel();
    }

    /**
     * Get RV edges (RR and VR) for a request in waiting list
     * @param requestIndexWaitingList
     * @return List of RV edges
     */
    public List<EdgeRV> getRVEdge(int requestIndexWaitingList) {
        List<EdgeRV> edgesRR = getRREdges(requestIndexWaitingList);
        List<EdgeRV> edgesRV = getRVEdgesFromUser(requestIndexWaitingList);
        edgesRR.addAll(edgesRV);
        return edgesRR;
    }

    /**
     * Check which requests can share a ride with request i
     * @param i Index of user in the waiting list
     * @return List of (user, user) edges
     */
    public List<EdgeRV> getRREdges(int i) {
        List<EdgeRV> edgesRR = getEdgesRR(i);
        //TODO fix filtering
        edgesRR = limitNumberOfEdgesRR(edgesRR);
        return edgesRR;
    }

    /**
     * Check which vehicles can include a user in their visit.
     * @param i Index of user in the waiting list
     * @return List of (vehicle, user) edges
     */
    public List<EdgeRV> getRVEdgesFromUser(int i) {
        List<EdgeRV> edgesRV = getEdgesRV(i);
        //TODO fix filtering sorting
        edgesRV = filterCompanyFleetButKeepHiringEdge(edgesRV);
        edgesRV = limitNumberEdgesRVButKeepHiringEdge(edgesRV);
        return edgesRV;
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

    /**
     * Return an edge connecting i to all subsequent requests in waiting list.
     * TODO Cap if cannot pickup requests within TW?
     *
     * @param i Index of request in waiting list
     */
    private List<EdgeRV> getEdgesRR(int i) {

        List<EdgeRV> edges = new LinkedList<>();

        // Request r1 data
        User r1 = listWaitingUsers.get(i);

        for (int j = i + 1; j < listWaitingUsers.size(); j++) {

            // Request r2 data
            User r2 = listWaitingUsers.get(j);

            EdgeRV edge = getRREdgesFromRequests(r1, r2);

            if (edge != null) {
                edges.add(edge);
                assertR1AndR2AreReachable(r1, r2);
            }
        }

        return edges;
    }

    private void assertR1AndR2AreReachable(User r1, User r2) {
        assert Dao.getInstance().getMapReachableNetworkIdsWithinTimeLimit().get(r1.getNodePk().getNetworkId()).contains(r2.getNodePk().getNetworkId()) ||
                Dao.getInstance().getMapReachableNetworkIdsWithinTimeLimit().get(r2.getNodePk().getNetworkId()).contains(r1.getNodePk().getNetworkId()) :
                String.format("%s %s\n",
                        Dao.getInstance().getDistSec(r1.getNodePk(), r2.getNodePk()),
                        Dao.getInstance().getDistSec(r2.getNodePk(), r1.getNodePk())); // PK1 - PK2
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
        List<Node[]> sequencesPkDpTwoRequests = Arrays.asList(
                new Node[]{pk1, pk2, dp2, dp1},
                new Node[]{pk1, pk2, dp1, dp2},
                new Node[]{pk1, dp1, pk2, dp2},
                new Node[]{pk2, pk1, dp1, dp2},
                new Node[]{pk2, pk1, dp2, dp1},
                new Node[]{pk2, dp2, pk1, dp1});

        for (Node[] validSequence : sequencesPkDpTwoRequests) {
            Leg draftVisit = Visit.getDraftVisit(validSequence);

            if (draftVisit != null) {
                return new EdgeRR(draftVisit.delay, request1, request2);
            }
        }
        return null;
    }

    private EdgeRV createEdgeRV(User request, Vehicle vehicle) {

        // Create request set out of one request
        Set<User> requests = new HashSet<>(Collections.singletonList(request));
        PDPermutations perms = new PDPermutations(requests, vehicle);

        while (perms.hasNext()) {

            Leg draftVisit = Visit.getDraftVisit(vehicle, perms.next());

            // If RV exists, there is at least ONE way to pickup up the request.
            // The BEST way will be generated by the RTV graph.
            if (draftVisit != null) {
                // Connect vehicle to request
                return new EdgeVR(draftVisit.delay, vehicle, request);
            }
        }
        return null;
    }

    /**
     * Pairwise graph of vehicles and requests. Combine vehicles and requests.
     */
    public void createGraphRVInParallel() {

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
                    DefaultWeightedEdge e = graphRV.addEdge(o.getFrom(), o.getTarget());
                    graphRV.setEdgeWeight(e, o.getDelay());
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

    public List<Vehicle> getVehiclesFromVREdgesOfRequest(User request) {

        return graphRV.edgesOf(request).stream()
                .filter(edge -> graphRV.getEdgeSource(edge) instanceof Vehicle)
                .map(edgeVR -> (Vehicle) graphRV.getEdgeSource(edgeVR))
                .collect(toList());
    }

    private List<User> getUsersFromVertexSet() {
        if (graphRV == null) {
            return Collections.emptyList();
        }
        return graphRV.vertexSet().stream()
                .filter(o -> o instanceof User)
                .map(o -> (User) o)
                .collect(toList());
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
        Logging.logger.info("------- RV EDGES");
        getRVEdges().forEach((user, defaultWeightedEdges) -> {
            Logging.logger.info("{}", String.format("\n########## %s - (edges=%d)", user, defaultWeightedEdges.size()));
            for (DefaultWeightedEdge edgeRV : defaultWeightedEdges) {
                Logging.logger.info("{}", String.format("%4d - %s", (int) graphRV.getEdgeWeight(edgeRV), edgeRV));
            }
        });
    }

    public void printRREdges() {
        Logging.logger.info("------- RR EDGES");
        getRREdges().forEach((user, defaultWeightedEdges) -> {
            Logging.logger.info("{}", String.format("\n########## %s - (edges(source)=%d)", user, defaultWeightedEdges.size()));
            for (DefaultWeightedEdge edgeRV : defaultWeightedEdges) {
                Logging.logger.info("{}", String.format("%4d - %s", (int) graphRV.getEdgeWeight(edgeRV), edgeRV));
            }
        });
    }

    public String toString() {
        double avgTargetRequests = getRRList().values().stream().mapToDouble(List::size).average().orElse(Double.NaN);
        double avgVehicleVertices = getRVList().values().stream().mapToDouble(List::size).average().orElse(Double.NaN);
        return String.format("#nodes = %s, #edges = %s, avg. RR targets = %.2f, avg. RV links = %.2f", this.graphRV.vertexSet().size(), this.graphRV.edgeSet().size(), avgTargetRequests, avgVehicleVertices);
    }

}