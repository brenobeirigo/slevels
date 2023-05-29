package model;

import dao.Dao;
import model.demand.User;
import model.node.Node;
import model.node.NodeDropoff;
import model.node.NodePickup;
import simulation.Environment;

import java.util.Comparator;

public class PathBuilder implements Comparable<PathBuilder> {
    private int currentTime;
    public Environment env;
    public int contractDeadline;
    public int maxCapacity;
    public Node fromNode;
    public Node nextNode;
    public int arrivalNext;
    public int load;
    public int delay;
    public int delayBonus;
    public int idleness;
    public int totalVisitSize;
    public Vehicle vehicle;


    public static Comparator<PathBuilder> legComparator = Comparator.nullsLast(
            Comparator.comparing(PathBuilder::getDelay).thenComparing(PathBuilder::getTotalVisitSize).thenComparing(PathBuilder::getIdleness));

    /**
     * Leg always start from a vehicle's last visited node
     * @param vehicle Vehicle carrying out the leg
     */
    public PathBuilder(Vehicle vehicle, Environment env) {
        this.env = env;
        this.vehicle = vehicle;
        this.fromNode = vehicle.getLastVisitedNode();
        // If departure is null:
        // - Vehicle is parked => earliest departure
        this.arrivalNext = vehicle.getEarliestDeparture();
        this.load = this.vehicle.getCurrentLoad();
        this.maxCapacity = this.vehicle.getCapacity();
        this.contractDeadline = this.vehicle.getContractDeadline();
        this.delay = 0;
        this.delayBonus = 0;
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
    public PathBuilder(Node node, int currentTime) {
        this.currentTime = currentTime;
        this.fromNode = node;
        this.arrivalNext = Math.max(currentTime, this.fromNode.getEarliestDeparture());

        // Only drop-off delays are accounted in delay (RV-graph RR sequences always start with pickup nodes)
        this.delay = 0;
        this.delayBonus = 0;
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
        if (!updateArrivalNextDelayRemainingAndIdleness()) return false;
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

    /**
     * Update arrival time, delay, and idleness of next node being visited in the sequence.
     * IMPORTANT: Delay is accounted only at the drop-off node
     * E.g.:
     * PK1 = e: 100, a:200, l:300 -- dist(PK1, DP1) = 500
     *
     * DP1 = e: 600, a:700, l:1000, ea: 700 -- delay = 100 (at pickup)
     * DP1 = e: 600, a:800, l:1000, ea: 700 -- delay = 200 (100 (pk) + 100 (dp))
     *
     * @return
     */
    private boolean updateArrivalNextDelayRemainingAndIdleness() {
        int distFromTo = env.getNetwork().getDistSec(fromNode.getNetworkId(), nextNode.getNetworkId());

        // No path available
        if (distFromTo >= 0) {
            // Time vehicle arrives at next node (can be earlier or later)
            // If distance is zero, arrival next MUST be at least earliest time at next node
            // Must be always later than current decision time!
            this.arrivalNext = Math.max(this.arrivalNext + distFromTo, this.currentTime);
//            assert this.arrivalNext >= Environment.currentTime : String.format("\nFrom=%s / \nTo=%s / \nMiddle=%s / \nEnvironment=%s / \nArrival=%s / \nDist=%s/\nVisit=%s/\nSeq=%s/\nTarget=%s\nTrack=%s, \nJourney=%s", env.getNetwork().getInfo(fromNode), env.getNetwork().getInfo(nextNode), vehicle.getMiddleNode() != null ? env.getNetwork().getInfo(vehicle.getMiddleNode()) : "null", Environment.currentTime, arrivalNext, distFromTo, vehicle.getVisit(), vehicle.getVisit().getSequenceVisits(), env.getNetwork().getInfo(vehicle.getVisit().getTargetNode()), vehicle.visitTrack, vehicle.getJourney());

            // If request is known, the system only knows about it after earliest departure
            if (this.arrivalNext >= nextNode.getEarliestDeparture() && this.arrivalNext <= nextNode.getLatest()) {

                // Idleness - Vehicle arrives at pickup location BEFORE request is placed
                // Happens only if requests are placed in advance
                if (nextNode instanceof NodePickup) {
                    if (arrivalNext < nextNode.getEarliestDeparture()) {
                        this.idleness += nextNode.getEarliest() - arrivalNext;
                        //Logging.logger.info("{}", String.format("Idleness: %s - NextNode: %s - Arrival next: %s ", this.idleness, nextNode.getEarliest(), this.arrivalNext));
                        assert this.idleness == 0;
                    }
                    this.delayBonus += nextNode.getLatest() - arrivalNext;
                } else if (nextNode instanceof NodeDropoff) {
                    int currentDelay = this.arrivalNext - nextNode.getEarliest();
                    this.delay += currentDelay;
                    this.delayBonus += nextNode.getLatest() - arrivalNext;
                    assert currentDelay >= 0 : String.format("NextNode: %s - Earliest: %s - New arrival next: %s", nextNode, nextNode.getEarliest(), this.arrivalNext);
                }

                return true;
            }
            return false;
        }
        return false;
    }

    private boolean isSoloRideRespected() {
        if (!(fromNode instanceof NodePickup)) {
            return true;
        }

        User uFrom = User.mapOfUsers.get(fromNode.getTripId());
        return uFrom.isSharingAllowed() || nextNode.getTripId() == fromNode.getTripId();
    }


    @Override
    public int compareTo(PathBuilder o) {
        return legComparator.compare(this, o);
    }

    public int getDelay() {
        return delay;
    }

    public int getDelayBonus() {
        return delayBonus;
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
