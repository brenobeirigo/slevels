package model.network;

import config.InstanceData;
import config.NetworkConfig;
import dao.Dao;
import helper.HelperIO;
import model.demand.User;
import model.node.Node;
import model.node.NodeDropoff;
import model.node.NodeNetwork;
import model.node.NodePickup;
import model.route.RouteUtil;
import org.jgrapht.alg.interfaces.ShortestPathAlgorithm;
import org.jgrapht.alg.shortestpath.AStarShortestPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.alg.util.Pair;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import simulation.Environment;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;


public class NetworkLoaded implements TransportNetwork {

    public static final String SHORTEST_PATH_DIJKSTRA = "shortest_path_dijkstra";
    public static final String SHORTEST_PATH_ASTAR = "shortest_path_astar";


    private final List<List<Integer>> adjacencyMatrix;
    private final List<List<Double>> distMatrixMeters;
    //    private final Map<Integer, List<Integer>> closestZones;
    private final SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> networkGraph;
    private final List<Zone> zones;
    private final List<Integer> zoneIds;
    private final Map<Integer, NodeNetwork> nodeNetworkInfo;
    protected ShortestPathAlgorithm<Integer, DefaultWeightedEdge> shortestPath;
    private final Map<Integer, List<Pair<Integer, Double>>> zoneZonesDistancesMap;
    public Map<Integer, Map<Integer, List<Integer>>> closestNZones;
    private Double speedFactor;

    public NetworkLoaded(NetworkConfig data){
        this(
                data.distances_file(),
                data.adjacency_matrix_file(),
                data.zone_data_file(),
                data.network_node_info_file(),
                data.shortest_path_method(),
                data.avg_speed_km_hour());
    }
    public NetworkLoaded(String pathDistanceMatrix, String pathAdjacencyMatrix, String pathZonesIDs, String pathNetworkNodeInfo, String shortestPathAlg, Double avgSpeedKmHour) {
        this.zones = NetworkUtil.getZones(pathZonesIDs);
        this.zoneIds = this.zones.stream().map(Zone::nodeId).collect(Collectors.toList());
        this.distMatrixMeters = NetworkUtil.getDistanceMatrixMeters(pathDistanceMatrix);
        this.zoneZonesDistancesMap = NetworkUtil.getZoneToZonesAndDistanceMap(distMatrixMeters, zoneIds);
        this.nodeNetworkInfo = NetworkUtil.getNodeDictionaryFromJsonString(HelperIO.readFileFromPath(pathNetworkNodeInfo));

//        this.closestZones = NetworkUtil.getClosestZoneMap(4, distMatrixMeters, zoneIds);
        this.adjacencyMatrix = NetworkUtil.getAdjacencyMatrix(pathAdjacencyMatrix);
        this.networkGraph = NetworkUtil.getWeightedGraphFromAdjacencyMatrix(adjacencyMatrix, distMatrixMeters);
        this.closestNZones = new HashMap<>();

        this.speedFactor = avgSpeedKmHour * (1_000 / 3_600);

        setShortestPathAlgorithm(shortestPathAlg);
    }

    /**
     * Set the algorithm to find the shortest paths on the network weighted graph.
     *
     * @param spAlgLabel: shortest_path_dijkstra, shortest_path_astar
     */
    private void setShortestPathAlgorithm(String spAlgLabel) {

        if (spAlgLabel.equals(SHORTEST_PATH_DIJKSTRA)) {
            this.shortestPath = new DijkstraShortestPath<>(networkGraph);

        } else if (spAlgLabel.equals(SHORTEST_PATH_ASTAR)) {
            this.shortestPath = new AStarShortestPath<>(
                    networkGraph,
                    (i, j) -> distMatrixMeters.get(i).get(j));
        }
    }

    @Override
    public Double getDistance(int originNetworkId, int destinationNetworkId) {
        return this.distMatrixMeters.get(originNetworkId).get(destinationNetworkId);
    }

    @Override
    public Set<Integer> getNodeIds() {
        return nodeNetworkInfo.keySet();
    }

    @Override
    public List<Integer> getNClosestZones(int originNetworkId, int nZones) {
        List<Integer> closestZones = this.zoneZonesDistancesMap.get(originNetworkId).stream().map(Pair::getFirst).limit(nZones).collect(Collectors.toList());
        return closestZones;
    }

    @Override
    public List<Integer> getZoneIds() {
        return this.zoneIds;
    }


    public String getStatistics() {
        StringBuilder b = new StringBuilder();
        b.append(String.format("Nodes: %d", this.distMatrixMeters.size()));
        b.append(String.format("Edges: %d", this.networkGraph.edgeSet().size()));

        Double minDistance = Double.MAX_VALUE;
        Double maxDistance = Double.MIN_VALUE;

        for (int i = 0; i < this.distMatrixMeters.size(); i++) {
            for (int j = 0; j < this.distMatrixMeters.size(); j++) {
                if (i != j) {
                    Double v = this.distMatrixMeters.get(i).get(j);
                    if (v < minDistance)
                        minDistance = v;
                    if (v > maxDistance)
                        maxDistance = v;
                }
            }
        }
        b.append(String.format("Min. distance: %3.2f m", minDistance));
        b.append(String.format("Max. distance: %3.2f m", maxDistance));
        b.append(String.format("Zones: %s", this.zones.toString()));

        return b.toString();
    }


    public List<String> getLonLatList(List<Integer> shortestPathB) {
        return shortestPathB.stream().map(
                v -> String.format(
                        "[%f, %f]",
                        nodeNetworkInfo.get(v).getPoint().getX(),
                        nodeNetworkInfo.get(v).getPoint().getY())
        ).collect(Collectors.toList());
    }

    /**
     * Access coordinate of point.
     *
     * @param id Node id
     * @return Point2D coordinate
     */
    @Override
    public Point2D getLocation(int id) {
        return nodeNetworkInfo.get((int) id).getPoint();
    }

    /**
     * Get distance in km
     *
     * @param from node id
     * @param to   node id
     * @return distance in km
     */
    public double getDistKm(int from, int to) {
        return distMatrixMeters.get(from).get(to);
    }

    /**
     * Get distance in km
     *
     * @param from node
     * @param to   node
     * @return distance in km
     */
    public double getDistKm(Node from, Node to) {
        return getDistKm(from.getNetworkId(), to.getNetworkId());
    }

    /**
     * Get distance in seconds (considering 30 m/s)
     * E.g.:
     * Speed = 30km/h
     * Then:
     * 30_000m/3_600s X dist_m/dist_sec =>
     * dist_sec = dist_m * 3_600s/30_000m
     *
     * @param from node id
     * @param to   node id
     * @return distance in seconds
     */
    @Override
    public int getDistSec(int from, int to) {
        return (int) (distMatrixMeters.get(from).get(to) * this.speedFactor);
    }


    /**
     * Get distance in seconds (considering 30 m/s)
     *
     * @param from node
     * @param to   node
     * @return distance in seconds
     */
    public int getDistSec(Node from, Node to) {
        return getDistSec(from.getNetworkId(), to.getNetworkId());
    }


    /**
     * Get shortest path (node id list) between origin o and destination d (including)
     *
     * @param originNetworkId      Shortest path origin
     * @param destinationNetworkId Shortest path destination
     * @return List of node ids
     */
    @Override
    public List<Integer> getShortestPathBetween(int originNetworkId, int destinationNetworkId) {
        return shortestPath.getPath(originNetworkId, destinationNetworkId).getVertexList();
    }

    @Override
    public double getLon(int networkId) {
        return getLocation(networkId).getX();
    }

    @Override
    public double getLat(int networkId) {
        return getLocation(networkId).getY();
    }


    /**
     * Get the network id of the node in the shortest path connecting two other nodes.
     *
     * @param from        Origin node
     * @param to          Destination node
     * @param elapsedTime Elapsed time
     * @return network id, or -1 if there is no intermediate node
     */
    @Override
    public int getNodeBetweenAndExtraDelay(Node from,
                                           Node to,
                                           int elapsedTime) {
        return getIntermediateNodeNetworkId(from.getNetworkId(), to.getNetworkId(), elapsedTime);
    }


    /**
     * Get the network id of the node in the shortest path connecting two other nodes.
     *
     * @param o
     * @param d
     * @param elapsedTime
     * @return network id, or -1 if there is no intermediate node
     */
    @Override
    public int getIntermediateNodeNetworkId(int o, int d, int elapsedTime) {

        // Vehicle did not leave the origin
        if (elapsedTime == 0) {
            return o;
        }

        List<Integer> sp = this.getShortestPathBetween(o, d);
        assert sp != null && !sp.isEmpty() : String.format("NULL" + o + " - " + d);

        List<Integer> spArrivals = getSpNodeArrivals(sp);
//        if (shortestPathDistances.get(o).get(d) == null) {
//            List<Integer> spArrivals = getSpNodeArrivals(sp);
//            shortestPathDistances.get(o).set(d, spArrivals);
//        }

        List<Integer> intermediateArrivalsList = spArrivals;
        assert !intermediateArrivalsList.isEmpty();

        int insertionPoint = RouteUtil.getInsertionPoint(elapsedTime, intermediateArrivalsList);

        if (insertionPoint == sp.size()) {
            return d;
        }

        return sp.get(insertionPoint);
    }


    /**
     * Build cumulative distance array from distances between nodes in sequence.
     * e.g.,     List [o, 1, 2, 3, d]
     * Arrivals [0, d(o,1), d(o,1) + d(1,2), d(o,1) + d(1,2) + d(2,3), d(o,1) + d(1,2) + d(2,3) + d(3,d)]
     *
     * @param sequenceNodeIds
     * @return cumulativeDistancesKm
     */
    private ArrayList<Integer> getSpNodeArrivals(List<Integer> sequenceNodeIds) {

        ArrayList<Integer> cumulativeDistancesKm = new ArrayList<>();

        int starting = 0;

        cumulativeDistancesKm.add(starting);

        // Building array of arrivals
        //TODO there are nodes with zero distances between them! Should be removed!
        for (int i = 0; i < sequenceNodeIds.size() - 1; i++) {
            int distanceLeg = getDistSec(sequenceNodeIds.get(i), sequenceNodeIds.get(i + 1));
            starting += distanceLeg;
            cumulativeDistancesKm.add(starting);
        }

        return cumulativeDistancesKm;
    }

    /**
     * Get the array of trip durations between every pair of nodes in the shortest path between "from" and "to"
     *
     * @param from
     * @param to
     * @return
     */
    public List<Integer> getArrayTravelDurationsBetweenInSeconds(Node from, Node to) {

        List<Integer> listNodes = Dao.getInstance().getServer().getShortestPathBetween(from.getNetworkId(), to.getNetworkId());

        List<Integer> durations = new ArrayList<>();

        for (int i = 0; i < listNodes.size() - 1; i++) {
            int o = listNodes.get(i);
            int d = listNodes.get(i + 1);
            durations.add(getDistSec(o, d));
        }
        return durations;
    }

    @Override
    public List<String> getShortestPathLonLatBetween(int originNetworkId, int destinationNetworkId) {
        List<Integer> shortestPathIDs = this.getShortestPathBetween(originNetworkId, destinationNetworkId);
        List<String> listCoords = this.getLonLatList(shortestPathIDs);

        return listCoords;
    }

    @Override
    public String getInfo(Node n) {
        return String.format(
                "[Network id=%d] %7s (earliest=%4s, ear. dep=%4s, departure=%4s, arrival=%4s, latest=%4s) [delay=%4s] %s",
                n.getNetworkId(),
                n,
                n.getEarliest(),
                n.getEarliestDeparture(),
                n.getDeparture(),
                n.getArrival(),
                n instanceof NodePickup || n instanceof NodeDropoff ? n.getLatest() : "----",
                n.getArrival() != null ? n.getArrival() - n.getEarliest() : "----",
                n instanceof NodePickup ? String.format("(dist. DP=%4d)", this.getDistSec(n, User.mapOfUsers.get(n.getTripId()).getNodeDp())) : "");
    }

}
