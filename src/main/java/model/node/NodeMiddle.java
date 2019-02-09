package model.node;

public class NodeMiddle extends Node {

    public static int nodeMiddleIds = Integer.MIN_VALUE;
    private Node nodeTo;
    private int elapsed;
    private Node nodeFrom;

    public NodeMiddle(int networkId, Node nodeFrom, Node nodeTo, int elapsed) {
        super(nodeMiddleIds, networkId);
        nodeMiddleIds++;
        this.tripId = nodeMiddleIds;
        this.nodeFrom = nodeFrom;
        this.nodeTo = nodeTo;
        this.elapsed = elapsed;
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
        return String.format("%7s", "MI" + String.valueOf(Math.abs(this.networkId)));
    }
}
