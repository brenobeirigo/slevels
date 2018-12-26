package model.node;

import javafx.geometry.Point2D;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static config.Config.*;

public class Node implements Comparable<Node> {

    public static Map<Integer, Integer> hotSpot = new HashMap<>();
    public static Set<Integer> tabu = new HashSet<>();

    public static final int MAX_NUMBER_NODES = 10000000;
    // Coordinate dictionaries
    public static Map<Integer, Double[]> nodeDic = new HashMap<>();

    protected int networkId, id;
    protected int earliest, arrival, departure, latest;
    protected int load;
    protected int delay;
    protected int tripId;
    //TODO: Place this in pk
    protected int urgent;
    protected int hotness; // How many nodes share the same networkID?

    public Node(int id, int networkId) {
        this.id = id;
        this.networkId = networkId;
        this.latest = Integer.MAX_VALUE;
        this.earliest = 0;
    }


    public Node(Node n) {
        this.id = n.id;
        this.load = n.load;
        this.networkId = n.networkId;
        this.earliest = n.earliest;
        this.latest = n.latest;
        this.arrival = n.arrival;
        this.tripId = n.tripId;

        //Node.hotSpot.compute(this.networkId, (tokenKey, oldValue) -> oldValue == null ? 1 : oldValue + 1);
    }


    public int getDeparture() {
        return departure;
    }

    public void setDeparture(int departure) {
        this.departure = departure;
    }

    public Node(int id, int networkId, double lat, double lon, int earliest, int latest, int load) {
        this.id = id;
        this.load = load;
        this.networkId = networkId;
        this.earliest = earliest;
        this.latest = latest;
        Node.nodeDic.put(networkId, new Double[]{lat, lon});
        //Node.hotSpot.compute(this.networkId, (tokenKey, oldValue) -> oldValue == null ? 1 : oldValue + 1);
    }

    public Node(int id, int networkId, int earliest, int latest, int load) {
        this.id = id;
        this.load = load;
        this.networkId = networkId;
        this.earliest = earliest;
        this.latest = latest;
        Node.nodeDic.put(networkId, new Double[]{0.0, 0.0});
        //Node.hotSpot.compute(this.networkId, (tokenKey, oldValue) -> oldValue == null ? 1 : oldValue + 1);
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

    /**
     * User was denied service! Strong candidate for rebalancing.
     */
    public void increaseUrgency() {
        this.urgent++;
    }

    public void increaseHotness() {
        Node.hotSpot.compute(this.networkId, (tokenKey, oldValue) -> oldValue == null ? 1 : oldValue + 1);
    }

    public String getInfo() {
        return String.format(
                "%7s (%4s | %4s - %4s | %4s) [%4s]",
                this,
                String.valueOf(this instanceof NodePK || this instanceof NodeDP ? this.getEarliest() : "----"),
                String.valueOf(this.getArrival()),
                String.valueOf(this.getDeparture()),
                String.valueOf(this instanceof NodePK || this instanceof NodeDP ? this.getLatest() : "----"),
                String.valueOf(this instanceof NodePK || this instanceof NodeDP ? this.getArrival() - getEarliest() : "----"));
    }

    public int getDelay() {
        return delay;
    }

    public int getTripId() {
        return tripId;
    }

    public int getId() {
        return id;
    }

    public int getLoad() {
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

    public int getEarliest() {
        return earliest;
    }

    public void setEarliest(int earliest) {
        this.earliest = earliest;
    }

    public int getArrival() {
        return arrival;
    }

    public void setArrival(int arrival) {
        this.arrival = arrival;
        this.delay = arrival - earliest;
    }

    public int getLatest() {
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
        return Node.nodeDic.get(networkId)[0];
        //return Dao.getInstance().getLocation(networkId).getY();
    }

    public double getLon() {
        System.out.println(networkId);
        //return Dao.getInstance().getLocation(networkId).getX();
        return Node.nodeDic.get(networkId)[1];
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
}