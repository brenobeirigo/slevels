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

        super(stop.getId(), stop.getNetworkId());

        // What if a target re-enters? A new node target have to be formed, but the original node must be passed over.
        if (stop instanceof NodeTargetRebalancing) {
            this.setGenNode(((NodeTargetRebalancing) stop).getGenNode());
        } else {

            this.setGenNode(stop);

        }
        this.arrival = stop.getArrival();
        this.departure = stop.getDeparture();
        this.tripId = stop.getTripId();
        this.urgent = stop.urgent;
        this.hotness = stop.hotness;
    }

    @Override
    public String getType() {
        return "target";
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(int vehicleId) {
        this.vehicleId = vehicleId;
    }


    @Override
    public String toString() {
        // RM = Middle node becomes rebalancing target (happens when vehicle is disrupted)
        return String.format("%7s", (this.tripId < 0 ? "RM" + (this.tripId + Integer.MAX_VALUE) : "RE" + this.tripId));
    }

    /**
     * Get node that became target.
     *
     * @return Node
     */
    public Node getGenNode() {
        return genNode;
    }

    /**
     * Verify if target node (i.e., the node that generated the relocation operation) was reached (valid arrival time)
     *
     * @return True, if target was reached
     */
    public boolean isReached() {
        return this.genNode.getArrival() > 0;
    }

    public void setGenNode(Node genNode) {
        this.genNode = genNode;
    }
}