package model.node;

public class NodeStop extends Node {

    private int vehicleId;

    public NodeStop(Node stop, int vehicleId) {
        super(stop.getId(),
                stop.getNetworkId());

        this.arrival = stop.getArrival();
        this.tripId = stop.getTripId();
        this.vehicleId = vehicleId;
        this.departure = stop.getDeparture();
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    @Override
    public String toString() {
        return String.format("%7s", "ST" + String.valueOf(this.tripId));
    }

    @Override
    public String getType() {
        return "stop";
    }
}