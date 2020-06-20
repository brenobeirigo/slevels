package model.node;

public class NodeStop extends Node {

    // Vehicle that generated node Stop
    private int vehicleId;

    public NodeStop(Node stop, int vehicleId, int minDeparture) {
        super(stop.getId(), stop.getNetworkId());


        this.arrival = stop.getArrival();
        this.tripId = stop.getTripId();
        this.vehicleId = vehicleId;
        this.departure = minDeparture;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }

    @Override
    public String toString() {

        // SM = Stop in middle (happens when vehicle is disrupted), ST = Stop in destination
        return String.format("%7s", (this.tripId < 0 ? "SM" + (this.tripId + Integer.MAX_VALUE) : "ST" + this.tripId));
    }

    @Override
    public String getType() {
        return "stop";
    }
}