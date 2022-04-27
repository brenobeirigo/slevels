package model.node;

public class NodeStop extends Node {

    // Vehicle that generated node Stop
    private int vehicleId;

    /**
     * Created when vehicle parks somewhere.
     * @param stop Node where vehicle stopped.
     * @param vehicleId Vehicle that created stop.
     * @param earliestDeparture Time when vehicle reaches stop node.
     */
    public NodeStop(Node stop, int vehicleId, int earliestDeparture) {
        super(stop.getId(), stop.getNetworkId());
        this.vehicleId = vehicleId;
        this.tripId = stop.getTripId();

        this.load = 0;
        this.arrival = stop.getArrival();
        this.arrivalSoFar = stop.getArrival();
        this.earliest = stop.getArrival();
        this.latest = Integer.MAX_VALUE;

        this.earliestDeparture = earliestDeparture;
        this.departure = null;

        this.delay = 0;
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