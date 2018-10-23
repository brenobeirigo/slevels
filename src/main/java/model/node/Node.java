package model.node;

import java.util.HashMap;
import java.util.Map;

public class Node implements Comparable<Node> {

    public static final int MAX_NUMBER_NODES = 10000000;
    // Coordinate dictionaries
    public static Map<Integer, Double[]> nodeDic = new HashMap<>();
    protected int networkId, id;
    protected int earliest, arrival, latest;
    protected int load;
    protected int delay;
    protected int tripId;


    public Node(int id, int networkId) {
        this.id = id;
        this.networkId = networkId;
    }

    public Node(Node n) {
        this.id = n.id;
        this.load = n.load;
        this.networkId = n.networkId;
        this.earliest = n.earliest;
        this.latest = n.latest;
        this.arrival = n.arrival;
        this.tripId = n.tripId;

    }

    public Node(int id, int networkId, double lat, double lon, int earliest, int latest, int load) {
        this.id = id;
        this.load = load;
        this.networkId = networkId;
        this.earliest = earliest;
        this.latest = latest;
        Node.nodeDic.put(networkId, new Double[]{lat, lon});
    }

    public Node(int id, int networkId, int earliest, int latest, int load) {
        this.id = id;
        this.load = load;
        this.networkId = networkId;
        this.earliest = earliest;
        this.latest = latest;
        Node.nodeDic.put(networkId, new Double[]{0.0, 0.0});
    }

    public String getInfo() {
        return String.format(
                "%7s (%4s | %4s | %4s) [%4s]",
                this,
                String.valueOf(this.getEarliest()),
                String.valueOf(this.getArrival()),
                String.valueOf(this.getLatest()),
                String.valueOf(this.getArrival() - getEarliest()));
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

    @Override
    public int compareTo(Node o) {
        return this.id - o.id;
    }

    public double getLat() {
        return Node.nodeDic.get(networkId)[0];
    }

    public double getLon() {
        return Node.nodeDic.get(networkId)[1];
    }
}