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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class MatchingOptimalServiceLevel extends MatchingOptimal {

    private int serviceLevelViolationPenalty;
    private int badServicePenalty;

    private GRBVar[] varFirstTierMet;
    private GRBVar[] varSecondTierMet;
    private GRBVar[] varClassServiceLevelViolation;
    private int[] nOfRequestsPerClass;


    public MatchingOptimalServiceLevel(int violationPenalty, int badServicePenalty, double timeLimit, double mipGap, int maxEdgesRV, double rtvExecutionTime, int rejectionPenalty) {
        super(timeLimit, mipGap, maxEdgesRV, rtvExecutionTime, rejectionPenalty);
        this.serviceLevelViolationPenalty = violationPenalty;
        this.badServicePenalty = badServicePenalty;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void setupObjective() throws GRBException {

        Map<Qos, GRBLinExpr> penObjectives = new HashMap<>();

        // Sort QoS reverse order = C, B, A
        List<String> sortedQos = Config.getInstance().qosDic.values().stream()
                .map(qos -> qos.id)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        // Violation penalty
        for (Qos qos : Config.getInstance().qosDic.values()) {
            penObjectives.put(qos, new GRBLinExpr());
            penObjectives.get(qos).addTerm(serviceLevelViolationPenalty, varClassServiceLevelViolation[qos.code]);
        }

        /*for (Visit visit : visits) {
            obj1.addTerm(visit.getDelay(), varVisitSelected(visit));
        }*/

        for (User request : requests) {
            penObjectives.get(request.qos).addTerm(rejectionPenalty, varRequestRejected(request));
            penObjectives.get(request.qos).addTerm(badServicePenalty, varRequestServiceLevelNotAchieved(request));
        }

        for (int i = 0; i < sortedQos.size(); i++) {
            String classLabel = sortedQos.get(i);
            model.setObjectiveN(penObjectives.get(Config.getInstance().qosDic.get(classLabel)), i, i, 1.0, 0.0, 0.0, "OBJ_" + classLabel);
        }

        // The objective is to minimize the total pay costs
        model.set(GRB.IntAttr.ModelSense, GRB.MINIMIZE);
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



    private void initVarsSL() throws GRBException {

        varFirstTierMet = new GRBVar[requests.size()];
        varSecondTierMet = new GRBVar[requests.size()];
        varClassServiceLevelViolation = new GRBVar[requests.size()];
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
