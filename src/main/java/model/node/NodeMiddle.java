package model.node;

public class NodeMiddle extends Node {
    // Negative to differentiate from trip ids
    public static int nodeMiddleIds = Integer.MIN_VALUE;
    private Node nodeTo;
    private int elapsedSinceLeftOriginNode;
    private Node nodeFrom;

    /**
     * Create waypoint between two points (origin and destination) at current time.
     * @param networkIdMiddleNode
     * @param nodeOrigin
     * @param nodeDestination
     * @param distOriginToMiddle
     */
    public NodeMiddle(int networkIdMiddleNode, Node nodeOrigin, Node nodeDestination, int distOriginToMiddle) {
        super(nodeMiddleIds, networkIdMiddleNode);
        nodeMiddleIds++;
        this.tripId = nodeMiddleIds;
        this.load = 0;
        this.delay = 0;

        this.nodeFrom = nodeOrigin;
        this.nodeTo = nodeDestination;

        this.earliest = nodeOrigin.getDeparture() + distOriginToMiddle;
        this.earliestDeparture = this.earliest;
        this.arrivalSoFar = this.earliest;
        this.latest = Integer.MAX_VALUE;
        this.maxDelay = 0;

        // Only changes when node is visited
        this.arrival = null;
        this.departure = null;
    }

    public NodeMiddle(int networkIdMiddleNode, int earliest, Node nodeOrigin, Node nodeDestination) {
        super(nodeMiddleIds, networkIdMiddleNode);
        nodeMiddleIds++;
        this.tripId = nodeMiddleIds;
        this.load = 0;
        this.delay = 0;

        this.nodeFrom = nodeOrigin;
        this.nodeTo = nodeDestination;

        this.earliest = earliest;
        this.earliestDeparture = this.earliest;
        this.arrivalSoFar = this.earliest;
        this.latest = Integer.MAX_VALUE;
        this.maxDelay = 0;

        // Only changes when node is visited
        this.arrival = null;
        this.departure = null;
    }

    public Node getNodeFrom() {
        return nodeFrom;
    }

    public Node getNodeTo() {
        return nodeTo;
    }

    public static void reset() {
        nodeMiddleIds = Integer.MIN_VALUE;
    }

    @Override
    public String getType() {
        return "middle";
    }

    @Override
    public String toString() {
        return String.format("%10s -[%s -> %s]", "MI" + String.valueOf(Math.abs(this.networkId)), this.nodeFrom, this.nodeTo);
    }
}
