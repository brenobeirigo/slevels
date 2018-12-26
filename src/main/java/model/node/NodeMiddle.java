package model.node;

public class NodeMiddle extends Node {

    public static int nodeMiddleIds = Integer.MIN_VALUE;


    public NodeMiddle(int networkId) {
        super(nodeMiddleIds, networkId);
        nodeMiddleIds++;
        this.tripId = nodeMiddleIds;
    }

    public static void reset() {
        nodeMiddleIds = Integer.MIN_VALUE;
    }

    @Override
    public String toString() {
        return String.format("%7s", "MI" + String.valueOf(Math.abs(this.networkId)));
    }
}
