package model;

import dao.Dao;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;

import java.util.Comparator;

public class Leg implements Comparable<Leg> {
    public Node fromNode;
    public Node nextNode;
    public int arrivalNext;
    public int load;
    public int delay;
    public int idleness;
    public int totalVisitSize;
    public Vehicle vehicle;


    public static Comparator<Leg> legComparator = Comparator.nullsLast(
            Comparator.comparing(Leg::getDelay).thenComparing(Leg::getTotalVisitSize).thenComparing(Leg::getIdleness));

    /**
     * Leg always start from a vehicle's last visited node
     * @param vehicle Vehicle carrying out the leg
     */
    public Leg(Vehicle vehicle) {
        this.vehicle = vehicle;
        this.fromNode = vehicle.getLastVisitedNode();
        this.arrivalNext = this.fromNode.getDeparture();
        this.load = this.vehicle.getCurrentLoad();
    }

    public int getTotalVisitSize() {
        return totalVisitSize;
    }

    public void setTotalVisitSize(int totalVisitSize) {
        this.totalVisitSize = totalVisitSize;
    }

    /**
     * Update leg's next node and check if current leg is feasible.
     * CAUTION! This method assumes a VALID PUDO sequence (pickups before respective drop-offs)
     *
     * @param nextNode Next node in VALID visiting sequence.
     * @return True, if leg is valid regarding load, sharing, and time constraints.
     */
    public boolean updateNextNode(Node nextNode) {

        this.nextNode = nextNode;
        this.totalVisitSize +=1;

        if (!updateLoad()) return false;
        if (!updateArrivalNextDelayAndIdleness()) return false;
        if (!isSoloRideRespected()) return false;
        if (!isArrivalWithinContractDeadline()) return false;

        // TODO add service time to arrival
        this.fromNode = this.nextNode;

        return true;
    }

    private boolean isArrivalWithinContractDeadline() {
        return this.arrivalNext <= vehicle.getContractDeadline();
    }

    private boolean updateLoad() {
        // Update loads (DP nodes have negative loads)
        this.load = this.load + this.nextNode.getLoad();

        // Capacity constraint
        return this.load >= 0 && this.load <= vehicle.getCapacity();
    }

    private boolean updateArrivalNextDelayAndIdleness() {
        int distFromTo = Dao.getInstance().getDistSec(fromNode, nextNode);

        // No path available
        if (distFromTo >= 0) {
            // Time vehicle arrives at next node (can be earlier or later)
            // If distance is zero, arrival next MUST be at least earliest time at next node
            int arrivalNext = this.arrivalNext + distFromTo;

            if (arrivalNext <= nextNode.getLatest()){

                this.arrivalNext = Math.max(arrivalNext, nextNode.getEarliest());

                if (nextNode instanceof NodeDP)
                    this.delay += this.arrivalNext - nextNode.getEarliest();

                else if (nextNode instanceof NodePK && arrivalNext < nextNode.getEarliest())
                    this.idleness += nextNode.getEarliest() - arrivalNext;


                return true;
            }
            return false;
        }
        return false;
    }

    private boolean isSoloRideRespected() {
        if (!(fromNode instanceof NodePK)) {
            return true;
        }

        User uFrom = User.mapOfUsers.get(fromNode.getTripId());
        return uFrom.isSharingAllowed() || nextNode.getTripId() == fromNode.getTripId();
    }


    @Override
    public int compareTo(Leg o) {
        return legComparator.compare(this, o);
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getIdleness() {
        return idleness;
    }

    public void setIdleness(int idleness) {
        this.idleness = idleness;
    }

}
