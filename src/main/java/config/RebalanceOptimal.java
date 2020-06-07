package config;

import dao.Dao;
import gurobi.*;
import model.Vehicle;
import model.node.Node;

import java.util.List;
import java.util.Set;

public class RebalanceOptimal implements RebalanceStrategy {
    @Override
    public void rebalance(Set<Vehicle> idleVehicles, List<Node> targets, Rebalance config) {

        try {

            if (targets == null || targets.isEmpty())
                return;

            /*System.out.println("Targets: " + targets.size());
            System.out.println("Vehicles: " + idleVehicles.size());
            System.out.println("TARGETS=" + nTargets + " -- VEHICLES:" + nIdleVehicles);*/

            // Model
            GRBEnv env = new GRBEnv();

            if(!config.showInfo){
                // Turn off logging
                env.set(GRB.IntParam.OutputFlag, 0);
            }

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
                    x[v][n] = model.addVar(0, 1, distance_vehicle_target, GRB.CONTINUOUS, label);
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

            // N. of rebalancing trips does not surpass number of vehicles or targets
            model.addConstr(constrMaxNumberOfRelocations, GRB.EQUAL, Math.min(targets.size(), idleVehicles.size()), "n_rebalancing_trips");

            // The objective is to minimize the total pay costs
            model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);

            // Optimize
            model.optimize();
            int status = model.get(GRB.IntAttr.Status);
            if (status == GRB.Status.UNBOUNDED) {
                System.out.println("The model cannot be solved because it is unbounded");
                return;
            }
            if (status == GRB.Status.OPTIMAL) {
                if (config.showInfo) {
                    System.out.println("The optimal objective is " + model.get(GRB.DoubleAttr.ObjVal));
                    System.out.println("Optimal rebalancing:");
                }
                v = 0;
                for (Vehicle vehicle : idleVehicles) {
                    n = 0;

                    for (Node target : targets) {

                        int result = (int) x[v][n].get(GRB.DoubleAttr.X);

                        if (result > 0) {

                            // Make vehicle move to target
                            if (vehicle.getLastVisitedNode().getNetworkId() != target.getNetworkId()) {

                                vehicle.rebalanceTo(target);
                                //System.out.println("-->>>>>" + label + " " + result + x[v][n] + " = " + distance + "distance2=" + distance2);

                            }
                            //else {
                            //System.out.println("-->>>>>" + label + " " + result + names.get(v).get(n) + " = " + distance + "distance2=" + distance2);
                            //}
                        }
                        n++;
                    }
                    v++;
                }
                return;
            }
            if (status != GRB.Status.INF_OR_UNBD && status != GRB.Status.INFEASIBLE) {
                System.out.println("Optimization was stopped with status " + status);
                return;
            }

            // Compute IIS
            System.out.println("The model is infeasible; computing IIS");
            model.computeIIS();
            System.out.println("\nThe following constraint(s) "
                    + "cannot be satisfied:");
            for (GRBConstr c : model.getConstrs()) {
                if (c.get(GRB.IntAttr.IISConstr) == 1) {
                    System.out.println(c.get(GRB.StringAttr.ConstrName));
                }
            }

            // Dispose of model and environment
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " +
                    e.getMessage());
        }
    }
}
