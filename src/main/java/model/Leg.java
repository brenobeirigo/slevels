package model;

import dao.Dao;
import model.node.Node;
import model.node.NodeDP;
import model.node.NodePK;
import simulation.Simulation;

import java.util.Comparator;

public class Leg implements Comparable<Leg> {
    public int contractDeadline;
    public int maxCapacity;
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
        // If departure is null:
        // - Vehicle is parked => earliest departure
        this.arrivalNext = vehicle.getEarliestDeparture();
        this.load = this.vehicle.getCurrentLoad();
        this.maxCapacity = this.vehicle.getCapacity();
        this.contractDeadline = this.vehicle.getContractDeadline();
    }


    /**
     * Leg starting from first node of sequence assuming a vehicle can start from it immediately.
     * Used only in RV-graph request-request edge creation.
     *
     * Precondition:
     * - r1 and r2 are not picked up. So, the earliest time any vehicle can arrive at r1 is the current time.
     *
     * @param node First node of sequence (must be pickup node)
     */
    public Leg(Node node) {
        this.fromNode = node;
        this.arrivalNext = Math.max(Simulation.rightTW, this.fromNode.getEarliestDeparture());
        this.delay = this.arrivalNext - this.fromNode.getEarliest();
        this.load = this.fromNode.getLoad();
        this.maxCapacity = Integer.MAX_VALUE;
        this.contractDeadline = Integer.MAX_VALUE;
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
        this.totalVisitSize += 1;

        if (!updateLoad()) return false;
        if (!updateArrivalNextDelayAndIdleness()) return false;
        if (!isSoloRideRespected()) return false;
        if (!isArrivalWithinContractDeadline()) return false;

        // TODO add service time to arrival
        this.fromNode = this.nextNode;

        return true;
    }

    private boolean isArrivalWithinContractDeadline() {
        return this.arrivalNext <= this.contractDeadline;
    }

    private boolean updateLoad() {
        // Update loads (DP nodes have negative loads)
        this.load = this.load + this.nextNode.getLoad();

        // Capacity constraint
        return this.load >= 0 && this.load <= this.maxCapacity;
    }

    private boolean updateArrivalNextDelayAndIdleness() {
        int distFromTo = Dao.getInstance().getDistSec(fromNode, nextNode);

        // No path available
        if (distFromTo >= 0) {
            // Time vehicle arrives at next node (can be earlier or later)
            // If distance is zero, arrival next MUST be at least earliest time at next node
            this.arrivalNext = this.arrivalNext + distFromTo;
            assert this.arrivalNext >= Simulation.rightTW : String.format("From=%s / To=%s / Middle=%s / Simulation=%s / Arrival=%s", fromNode.getInfo(), nextNode.getInfo(), vehicle.getMiddleNode() != null ? vehicle.getMiddleNode().getInfo() : "null", Simulation.rightTW, arrivalNext);

            // If request is known, the system only knows about it after earliest departure
            if (this.arrivalNext >= nextNode.getEarliestDeparture() && this.arrivalNext <= nextNode.getLatest()) {

                // Idleness - Vehicle arrives at pickup location BEFORE request is placed
                // Happens only if requests are placed in advance
                if (nextNode instanceof NodePK && arrivalNext < nextNode.getEarliestDeparture()) {
                    this.idleness += nextNode.getEarliest() - arrivalNext;
                    //System.out.printf("Idleness: %s - NextNode: %s - Arrival next: %s ", this.idleness, nextNode.getEarliest(), this.arrivalNext);
                    assert this.idleness == 0;
                } else if (nextNode instanceof NodeDP) {
                    int currentDelay = this.arrivalNext - nextNode.getEarliest();
                    this.delay += currentDelay;
                    assert currentDelay >= 0 : String.format("NextNode: %s - Earliest: %s - New arrival next: %s", nextNode, nextNode.getEarliest(), this.arrivalNext);
                }

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
