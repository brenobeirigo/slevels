package config;

import dao.Dao;
import model.Vehicle;
import model.node.Node;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;

public class Rebalance {

    public boolean allowManyToOneTarget;
    public boolean reinsertTargets;
    public boolean clearTargetListEachRound;
    public boolean useUrgentKey;
    public String method;
    public boolean showInfo;
    public boolean createEpisode;

    public Rebalance(boolean allowManyToOneTarget,
                     boolean reinsertTargets,
                     boolean clearTargetListEachRound,
                     boolean useUrgentKey,
                     String method) {
        this.allowManyToOneTarget = allowManyToOneTarget;
        this.reinsertTargets = reinsertTargets;
        this.clearTargetListEachRound = clearTargetListEachRound;
        this.useUrgentKey = useUrgentKey;
        this.method = method;
    }


    public Rebalance(boolean allowManyToOneTarget,
                     boolean reinsertTargets,
                     boolean clearTargetListEachRound,
                     boolean useUrgentKey,
                     String method,
                     boolean show,
                     boolean createEpisode) {
        this.allowManyToOneTarget = allowManyToOneTarget;
        this.reinsertTargets = reinsertTargets;
        this.clearTargetListEachRound = clearTargetListEachRound;
        this.useUrgentKey = useUrgentKey;
        this.method = method;
        this.showInfo = show;
        this.createEpisode = createEpisode;
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
     * @param listIdle List of vehicles operating
     */
    public void rebalance(Set<Vehicle> listIdle) {
        // System.out.println("Rebalancing");

        // Missed pickup nodes are sorted according to their attractiveness
        // 1st - Service level fail
        // 2nd - Historical attractiveness
        Collections.sort(Vehicle.setOfHotPoints);

        if (showInfo)
            System.out.printf("Attractive points: " + Vehicle.setOfHotPoints);


        while (!Vehicle.setOfHotPoints.isEmpty()) {

            // If all idle vehicle were rebalanced
            if (listIdle.isEmpty()) {
                // Erase current hot points (ignore relocation targets)
                if (this.clearTargetListEachRound) {
                    Vehicle.setOfHotPoints.clear();
                }
                return;
            }

            // Get next hot point in sequence
            Node target = Vehicle.setOfHotPoints.poll();

            // Closest vehicle to candidate hot point (a previous miss)
            Vehicle rebalancingVehicle = getBestVehicleToServiceTarget(listIdle, target);

            //Closest vehicle is found
            if (rebalancingVehicle != null) {

                // Make vehicle move to target
                rebalancingVehicle.rebalanceTo(target);

                // Vehicle is no longer idle
                listIdle.remove(rebalancingVehicle);

                //System.out.println(Node.tabu.size() + " - " +  Node.tabu);
                if (showInfo) {
                    String logRelocation = getRebalancingLog(listIdle, target, rebalancingVehicle);
                    System.out.println(logRelocation);
                }
            }

        }
        //System.out.println("Addressed: " + addressedHotNodes.size() + " - - Hot: " + Vehicle.setOfHotPoints.size());
        //Vehicle.setOfHotPoints.removeAll(addressedHotNodes);
    }


    public Vehicle getBestVehicleToServiceTarget(Set<Vehicle> listIdle, Node hotPoint) {

        // Closest vehicle to candidate hot point (a previous miss)
        Vehicle idlestClosestVehicle;

        // If hot point is urgent (increaseUrgency method), send closest vehicle
        if (hotPoint.getUrgent() > 0) {
            idlestClosestVehicle = listIdle.stream().min(Comparator.comparing(a -> Dao.getInstance().getDistSec(hotPoint, a.getLastVisitedNode()))).get();
        //If hot point is not urgent, send vehicle idle for the most time and untie by closest distance
        } else {
            idlestClosestVehicle = listIdle.stream().max(Comparator
                .comparing(Vehicle::getRoundsIdle)
                .thenComparing(a -> Dao.getInstance().getDistSec(hotPoint, a.getLastVisitedNode()), Comparator.reverseOrder())).get();
    }
        /*System.out.println("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFff");
        for(Vehicle v:listIdle){
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


    private String getRebalancingLog(Set<Vehicle> listIdle, Node target, Vehicle rebalancingVehicle) {

        short distToTarget = Dao.getInstance().getDistSec(rebalancingVehicle.getLastVisitedNode(), target);
        Node currentNode = rebalancingVehicle.getLastVisitedNode();
        int historicalScore = Node.hotSpot.get(target.getNetworkId());

        return rebalancingVehicle +
                "( #Rounds idle: " + rebalancingVehicle.getRoundsIdle() + ") [ #Targets: " + Vehicle.setOfHotPoints.size() +
                "] #Idle vehicles: (" + listIdle.size() + ")   " +
                "   Visit: " + rebalancingVehicle.getVisit() +
                "--" +
                currentNode + "[" + rebalancingVehicle.getLastVisitedNode().getInfo() + "]" +
                " -> " + distToTarget + " -> " +
                target + "[" + target.getNetworkId() + "]  (Urgent=" + target.getUrgent() + ", Historical=" + historicalScore + "): [" + target.getInfo() + "]";
    }
}
