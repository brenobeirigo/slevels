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
import util.PDPermutations;


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class GraphRV {

    private final List<User> listWaitingUsers;
    private final int vehicleCapacity;
    private final SimpleWeightedGraph<Object, DefaultWeightedEdge> graphRV;
    private final List<Vehicle> listVehicles;
    private final int maxEdgesRR;
    private final int maxEdgesRV;

    public static String fileName = "D:\\projects\\dev\\slevels\\src\\main\\resources\\day\\serialvsparallel.csv";
    public static FileOutputStream fos;

    public static String fileName2 = "D:\\projects\\dev\\slevels\\src\\main\\resources\\day\\rv2.csv";
    public static FileOutputStream fos2;


    static {
        try {
            fos = new FileOutputStream(fileName, true);
            fos2 = new FileOutputStream(fileName2, true);
            try {
                fos.write("nRequests Serial Parallel1 Parallel2 Looping Old\r\n".getBytes());
                fos2.write("old new newer\r\n".getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    public GraphRV(List<User> listWaitingUsers, List<Vehicle> listVehicles, int vehicleCapacity, int maxEdgesRV, int maxEdgesRR) {
        this.vehicleCapacity = vehicleCapacity;
        this.listWaitingUsers = listWaitingUsers;
        this.maxEdgesRV = maxEdgesRV;
        this.maxEdgesRR = maxEdgesRR;
        this.listVehicles = listVehicles;
        this.graphRV = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);

        //SimpleWeightedGraph<Object, DefaultWeightedEdge> a = new SimpleWeightedGraph(this.graphRV)

        // Which requests can be combined?
        createGraphRVInParallel();
    }

    public void saveTimes(String line){
        try {
            fos.write(String.format("%s\r\n",line).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveTimes(FileOutputStream fos, String line){
        try {
            fos.write(String.format("%s\r\n",line).getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
            Long timestamp_0 = System.nanoTime();
            EdgeRV rv = createEdgeRV_old(r1, vehicle);
            Long timestamp_1 = System.nanoTime();
            EdgeRV rv2 = createEdgeRV(r1, vehicle);
            Long timestamp_2 = System.nanoTime();
            EdgeRV rv3 = createEdgeRV2(r1, vehicle);
            Long timestamp_3 = System.nanoTime();
            //TODO compare RVs via equals - worth creating two objects?
            if (rv!=null) {
                assert rv.equals(rv2) && rv2.equals(rv3) : "%s - %s - old = %s, new = %s, new2 = %s".formatted(r1, vehicle, rv, rv2, rv3);
            }
            else {
                assert rv == rv2 && rv2 == rv3;
            }
            long t1 = timestamp_1 - timestamp_0;
            long t2 = timestamp_2 - timestamp_1;
            long t3 = timestamp_3 - timestamp_2;
            System.out.println("Old:" + t1 + " New:" + t2 + " - New better?" +((t1)>(t2)) + " - New EVEN better?" +(t2> t3));

            saveTimes(fos2, String.format("%d %d %d", t1,t2,t3));
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

            EdgeRV edge = getRREdgesFromRequests(r1,r2);

            if (edge != null){
                edges.add(edge);
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
                return new EdgeRR(delay, request1, request2);
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
                //System.out.printf("OLD: %s - delay: %d - vehicle: %s - request: %s:\n", sequenceFromVehiclePositionToLastDelivery,delay, vehicle, request);
                return new EdgeVR(delay, vehicle, request);
            }
        }
        return null;
    }

    private EdgeRV createEdgeRV(User request, Vehicle vehicle) {

        // Create request set out of one request
        Set<User> requests = new HashSet<>(Collections.singletonList(request));
        PDPermutations perms = new PDPermutations(requests, vehicle);

        while (perms.hasNext()) {
            List<Node> sequencePickupsAndDeliveries = List.of(perms.next());

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
                //System.out.printf("NEW: %s - delay: %d - vehicle: %s - request: %s:\n", sequenceFromVehiclePositionToLastDelivery,delay, vehicle, request);
                return new EdgeVR(delay, vehicle, request);
            }
        }
        return null;
    }

    private EdgeRV createEdgeRV2(User request, Vehicle vehicle) {

        // Create request set out of one request
        Set<User> requests = new HashSet<>(Collections.singletonList(request));
        PDPermutations perms = new PDPermutations(requests, vehicle);

        while (perms.hasNext()) {
            List<Node> sequencePickupsAndDeliveries = List.of(perms.next());

            LinkedList<Node> sequenceFromVehiclePositionToLastDelivery = Method.addLastVisitedAndMiddleNodesToStart(sequencePickupsAndDeliveries, vehicle);

            if (sequenceFromVehiclePositionToLastDelivery == null)
                continue;

            int delay = Visit.isValidSequenceFeasible(
                    sequenceFromVehiclePositionToLastDelivery,
                    vehicle.getDepartureCurrent(),
                    vehicle.getCurrentLoad(),
                    vehicle.getCapacity(),
                    vehicle.getContractDeadline());


            // If RV exists, there is at least ONE way to pickup up the request.
            // The BEST way will be generated by the RTV graph.
            if (delay >= 0) {
                // Connect vehicle to request
                //System.out.printf("NEW: %s - delay: %d - vehicle: %s - request: %s:\n", sequenceFromVehiclePositionToLastDelivery,delay, vehicle, request);
                return new EdgeVR(delay, vehicle, request);
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
        System.out.println("## List waiting users: " + listWaitingUsers.size());
        int n = listWaitingUsers.size();

        long s1 = System.nanoTime();
        List<EdgeRV>  a = IntStream.range(0, n-1).boxed().flatMap(i->IntStream.range(i+1, n).boxed().map(j-> getRREdgesFromRequests(listWaitingUsers.get(i),listWaitingUsers.get(j)))).collect(Collectors.toList());
        HashSet<EdgeRV>  a2 = IntStream.range(0, n).mapToObj(this::getRVEdgesFromUser).collect(HashSet<EdgeRV>::new, HashSet::addAll, HashSet::addAll);
        long s2 = System.nanoTime();
        long t1 = s2 - s1;
        System.out.printf("Serial:    %d\n", t1);

        long s3 = System.nanoTime();
        List<EdgeRV>  b = IntStream.range(0, n-1).boxed().flatMap(i->IntStream.range(i+1, n).boxed().parallel().map(j-> getRREdgesFromRequests(listWaitingUsers.get(i),listWaitingUsers.get(j)))).collect(Collectors.toList());
        HashSet<EdgeRV>  b2 = IntStream.range(0, n).mapToObj(this::getRVEdgesFromUser).parallel().collect(HashSet<EdgeRV>::new, HashSet::addAll, HashSet::addAll);
        long s4 = System.nanoTime();
        long t2 = s4 - s3;
        System.out.printf("Parallel1: %d\n", t2);

        long s5 = System.nanoTime();
        List<EdgeRV>  c = IntStream.range(0, n-1).boxed().parallel().flatMap(i->IntStream.range(i+1, n).boxed().map(j-> getRREdgesFromRequests(listWaitingUsers.get(i),listWaitingUsers.get(j)))).collect(Collectors.toList());
        HashSet<EdgeRV>  c2 = IntStream.range(0, n).mapToObj(this::getRVEdgesFromUser).parallel().collect(HashSet<EdgeRV>::new, HashSet::addAll, HashSet::addAll);
        long s6 = System.nanoTime();
        long t3 = s6 - s5;
        System.out.printf("Parallel2: %d\n", t3);

        long s7 = System.nanoTime();
        List<EdgeRV>  d = new ArrayList<>();
        HashSet<EdgeRV> d2 = new HashSet<>();
        for (int i = 0; i <n-1; i++) {
            for (int j = i+1; j < n; j++) {
                d.add(getRREdgesFromRequests(listWaitingUsers.get(i),listWaitingUsers.get(j)));
            }
        }
        for (int i = 0; i < n; i++) {
            d2.addAll(getRVEdgesFromUser(i));
        }

        long s8 = System.nanoTime();
        long t4 = s8 - s7;
        System.out.printf("Looping  : %d\n", t4);

        long s9 = System.nanoTime();
        HashSet<EdgeRV>  f = IntStream.range(0, listWaitingUsers.size()).parallel()
                .mapToObj(this::getRVEdge)
                .collect(HashSet<EdgeRV>::new, HashSet::addAll, HashSet::addAll);

        long s10 = System.nanoTime();
        long t5 = s10 - s9;
        System.out.printf("OLD METHO: %d\n", t5);

        assert f.containsAll(d2);
        assert d2.containsAll(c2);
        assert c2.containsAll(b2);
        assert b2.containsAll(a2);


        saveTimes(String.format("%d %d %d %d %d %d",n, t1,t2,t3,t4,t5));

//        List<EdgeRV> RREdges =
//        Long timestamp_0 = System.nanoTime();
//        List<EdgeRV> collect1 = IntStream.range(0, listWaitingUsers.size()).parallel().mapToObj(this::getRREdges).collect(ArrayList::new, List::addAll, List::addAll);
//        Long timestamp_1 = System.nanoTime();
//        System.out.println("Parallel:" + (timestamp_1 - timestamp_0));
//        System.out.println(collect1);
//
//        Long timestamp_2 = System.nanoTime();
//        List<EdgeRV> collect2 = IntStream.range(0, listWaitingUsers.size()).mapToObj(this::getRVEdge).collect(ArrayList::new, List::addAll, List::addAll);
//        Long timestamp_3 = System.nanoTime();

//
//        Long timestamp_4 = System.nanoTime();
//
//        List<EdgeRV> collect3 = IntStream.range(0, listWaitingUsers.size()).mapToObj(this::getRVEdge).collect(ArrayList::new, List::addAll, List::addAll);
//        Long timestamp_5 = System.nanoTime();
//
//
//        System.out.println("Sequential:" + (timestamp_3 - timestamp_2));
//        System.out.println(collect2);
//        System.out.println("Collect parallel:" + collect1.size() + " - Collect sequential:" + collect2.size());
        // Generate RV edges for users in parallel
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

    private List<Vehicle> getVehiclesFromVREdgesOfRequest(User request) {

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
                .collect(toList());
    }

    public Map<User, List<DefaultWeightedEdge>> getRREdges() {
        Map<User, List<DefaultWeightedEdge>> requestEdgesRR = new HashMap<>();

        List<User> requests = getUsersFromVertexSet();

        for (User request : requests) {

            List<DefaultWeightedEdge> edgesRV = graphRV.edgesOf(request).stream()
                    .filter(defaultEdge -> graphRV.getEdgeSource(defaultEdge) instanceof User)
                    .collect(toList());

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