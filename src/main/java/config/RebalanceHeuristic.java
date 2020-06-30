package config;

import dao.Dao;
import model.Vehicle;
import model.node.Node;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class RebalanceHeuristic implements RebalanceStrategy {

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
                    if (config.clearTargetListEachRound) {
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

    }
