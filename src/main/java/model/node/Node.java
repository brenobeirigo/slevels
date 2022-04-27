package model.node;

import dao.Dao;
import model.User;
import simulation.Simulation;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static config.Config.*;

public abstract class Node implements Comparable<Node> {

    public static Map<Integer, Integer> hotSpot = new HashMap<>();
    public static Set<Integer> tabu = new HashSet<>();

    public static final int MAX_NUMBER_NODES = 10000000;
    // Coordinate dictionaries
    public static Map<Integer, Double[]> nodeDic = new HashMap<>();

    protected int networkId, id;
    protected Integer earliest;
    protected Integer earliestDeparture; // Node is only accounted by the system at decision points at the end of batch TWs
    protected Integer arrival; // Updated when picked up (User is a passenger)
    protected Integer departure;
    protected Integer latest;
    protected Integer arrivalSoFar; // Updated on the first match (User is a request)
    protected Integer load;
    protected Integer delay;
    protected Integer tripId;

    //TODO: Place this in pk
    protected int urgent;
    protected int hotness; // How many nodes share the same networkID?

    public Node(int id, int networkId) {
        this.id = id;
        this.networkId = networkId;
    }


    public Node(Node n) {
        this.id = n.id;
        this.load = n.load;
        this.networkId = n.networkId;
        this.earliest = n.earliest;
        this.earliestDeparture = n.earliestDeparture;
        this.latest = n.latest;
        this.arrival = n.arrival;
        this.arrivalSoFar = n.arrivalSoFar;
        this.tripId = n.tripId;
        this.delay = n.delay;
        this.departure = n.departure;
        this.urgent = n.urgent;
        this.hotness = n.hotness;

        //Node.hotSpot.compute(this.networkId, (tokenKey, oldValue) -> oldValue == null ? 1 : oldValue + 1);
    }

    public static boolean nodesAreAtSameNetworkLocations(Node lastVisitedNode, Node middle) {
        return lastVisitedNode.getNetworkId() == middle.getNetworkId();
    }


    public Integer getDeparture() {
        return departure;
    }

    public void setDeparture(int departure) {
        this.departure = departure;
    }

    public Node(int id, int networkId, double lat, double lon, int earliest, int latest) {
        this.id = id;
        this.networkId = networkId;
        this.earliest = earliest;
        this.latest = latest;
        Node.nodeDic.put(networkId, new Double[]{lat, lon});
    }

    public Node(int id, int networkId, int earliest, int latest) {
        this.id = id;
        this.networkId = networkId;
        this.earliest = earliest;
        this.latest = latest;
        Node.nodeDic.put(networkId, new Double[]{0.0, 0.0});
    }

    public static void reset() {
        /* Reset user */
        Node.nodeDic = new HashMap<>();
        Node.tabu = new HashSet<>();
        Node.hotSpot = new HashMap<>();
    }

    public static String getGeoJson(Point2D p) {

        String s = String.format("{\n" +
                "      \"type\": \"Feature\",\n" +
                "      \"properties\": {\n" +
                "        \"marker-color\": \"%s\",\n" +
                "        \"marker-size\": \"%s\",\n" +
                "        \"marker-symbol\": \"%s\"\n" +
                "      },\n" +
                "      \"geometry\": {\n" +
                "        \"type\": \"Point\",\n" +
                "        \"coordinates\": [\n" +
                "          %f,\n" +
                "          %f\n" +
                "        ]\n" +
                "      }\n" +
                "    }", "#ea0000", "small", "circle", p.getX(), p.getY());

        return s;
    }

    public abstract String getType();

    public void increaseHotness() {
        Node.hotSpot.compute(this.networkId, (tokenKey, oldValue) -> oldValue == null ? 1 : oldValue + 1);
    }

    public String getInfo() {
        return String.format(
                "[timestep=%4d] %7s (earliest=%4s, ear. dep=%4s, departure=%4s, arrival=%4s, latest=%4s) [delay=%4s] %s",
                Simulation.rightTW,
                this,
                String.valueOf(this.getEarliest()),
                String.valueOf(this.getEarliestDeparture()),
                String.valueOf(this.getDeparture()),
                String.valueOf(this.getArrival()),
                String.valueOf(this instanceof NodePK || this instanceof NodeDP ? this.getLatest() : "----"),
                String.valueOf(this.getArrival()!=null? this.getArrival() - getEarliest() : "----"),
                String.valueOf(this instanceof NodePK? String.format("(dist. DP=%4d)", Dao.getInstance().getDistSec(this, User.mapOfUsers.get(this.tripId).getNodeDp())): ""));
    }

    public Integer getDelay() {
        return delay;
    }

    public Integer getDelaySoFar(){
        // Arrival so far is Integer.MAX_VALUE, hence delaySoFar is a large number when node was not visited.
        return this.arrivalSoFar - this.earliest;
    }

    public Integer getTripId() {
        return tripId;
    }

    public int getId() {
        return id;
    }

    public Integer getLoad() {
        return load;
    }

    public void setLoad(int load) {
        this.load = load;
    }

    @Override
    public int hashCode() {
        // System.out.println("Hash:" + this.id);
        return this.id;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    public Integer getEarliest() {
        return earliest;
    }

    public void setEarliest(int earliest) {
        this.earliest = earliest;
    }

    public Integer getArrival() {
        return arrival;
    }

    public void setArrival(int arrival) {
        this.arrival = arrival;
        this.delay = arrival - earliest;
    }

    public Integer getLatest() {
        return latest;
    }

    public void setLatest(int latest) {
        this.latest = latest;
    }

    /**
     * Increase maximum delay of a node. Invoked during the assignment, when customers cannot be
     * serviced in a round and have to wait more for a vehicle.
     *
     * @param extraDelay
     */
    public void increaseLatest(int extraDelay) {
        this.latest += extraDelay;
    }



    public double getLat() {
        //return Node.nodeDic.get(networkId)[0];
        return Dao.getInstance().getLocation(networkId).getY();
    }

    public double getLon() {
        // System.out.println(networkId);
        // System.out.println(this);
        // TODO where coordinates  come from?
        return Dao.getInstance().getLocation(networkId).getX();
        //System.out.println(Node.nodeDic.keySet());
        //return Node.nodeDic.get(networkId)[1];
    }

    @Override
    public int compareTo(Node that) {
        // Urgent nodes are priority
        if (this.urgent > that.urgent) return BEFORE;
        if (this.urgent < that.urgent) return AFTER;

        // System.out.println("comparing " + this + " and " + that + "(" + this.getId() + ", "+ that.getId() + ")");
        if (hotSpot.get(this.networkId) < hotSpot.get(that.networkId)) return AFTER;
        if (hotSpot.get(this.networkId) > hotSpot.get(that.networkId)) return BEFORE;
        return EQUAL;
    }

    public int getUrgent() {
        return urgent;
    }

    public void setUrgent(int urgent) {
        this.urgent = urgent;
    }

    public void increaseUrgency() {
        this.urgent++;
    }

    public Integer getArrivalSoFar() {
        return arrivalSoFar;
    }

    public void setArrivalSoFar(int arrivalSoFar) {
        this.arrivalSoFar = arrivalSoFar;
    }

    /**
     * IF node = DP -> Departure can be greater than rightTW
     * IF node = PK && on-demand-based system -> Departure always <= rightTW
     * If node = PK && reservation-based system -> Departure can be greater than right TW
     *
     * @return Earliest time Node ca be serviced
     */
    public Integer getEarliestDeparture() {
        return earliestDeparture;
    }

    public void setEarliestDeparture(int earliestDeparture) {
        this.earliestDeparture = earliestDeparture;
    }
}