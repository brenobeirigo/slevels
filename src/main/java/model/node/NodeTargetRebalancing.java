package model.node;

import dao.Dao;
import model.Vehicle;

public class NodeTargetRebalancing extends Node {

    private int vehicleId;
    private Node genNode;

    public NodeTargetRebalancing(Vehicle vehicle, int networkId){
        super(networkId);
        this.tripId = -1;
        this.load = 0;
        int distToTarget = Dao.getInstance().getDistSec(vehicle.getLastVisitedNode().getNetworkId(), networkId);
        this.earliest = vehicle.getEarliestDeparture() + distToTarget;
        this.latest = Integer.MAX_VALUE;
        this.earliestDeparture = earliest;
        this.arrivalSoFar = earliest;

        this.departure = null;

        this.delay = 0;
        this.maxDelay = 0;


    }
    public NodeTargetRebalancing(Vehicle vehicle, Node target) {
        super(target.getId(), target.getNetworkId());
        this.tripId = target.getTripId();

        this.load = 0;

        int distToTarget = Dao.getInstance().getDistSec(vehicle.getLastVisitedNode(), target);
        this.earliest = vehicle.getEarliestDeparture() + distToTarget;
        this.latest = Integer.MAX_VALUE;
        this.earliestDeparture = earliest;
        this.arrivalSoFar = earliest;

         this.departure = null;

        this.urgent = target.urgent;
        this.hotness = target.hotness;

        this.setGenNode(target);
        this.delay = 0;
        this.maxDelay = 0;
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
        return String.format("%10s", (isTargetMiddleNode() ? "RM" + (this.tripId + Integer.MAX_VALUE) : "RE" + this.tripId));
    }

    /**
     * When target is a middle node, visit was a stop visit where vehicle became empty as previous users were displaced.
     * @return
     */
    private boolean isTargetMiddleNode() {
        return this.tripId < 0;
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
        // What if a target re-enters? A new node target have to be formed, but the original node must be passed over.
        //TODO check if this holds
        if (genNode instanceof NodeTargetRebalancing) {
            this.genNode = ((NodeTargetRebalancing) genNode).getGenNode();
        } else {
            this.genNode = genNode;

        };
    }
}