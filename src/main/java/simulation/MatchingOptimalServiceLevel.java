package simulation;

import gurobi.GRB;
import gurobi.GRBException;
import model.User;
import model.Vehicle;

import java.util.List;


public class MatchingOptimalServiceLevel extends MatchingOptimal {

    private int violationPenalty;
    private int badServicePenalty;

    public MatchingOptimalServiceLevel(int violationPenalty, int badServicePenalty, double timeLimit, double mipGap, int maxEdgesRV, double rtvExecutionTime, int rejectionPenalty) {
        super(timeLimit, mipGap, maxEdgesRV, rtvExecutionTime, rejectionPenalty);
        this.violationPenalty = violationPenalty;
        this.badServicePenalty = badServicePenalty;
    }

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> listVehicles, Matching configMatching) {

        buildGraphRTV(unassignedRequests, listVehicles);

        result = new ResultAssignment(currentTime);

        if (this.requests.isEmpty())
            return result;

        try {

            createModel();
            initVarsStandard();
            initVarsSL();
            setupVehicleConservationConstraints();
            setupRequestConservationConstraints();
            setupRequestServiceLevelConstraints();
            setupClassTargetServiceLevelConstraints();
            setupRequestStatusConstraints();
            setupObjective();

            // model.write(String.format("round_mip_model/assignment_%d.lp", currentTime));
            this.model.optimize();

            int status = this.model.get(GRB.IntAttr.Status);
            if (status == GRB.Status.OPTIMAL || status == GRB.Status.TIME_LIMIT) {

                if (status == GRB.Status.TIME_LIMIT) {
                    System.out.println("TIME LIMIT11111!!!!!!!");
                }

                extractResult();
                extractResultSL();

            } else {
                computeIIS();
            }


            // Dispose of model and environment
            model.dispose();
            env.dispose();

        } catch (GRBException e) {
            System.out.println("Error code: " + e.getErrorCode() + ". " + e.getMessage());
        }

        result.printRoundResult();

        return result;
    }

}
