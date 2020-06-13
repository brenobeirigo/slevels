package model.node;

import java.awt.geom.Point2D;
import java.util.Map;

public class NodeNetwork extends Node {

    private Map<Integer, Integer> closestRegionCenter;


    private Point2D point;

    public NodeNetwork(int id, Point2D point, Map<Integer, Integer> centerNodeId) {
        super(id, id);
        this.closestRegionCenter = centerNodeId;
        this.point = point;
    }

    @Override
    public String getType() {
        return null;
    }

    public Point2D getPoint() {
        return point;
    }

    public Map<Integer, Integer> getClosestRegionCenter() {
        return closestRegionCenter;
    }
}

