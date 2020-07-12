package simulation;

import config.Config;
import config.Qos;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import model.User;
import model.Vehicle;
import model.Visit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class MatchingOptimalServiceLevel extends MatchingOptimal {

    private int serviceLevelViolationPenalty;
    private int badServicePenalty;

    private GRBVar[] varFirstTierMet;
    private GRBVar[] varSecondTierMet;
    private GRBVar[] varClassServiceLevelViolation;
    private int[] nOfRequestsPerClass;


    public MatchingOptimalServiceLevel(int maxVehicleCapacityRTV, int violationPenalty, int badServicePenalty, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int rejectionPenalty) {
        super(maxVehicleCapacityRTV, mipTimeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, rejectionPenalty);
        this.serviceLevelViolationPenalty = violationPenalty;
        this.badServicePenalty = badServicePenalty;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void setupObjective() throws GRBException {

        Map<String, GRBLinExpr> penObjectives = new LinkedHashMap<>();

        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(qos.id, new GRBLinExpr());
            penObjectives.get(qos.id).addTerm(serviceLevelViolationPenalty, varClassServiceLevelViolation[qos.code]);
        }

        for (User request : requests) {
            penObjectives.get(request.qos.id).addTerm(rejectionPenalty, varRequestRejected(request));
            penObjectives.get(request.qos.id).addTerm(badServicePenalty, varRequestServiceLevelNotAchieved(request));
        }

        penObjectives.put("WAITING", new GRBLinExpr());
        for (Visit visit : visits) {
            penObjectives.get("WAITING").addTerm(visit.getDelay(), varVisitSelected(visit));
        }

        int i = penObjectives.size();
        for (Map.Entry<String, GRBLinExpr> e : penObjectives.entrySet()) {
            i--;
            model.setObjectiveN(e.getValue(), i, i, 1.0, 0.0, 0.0, "OBJ_" + e.getKey());
        }

        // The objective is to minimize the total pay costs
        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
    }


    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> listVehicles, Matching configMatching) {
        buildGraphRTV(unassignedRequests, listVehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV);

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
                    System.out.println(String.format("## TIME LIMIT REACHED = %.2f seconds // Solution count: %s", mipTimeLimit, model.get(GRB.IntAttr.SolCount)));
                }

                extractResult();
                extractResultSL();

            } else {
                computeIIS();
            }

        } catch (GRBException e) {
            System.out.println("TIME IS OVER - No solution found, keep previous assignment. Gurobi error code: " + e.getErrorCode() + ". " + e.getMessage());
            keepPreviousAssignement();
        } finally {
            // Dispose of model and environment
            model.dispose();
            try {
                env.dispose();
            } catch (GRBException e) {
                e.printStackTrace();
            }
        }

        result.printRoundResult();

        return result;
    }

    protected void keepPreviousAssignement() {

        result.requestsUnassigned.addAll(User.getUnassigned(requests));

        // Keep the previously picked up requests
        List<User> serviced = User.getAssigned(requests);
        serviced.forEach(user -> result.addVisit(user.getCurrentVisit()));

        // Classify serviced in first- and second-tier
        result.requestsServicedLevelAchieved.addAll(User.filterFirstTier(serviced));
        result.requestsServicedLevelNotAchieved.addAll(User.filterSecondTier(serviced));

        for (Qos qos : Config.getInstance().qosDic.values()) {
            result.unmetServiceLevelClass.put(qos, User.filterUsersOfQos(User.filterSecondTier(requests), qos).size());
            result.totalServiceLevelClass.put(qos, User.filterUsersOfQos(requests, qos).size());
        }
    }


    private void initVarsSL() throws GRBException {

        varFirstTierMet = new GRBVar[requests.size()];
        varSecondTierMet = new GRBVar[requests.size()];
        varClassServiceLevelViolation = new GRBVar[Config.getInstance().getQosCount()];
        nOfRequestsPerClass = new int[Config.getInstance().getQosCount()];

        for (User request : requests) {
            addIsTargetServiceLevelMetVar(request);
            addIsTargetServiceLevelNotMetVar(request);
            nOfRequestsPerClass[request.getQoSCode()]++;
        }

        for (Qos qos : Config.getInstance().qosDic.values()) {
            addClassServiceLevelSlack(qos, nOfRequestsPerClass[qos.code]);
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void setupRequestStatusConstraints() throws GRBException {
        for (User request : requests) {

            GRBLinExpr constrRequestServiceLevelNotAchieved = new GRBLinExpr();
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestServiceLevelNotAchieved(request));
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestServiceLevelAchieved(request));
            constrRequestServiceLevelNotAchieved.addTerm(1, varRequestRejected(request));

            model.addConstr(constrRequestServiceLevelNotAchieved, GRB.EQUAL, 1, "second_tier_" + request.toString().trim());
        }
    }

    private void setupClassTargetServiceLevelConstraints() {

        GRBLinExpr[] constrFirstClass = new GRBLinExpr[Config.getInstance().getQosCount()];
        for (int i = 0; i < Config.getInstance().getQosCount(); i++) {
            constrFirstClass[i] = new GRBLinExpr();
        }

        // Sum of all user that got first tier service levels of each class
        for (User request : requests) {
            GRBVar slAchieved = varRequestServiceLevelAchieved(request);
            if (slAchieved != null)
                constrFirstClass[request.getQoSCode()].addTerm(1, slAchieved);
        }

        Config.getInstance().qosDic.forEach((s, qos) -> {

            // Add slack to each service level class (i.e., number of user who received second tier or were rejected)
            constrFirstClass[qos.code].addTerm(1, varClassServiceLevelViolation[qos.code]);

            // Number of picked up users has to be higher than service rate promised
            try {
                model.addConstr(
                        constrFirstClass[qos.code],
                        GRB.GREATER_EQUAL,
                        (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]),
                        String.format("class_%s", qos.id));
            } catch (GRBException e) {
                e.printStackTrace();
            }
        });
    }


    private boolean isFirstTier(User request, Visit visit) {

        double pickupDelay = graphRTV.getWeightFromRequestVisitEdge(request, visit);
        return request.isDelayFirstTier(pickupDelay);
    }

    private void setupRequestServiceLevelConstraints() throws GRBException {


        for (User request : requests) {

            GRBLinExpr constrRequestServiceLevel = new GRBLinExpr();

            List<Visit> requestVisits = graphRTV.getListOfVisitsFromUser(request);

            for (Visit visit : requestVisits) {
                if (isFirstTier(request, visit)) {
                    constrRequestServiceLevel.addTerm(1, varVisitSelected(visit));
                }
            }

            constrRequestServiceLevel.addTerm(-1, varRequestServiceLevelAchieved(request));
            String label = String.format("first_tier_visits_request_%s", request.toString().trim());
            model.addConstr(constrRequestServiceLevel, GRB.EQUAL, 0, label);

        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VARIABLES ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private GRBVar varRequestServiceLevelAchieved(User request) {
        return varFirstTierMet[requestIndex.get(request)];
    }

    private GRBVar varRequestServiceLevelNotAchieved(User request) {
        return varSecondTierMet[requestIndex.get(request)];
    }

    private void addIsTargetServiceLevelMetVar(User request) throws GRBException {
        varFirstTierMet[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, "w_SL1_MET_" + request.toString().trim());
    }

    private void addIsTargetServiceLevelNotMetVar(User request) throws GRBException {
        varSecondTierMet[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, "z_SL2_MET_" + request.toString().trim());
    }

    private void addClassServiceLevelSlack(Qos qos, int userCount) throws GRBException {
        varClassServiceLevelViolation[qos.code] = model.addVar(0, userCount, 1, GRB.INTEGER, "slack_SL2" + qos.id);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RESULT PROCESSING ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void extractResultSL() throws GRBException {

        for (Qos qos : Config.getInstance().qosDic.values()) {
            double unmetService = varClassServiceLevelViolation[qos.code].get(GRB.DoubleAttr.X);
            result.unmetServiceLevelClass.put(qos, (int) unmetService);
            result.totalServiceLevelClass.put(qos, nOfRequestsPerClass[qos.code]);
        }

        for (User request : requests) {

            if (varRequestServiceLevelAchieved(request).get(GRB.DoubleAttr.X) > 0.99) {
                result.requestsServicedLevelAchieved.add(request);
                // System.out.println(request + " - ACHIEVED");
            }

            if (varRequestServiceLevelNotAchieved(request).get(GRB.DoubleAttr.X) > 0.99) {
                result.requestsServicedLevelNotAchieved.add(request);
                // System.out.println(request + " - NOT ACHIEVED");
            }
        }
    }

    @Override
    public String toString() {
        return "_OPT-JAVIERSL";
    }

}
