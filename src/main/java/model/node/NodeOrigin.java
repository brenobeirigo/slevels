package model.node;


public class NodeOrigin extends Node {


    public NodeOrigin(int vehicleId, int networkId, double lat, double lon) {
        super(vehicleId, networkId, lat, lon, 0, Integer.MAX_VALUE);
        this.load = 0;
        this.tripId = vehicleId;
        this.delay = 0;
        this.departure = null;
        this.arrival = this.earliest;
        this.arrivalSoFar = this.earliest;
        this.maxDelay = 0;
    }

    public NodeOrigin(int id, int id_network) {
        super(id, id_network, 0, Integer.MAX_VALUE);
        this.load = 0;
        this.tripId = id;
        this.delay = 0;
        this.departure = null;
        this.arrival = this.earliest;
        this.arrivalSoFar = this.earliest;
        this.maxDelay = 0;
    }

    @Override
    public String toString() {
        return String.format("%10s", "OR" + String.valueOf(this.getId() - Node.MAX_NUMBER_NODES * 2));
    }

    @Override
    public String getType() {
        return "origin";
    }
}