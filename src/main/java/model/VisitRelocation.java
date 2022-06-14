package model;

import dao.Dao;
import model.node.Node;
import model.node.NodeTargetRebalancing;

import java.util.*;
public class VisitRelocation extends Visit {

    protected Node targetNode;
    protected int targetArrival;

    /**
     * If Vehicle is parked:
     * - Departure = decision time
     * If vehicle is displacing users (i.e., cruising to pick up)
     * - Departure = time vehicle left last visited node (e.g., origin (dep=t1) -----> v1 --------> Target (middle) )
     *
     * @param targetNode Where vehicle is going to
     * @param vehicle    Vehicle that will rebalance
     */
    public VisitRelocation(Node targetNode, Vehicle vehicle) {
        // Create a target node for rebalancing
        NodeTargetRebalancing target = new NodeTargetRebalancing(vehicle, targetNode);
        // Time vehicle left the previous node
        Node start = null;
        int distOM = 0;
        this.departure = vehicle.getEarliestDeparture();
        if (vehicle.isParked()) {
            start = vehicle.getLastVisitedNode();
        } else {
            start = vehicle.getMiddleNode();
//            this.departure = vehicle.getMiddleNode().getEarliestDeparture();
            distOM = Dao.getInstance().getDistSec(vehicle.getLastVisitedNode(), start);
        }

        this.targetNode = target;
        this.vehicle = vehicle;

        this.sequenceVisits = new LinkedList<>();
        List<Integer> path = Dao.getInstance()
                .getShortestPathBetween(
                        start.getNetworkId(),
                        target.getNetworkId());

//        System.out.println(" aaa" + String.join(" ,", Arrays.asList(Dao.ZONE_ID_SET).stream().map(a -> String.valueOf(a)).collect(Collectors.toList())));
//        Set<Integer> subset = Sets.intersection(new HashSet<>(path), Dao.ZONE_ID_SET);
//        System.out.println(subset);
        int dep = start.getEarliestDeparture();

        for (int i = 0; i < path.size() - 2; i++) {
            dep = dep + Dao.getInstance().getDistSec(path.get(i), path.get(i + 1));

            NodeSP waypointNode = new NodeSP(path.get(i + 1));
            waypointNode.setEarliestDeparture(dep);
            waypointNode.setArrivalSoFar(dep);
            waypointNode.setEarliest(dep);
            this.sequenceVisits.add(waypointNode);
//            this.arrivals.get(dep + Dao.getInstance().getDistSec(this.path.get(i), this.path.get(i + 1)));
        }


        this.idle = 0;
        this.delay = distOM + Dao.getInstance().getDistSec(start, target);
        this.delayBonus = 0;

        // Arrival at target node (latest time at vehicle current node + distance to target)
        this.targetArrival = vehicle.getEarliestDeparture() + Dao.getInstance().getDistSec(vehicle.getLastVisitedNode(), target);
    }

    public int getArrivalTimeAtNext() {
        return this.targetArrival;
    }

    public Node getTargetNode() {
        return this.targetNode;
    }
}
