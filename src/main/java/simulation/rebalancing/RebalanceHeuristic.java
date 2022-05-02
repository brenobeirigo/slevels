package simulation.rebalancing;

import dao.Dao;
import model.RebalanceEpisode;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeTargetRebalancing;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class RebalanceHeuristic implements RebalanceStrategy {

    public boolean allowManyToOneTarget;
    public boolean reinsertTargets;
    public boolean clearTargetListEachRound;
    public boolean useUrgentKey;

    private long totalExecutionTimeNanoTime;


    public RebalanceHeuristic(boolean allowManyToOneTarget,
                              boolean reinsertTargets,
                              boolean clearTargetListEachRound,
                              boolean useUrgentKey) {
        this.allowManyToOneTarget = allowManyToOneTarget;
        this.reinsertTargets = reinsertTargets;
        this.clearTargetListEachRound = clearTargetListEachRound;
        this.useUrgentKey = useUrgentKey;
    }

    @Override
    public String toString() {

        String str = "";
        str += clearTargetListEachRound ? "_CT" : "";
        str += allowManyToOneTarget ? "_MO" : "";
        str += reinsertTargets ? "_RT" : "";
        str += useUrgentKey ? "_UR" : "";

        return str;
    }

    /**
     * Receive the fleet of vehicles and a static set of "hot points", that is, pick up misses.
     * Only vehicles idle for maxRoundsIdleBeforeRebalance are considered.
     * <p>
     * The vehicle idle for the longest time will be assign to a customer.
     * <p>
     * tiebreaker is distance (closer vehicles before)
     *
     * @param idleVehicles List of vehicles operating
     */

    @Override
    public void rebalance(Set<Vehicle> idleVehicles, List<Node> targets, Rebalance config) {

        // System.out.println("Rebalancing");

        // Missed pickup nodes are sorted according to their attractiveness
        // 1st - Service level fail
        // 2nd - Historical attractiveness
        Collections.sort(Vehicle.setOfHotPoints);

        if (config.showInfo)
            System.out.println("Attractive points: " + Vehicle.setOfHotPoints);


        while (!Vehicle.setOfHotPoints.isEmpty()) {

            // If all idle vehicle were rebalanced
            if (idleVehicles.isEmpty()) {
                // Erase current hot points (ignore relocation targets)
                if (this.clearTargetListEachRound) {
                    Vehicle.setOfHotPoints.clear();
                }
                return;
            }

            // Get next hot point in sequence
            Node target = Vehicle.setOfHotPoints.poll();

            // Closest vehicle to candidate hot point (a previous miss)
            Vehicle rebalancingVehicle = getBestVehicleToServiceTarget(idleVehicles, target);

            //Closest vehicle is found
            if (rebalancingVehicle != null) {

                // Make vehicle move to target
                rebalancingVehicle.rebalanceTo(target);

                // Vehicle is no longer idle
                idleVehicles.remove(rebalancingVehicle);

                //System.out.println(Node.tabu.size() + " - " +  Node.tabu);
                if (config.showInfo) {
                    String logRelocation = getRebalancingLog(idleVehicles, target, rebalancingVehicle);
                    System.out.println(logRelocation);
                }
            }

        }
        //System.out.println("Addressed: " + addressedHotNodes.size() + " - - Hot: " + Vehicle.setOfHotPoints.size());
        //Vehicle.setOfHotPoints.removeAll(addressedHotNodes);
    }


    public Vehicle getBestVehicleToServiceTarget(Set<Vehicle> idleVehicles, Node hotPoint) {

        // Closest vehicle to candidate hot point (a previous miss)
        Vehicle idlestClosestVehicle;

        // If hot point is urgent (increaseUrgency method), send closest vehicle
        if (hotPoint.getUrgent() > 0) {
            idlestClosestVehicle = idleVehicles.stream().min(Comparator.comparing(a -> Dao.getInstance().getDistSec(hotPoint, a.getLastVisitedNode()))).get();
            //If hot point is not urgent, send vehicle idle for the most time and untie by closest distance
        } else {
            idlestClosestVehicle = idleVehicles.stream().max(Comparator
                    .comparing(Vehicle::getRoundsIdle)
                    .thenComparing(a -> Dao.getInstance().getDistSec(hotPoint, a.getLastVisitedNode()), Comparator.reverseOrder())).get();
        }
        /*System.out.println("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFff");
        for(Vehicle v:idleVehicles){
            System.out.println(v + " - " + v.getRoundsIdle() + " - " + "urgent:"+ hotPoint.getUrgent() + " = " + Dao.getInstance().getDistSec(
                    hotPoint,
                    v.getLastVisitedNode()) + (v==idlestClosestVehicle? " ---- HERE":""));
        }*/

        return idlestClosestVehicle;

        // If hotpoint is urgent, get closest
        /*if (hotPoint.getUrgent() > 0) {

            //System.out.println("Addressing urgent...");

            while (it.hasNext()) {

                Vehicle candidateRebalancingVehicle = it.next();

                // Untie using shortest distance
                int distanceCandidate = Dao.getInstance().getDistSec(hotPoint, candidateRebalancingVehicle.getLastVisitedNode());
                int distanceIncumbent = Dao.getInstance().getDistSec(hotPoint, idlestClosestVehicle.getLastVisitedNode());

                // Untie with distance
                if (distanceCandidate < distanceIncumbent && distanceCandidate > 0) {
                    idlestClosestVehicle = candidateRebalancingVehicle;
                }
            }
        } else {
            while (it.hasNext()) {

                Vehicle candidateRebalancingVehicle = it.next();

                // Get the idlest and closest vehicle to hot point
                if (candidateRebalancingVehicle.getRoundsIdle() >= idlestClosestVehicle.getRoundsIdle()) {

                    if (candidateRebalancingVehicle.getRoundsIdle() == idlestClosestVehicle.getRoundsIdle()) {

                        // Untie using shortest distance
                        int distanceCandidate = Dao.getInstance().getDistSec(hotPoint, candidateRebalancingVehicle.getLastVisitedNode());
                        int distanceIncumbent = Dao.getInstance().getDistSec(hotPoint, idlestClosestVehicle.getLastVisitedNode());

                        // Untie with distance
                        if (distanceCandidate < distanceIncumbent && distanceCandidate > 0) {
                            idlestClosestVehicle = candidateRebalancingVehicle;
                        }
                    } else {
                        idlestClosestVehicle = candidateRebalancingVehicle;
                    }
                }
            }
        }

        return idlestClosestVehicle;*/

    }


    private String getRebalancingLog(Set<Vehicle> idleVehicles, Node target, Vehicle rebalancingVehicle) {

        int distToTarget = Dao.getInstance().getDistSec(rebalancingVehicle.getLastVisitedNode(), target);
        Node currentNode = rebalancingVehicle.getLastVisitedNode();
        int historicalScore = Node.hotSpot.get(target.getNetworkId());

        return rebalancingVehicle +
                "( #Rounds idle: " + rebalancingVehicle.getRoundsIdle() + ") [ #Targets: " + Vehicle.setOfHotPoints.size() +
                "] #Idle vehicles: (" + idleVehicles.size() + ")   " +
                "   Visit: " + rebalancingVehicle.getVisit() +
                "--" +
                currentNode + "[" + rebalancingVehicle.getLastVisitedNode().getInfo() + "]" +
                " -> " + distToTarget + " -> " +
                target + "[" + target.getNetworkId() + "]  (Urgent=" + target.getUrgent() + ", Historical=" + historicalScore + "): [" + target.getInfo() + "]";
    }

    /**
     * Test whether it is beneficial send different vehicles to service users in the same locations.
     *
     * @param u
     */
    public void computeAttractivenessLocationUser(User u) {

        Node pk = u.getNodePk();

        // This node should have more vehicles around it
        pk.increaseHotness();


        if (!this.allowManyToOneTarget) {
            // Vehicles are sent to the same attractive places in the network repeatedly
            Vehicle.setOfHotPoints.add(pk);

            // Do not send vehicles to the same places
        } else if (!Node.tabu.contains(pk.getNetworkId())) {
            // Only add points not yet being addressed by other vehicles
            Vehicle.setOfHotPoints.add(pk);
        }
    }

    @Override
    public void interruptRebalancing(Vehicle vehicle, int timeWindow, boolean showInfo, boolean createEpisode) {

        Node currentNode = vehicle.getLastVisitedNode();
        Node middleNode = vehicle.getMiddleNode();
        NodeTargetRebalancing target = (NodeTargetRebalancing) vehicle.getVisit().getTargetNode();

        // Target was not reached, it goes back to attractive points
        if (this.reinsertTargets && !target.isReached()) {

            //TODO Does re-adding the node helps? It looks like YES!
            //System.out.println("STOPPED REB.:" + target.getGenNode().getUrgent() + " - " + target.getGenNode().getArrival() + " :" + this.getSequenceVisits().getFirst() + "-"+target+"-" + target.getGenNode());

            // If a node was not reached, it means vehicles keep being assigned around its region.
            // The immediate demand factor is therefore incremented to keep attracting vehicles to this region
            if (this.useUrgentKey)
                target.increaseUrgency();

            makeTargetRebalancingCandidate(target);
        }

        // Vehicle is no longer simulation.rebalancing (User was inserted)
        vehicle.stoppedRebalancingToPickup();

        if (middleNode == null)
            System.out.println("Middle node");

        double distTraveledKmCurrentMiddle = Dao.getInstance().getDistKm(currentNode, middleNode);

        vehicle.increaseDistanceTraveledRebalancing(distTraveledKmCurrentMiddle);

        // Create structure that helps to understand what is happening in the simulation.rebalancing process
        if (createEpisode) {

            int roundsToFindUser = (Dao.getInstance().getDistSec(currentNode, middleNode) / timeWindow);

            double distTraveledKm = Dao.getInstance().getDistKm(currentNode, target);

            RebalanceEpisode r = new RebalanceEpisode(
                    currentNode.getNetworkId(),
                    target.getNetworkId(),
                    middleNode.getNetworkId(),
                    vehicle.getRoundsIdle(),
                    distTraveledKmCurrentMiddle,
                    distTraveledKm,
                    roundsToFindUser);

            if (showInfo) {
                System.out.println(r);
            }
        }
    }

    private void makeTargetRebalancingCandidate(NodeTargetRebalancing target) {
        Vehicle.setOfHotPoints.add(target);
    }
}

