package model.node;

public class NodeTargetRebalancing extends Node {

    private int vehicleId;
    private Node genNode;

    public NodeTargetRebalancing(Node stop, int vehicleId) {
        super(stop.getId(),
                stop.getNetworkId());

        this.arrival = stop.getArrival();
        this.tripId = stop.getTripId();
        this.vehicleId = vehicleId;
    }


    public NodeTargetRebalancing(Node stop) {
        super(stop.getId(),
                stop.getNetworkId());

        this.setGenNode(stop);
        this.arrival = stop.getArrival();
        this.departure = stop.getDeparture();
        this.tripId = stop.getTripId();
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }


    @Override
    public String toString() {
        return String.format("%7s", "RE" + String.valueOf(this.tripId));
    }


    public Node getGenNode() {
        return genNode;
    }

    public void setGenNode(Node genNode) {
        this.genNode = genNode;
    }
}