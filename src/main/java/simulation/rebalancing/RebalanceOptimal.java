package simulation.rebalancing;

import config.Config;
import dao.Dao;
import dao.Logging;
import gurobi.*;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeTargetRebalancing;

import java.util.List;
import java.util.Set;

public class RebalanceOptimal implements RebalanceStrategy {

    public RebalanceOptimal() {
    }

    private static GRBEnv env;

    private void initGurobiEnv() {
        if (env == null) {
            // Model
            try {
                env = new GRBEnv();
                if (Config.showRoundMIPInfo()) {
                    env.set(GRB.IntParam.OutputFlag, 1);
                } else {
                    // Turn off logging
                    env.set(GRB.IntParam.OutputFlag, 0);
                }
            } catch (GRBException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void rebalance(Set<Vehicle> idleVehicles, List<Node> targets, Rebalance config) {


        try {

            if (targets == null || targets.isEmpty())
                return;

            /*Logging.logger.info("Targets: " + targets.size());
            Logging.logger.info("Vehicles: " + idleVehicles.size());
            Logging.logger.info("TARGETS=" + nTargets + " -- VEHICLES:" + nIdleVehicles);*/


            // Model
            initGurobiEnv();

            GRBModel model = new GRBModel(env);
            model.set(GRB.StringAttr.ModelName, "assignment_vehicles_to_rejected");

            // Assignment variables: x[v][n] == 1 if vehicle v is assigned to node n.
            GRBVar[][] x = new GRBVar[idleVehicles.size()][targets.size()];

            int v=0, n = 0;

            // Vehicle conservation ////////////////////////////////////////////////////////////////////////////////////
            GRBLinExpr constrMaxNumberOfRelocations = new GRBLinExpr();

            for (Vehicle vehicle : idleVehicles) {
                n = 0;

                GRBLinExpr constrVehicleConservation = new GRBLinExpr();

                for (Node target : targets) {

                    double distance_vehicle_target = Dao.getInstance().getDistKm(vehicle.getLastVisitedNode(), target);
                    //String label = String.format("%d_%d_%d_%d", v ,vehicle.getId(), n ,target.getTripId());
                    String label = String.format("%d_%d", v, n);
                    x[v][n] = model.addVar(0, 1, distance_vehicle_target, GRB.BINARY, label);
                    constrMaxNumberOfRelocations.addTerm(1, x[v][n]);
                    constrVehicleConservation.addTerm(1, x[v][n]);
                    n++;
                }

                // A vehicle can visit at most a target
                model.addConstr(constrVehicleConservation, GRB.LESS_EQUAL, 1, "assignment_" + vehicle);
                v++;
            }

            // Request conservation ////////////////////////////////////////////////////////////////////////////////////
            n = 0;
            for (Node target : targets) {
                GRBLinExpr constrTargetConservation = new GRBLinExpr();
                for (v = 0; v < idleVehicles.size(); v++) {
                    constrTargetConservation.addTerm(1, x[v][n]);
                }

                // A target can be visited by at most one vehicle
                model.addConstr(constrTargetConservation, GRB.LESS_EQUAL, 1, "node_" + n + "_" + target.getTripId());
                n++;
            }

            // N. of simulation.rebalancing trips does not surpass number of vehicles or targets
            model.addConstr(constrMaxNumberOfRelocations, GRB.EQUAL, Math.min(targets.size(), idleVehicles.size()), "n_rebalancing_trips");

            // The objective is to minimize the total pay costs
            model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);

            // Optimize
            model.optimize();
            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.Status.UNBOUNDED) {
                Logging.logger.info("The model cannot be solved because it is unbounded");
                return;
            }
            if (status == GRB.Status.OPTIMAL) {
                if (config.showInfo) {
                    Logging.logger.info("The optimal objective is " + model.get(GRB.DoubleAttr.ObjVal));
                    Logging.logger.info("Optimal simulation.rebalancing:");
                }
                v = 0;
                for (Vehicle vehicle : idleVehicles) {
                    n = 0;

                    for (Node target : targets) {

                        if (x[v][n].get(GRB.DoubleAttr.X) > 0.99) {

                            // Make vehicle move to target
                            if (vehicle.getLastVisitedNode().getNetworkId() != target.getNetworkId()) {

                                vehicle.rebalanceTo(target);
                                //Logging.logger.info("-->>>>>" + label + " " + result + x[v][n] + " = " + distance + "distance2=" + distance2);

                            }
                            //else {
                            //Logging.logger.info("-->>>>>" + label + " " + result + names.get(v).get(n) + " = " + distance + "distance2=" + distance2);
                            //}
                        }
                        n++;
                    }
                    v++;
                }
                return;
            }
            if (status != GRB.Status.INF_OR_UNBD && status != GRB.Status.INFEASIBLE) {
                Logging.logger.info("Optimization was stopped with status " + status);
                return;
            }

            // Compute IIS
            Logging.logger.info("The model is infeasible; computing IIS");
            model.computeIIS();
            Logging.logger.info("\nThe following constraint(s) "
                    + "cannot be satisfied:");
            for (GRBConstr c : model.getConstrs()) {
                if (c.get(GRB.IntAttr.IISConstr) == 1) {
                    Logging.logger.info(c.get(GRB.StringAttr.ConstrName));
                }
            }

            // Dispose of model and environment
            model.dispose();
            // env.dispose();

        } catch (GRBException e) {
            Logging.logger.info("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
    }

    public void interruptRebalancing(Vehicle vehicle, int timeWindow, boolean episode, boolean createEpisode) {

        Node currentNode = vehicle.getLastVisitedNode();
        Node middleNode = vehicle.getMiddleNode();

        // Vehicle is no longer simulation.rebalancing (User was inserted)
        vehicle.stoppedRebalancingToPickup();

        assert middleNode != null;

        double distTraveledKmCurrentMiddle = Dao.getInstance().getDistKm(currentNode, middleNode);

        vehicle.increaseDistanceTraveledRebalancing(distTraveledKmCurrentMiddle);

    }


    @Override
    public String toString() {
        return "_RE-OP";
    }
}
