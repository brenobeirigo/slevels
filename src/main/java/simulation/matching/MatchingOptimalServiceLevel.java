package simulation.matching;

import config.Config;
import config.Qos;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import model.*;

import java.util.*;


public class MatchingOptimalServiceLevel extends MatchingOptimal {

    protected final int badServicePenalty;
    protected GRBVar[] varClassServiceLevelViolation;
    private GRBVar[] varFirstTierMet;
    private int[] nOfRequestsPerClass;


    public MatchingOptimalServiceLevel(int maxVehicleCapacityRTV, int badServicePenalty, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int maxEdgesRR, int rejectionPenalty, String[] objectives) {
        super(maxVehicleCapacityRTV, mipTimeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, maxEdgesRR, rejectionPenalty, objectives);
        this.badServicePenalty = badServicePenalty;
    }

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> currentVehicleList, Set<Vehicle> hired, Matching configMatching) {
        this.currentTime = currentTime;
        this.result = new ResultAssignment(currentTime);

        List<Vehicle> allAvailableVehicles = new ArrayList<>(currentVehicleList);
        allAvailableVehicles.addAll(hired);

        buildGraphRTV(unassignedRequests, allAvailableVehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV, maxEdgesRV, maxEdgesRR);

        if (this.requests.isEmpty())
            return result;

        try {
            createGurobiModelAndEnvironment();
            initVarsServiceLevel();
            addConstraintsServiceLevels();
            setupObjectives();

            this.model.optimize();

            if (isModelOptimal() || isTimeLimitReached()) {
                if (isTimeLimitReached()) {
                    System.out.printf("## TIME LIMIT REACHED = %.2f seconds // Solution count: %s%n", mipTimeLimit, model.get(GRB.IntAttr.SolCount));
                }

                extractResultServiceLevel();

            } else {
                computeIISReduceUntilCanBeSolved();
            }
        } catch (GRBException e) {
            System.out.println("TIME IS OVER - No solution found, keep previous assignment. Gurobi error code: " + e.getErrorCode() + ". " + e.getMessage());
            keepPreviousAssignment();
        } finally {
            disposeModelEnvironmentAndSave();
        }

        result.printRoundResultSummary();

        return result;
    }

    protected void addConstraintsServiceLevels() throws GRBException {
        addConstraintsStandardAssignment();
        activateVarRequestMetSL();
        guaranteeClassMinimumSLRelaxed();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void addObjective(String objective) {
        super.addObjective(objective);
        switch (objective) {
            case "obj_hierarchical_rejection_service_level":
                objHierarchicalRejectionServiceLevel();
                break;
            case "obj_hierarchical_service_level":
                objHierarchicalServiceLevel();
                break;
            case "obj_hierarchical_rejection":
                objHierarchicalRejection();
                break;
            case "obj_hierarchical_slack":
                objHierarchicalSlack();
                break;
        }
    }

    protected void objHierarchicalRejectionServiceLevel() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        String objGoal = "REJ_VIOL";
        for (Qos qos : sortedQos) {
            String objLabel = String.format("%s_%s", objGoal, qos.id);
            penObjectives.put(objLabel, new GRBLinExpr());
        }

        for (User request : requests) {
            String objLabel = String.format("%s_%s", objGoal, request.qos.id);
            penObjectives.get(objLabel).addTerm(rejectionPenalty, varRequestRejected(request));
            penObjectives.get(objLabel).addTerm(-badServicePenalty, varRequestServiceLevelAchieved(request));
            penObjectives.get(objLabel).addConstant(badServicePenalty);
        }
    }

    protected void objHierarchicalServiceLevel() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        String objGoal = "VIOL";
        for (Qos qos : sortedQos) {
            String objLabel = String.format("%s_%s", objGoal, qos.id);
            penObjectives.put(objLabel, new GRBLinExpr());
        }

        for (User request : requests) {
            String objLabel = String.format("%s_%s", objGoal, request.qos.id);
            penObjectives.get(objLabel).addTerm(-badServicePenalty, varRequestServiceLevelAchieved(request));
            penObjectives.get(objLabel).addConstant(badServicePenalty);
        }
    }

    protected void objHierarchicalSlack() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        String label = "SLACK_";
        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(label + qos.id, new GRBLinExpr());
            penObjectives.get(label + qos.id).addTerm(1, varClassServiceLevelViolation[qos.code]);
        }
    }

    protected void objHierarchicalRejection() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        // Violation penalty
        String label = "H_REJ_";
        for (Qos qos : sortedQos) {
            penObjectives.put(label + qos.id, new GRBLinExpr());
        }

        for (User request : requests) {
            penObjectives.get(label + request.qos.id).addTerm(rejectionPenalty, varRequestRejected(request));
        }
    }

    protected void keepPreviousAssignment() {

        result.getRequestsUnassigned().addAll(User.getUnassigned(requests));

        // Keep the previously picked up requests
        List<User> serviced = User.getAssigned(requests);
        serviced.forEach(user -> result.addVisit(user.getCurrentVisit()));

        // Classify serviced in first- and second-tier
        result.requestsServicedLevelAchieved.addAll(User.filterFirstTier(serviced));
        result.requestsServicedLevelNotAchieved.addAll(User.filterSecondTier(serviced));

        for (Qos qos : Config.getInstance().qosDic.values()) {
            result.unmetServiceLevelClass.put(qos, User.filterUsersOfQos(User.filterSecondTier(requests), qos).size());
            result.nOfRequestsClass.put(qos, User.filterUsersOfQos(requests, qos).size());
        }
    }

    protected void initVarsServiceLevel() throws GRBException {

        super.initVarsStandardAssignment();

        varFirstTierMet = new GRBVar[requests.size()];
        varClassServiceLevelViolation = new GRBVar[Config.getInstance().getQosCount()];
        nOfRequestsPerClass = new int[Config.getInstance().getQosCount()];

        for (User request : requests) {
            addIsTargetServiceLevelMetVar(request);
            nOfRequestsPerClass[request.getQoSCode()]++;
        }

        for (Qos qos : Config.getInstance().qosDic.values()) {
            addClassServiceLevelSlack(qos, nOfRequestsPerClass[qos.code]);
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void guaranteeClassMinimumSL() {

        GRBLinExpr[] constrGaranteeClassServiceLevel = new GRBLinExpr[Config.getInstance().getQosCount()];

        for (int i = 0; i < Config.getInstance().getQosCount(); i++) {
            constrGaranteeClassServiceLevel[i] = new GRBLinExpr();
        }

        // Sum of all user that got first tier service levels of each class
        for (User request : requests) {

            // GOOD SERVICE = desired service level
            GRBVar slAchieved = varRequestServiceLevelAchieved(request);
            if (slAchieved != null) {
                constrGaranteeClassServiceLevel[request.getQoSCode()].addTerm(1, slAchieved);
            }
        }

        Config.getInstance().qosDic.forEach((s, qos) -> {
            // Number of picked up users has to be higher than service rate promised
            try {
                int minRequestsSL = (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]);
                String label = String.format("class_%s_sr_%.2f_total_%d_min_%d", qos.id, qos.serviceRate, nOfRequestsPerClass[qos.code], minRequestsSL);

                model.addConstr(constrGaranteeClassServiceLevel[qos.code], GRB.GREATER_EQUAL, minRequestsSL, label);
            } catch (GRBException e) {
                e.printStackTrace();
            }
        });
    }

    protected void guaranteeClassMinimumSLRelaxed() {

        GRBLinExpr[] constrGaranteeClassServiceLevel = new GRBLinExpr[Config.getInstance().getQosCount()];

        for (int i = 0; i < Config.getInstance().getQosCount(); i++) {
            constrGaranteeClassServiceLevel[i] = new GRBLinExpr();
        }

        // Sum of all user that got first tier service levels of each class
        for (User request : requests) {

            // GOOD SERVICE = desired service level
            GRBVar slAchieved = varRequestServiceLevelAchieved(request);
            if (slAchieved != null) {
                constrGaranteeClassServiceLevel[request.getQoSCode()].addTerm(1, slAchieved);
            }
        }

        Config.getInstance().qosDic.forEach((s, qos) -> {

            // Add slack to each service level class (i.e., number of user who received second tier or were rejected)
            constrGaranteeClassServiceLevel[qos.code].addTerm(1, varClassServiceLevelViolation[qos.code]);

            // Number of picked up users has to be higher than service rate promised
            try {
                int minRequestsSL = (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]);
                String label = String.format("class_%s_sr_%.2f_total_%d_min_%d", qos.id, qos.serviceRate, nOfRequestsPerClass[qos.code], minRequestsSL);

                model.addConstr(constrGaranteeClassServiceLevel[qos.code], GRB.GREATER_EQUAL, minRequestsSL, label);
            } catch (GRBException e) {
                e.printStackTrace();
            }
        });
    }


    private boolean isFirstTier(User request, Visit visit) {

        double pickupDelay = getDelayOfRequestInVisit(request, visit);
        return request.isDelayFirstTier(pickupDelay);
    }

    protected void activateVarRequestMetSL() throws GRBException {

        for (User request : requests) {
            GRBLinExpr constrRequestServiceLevel = new GRBLinExpr();

            List<Visit> requestVisits = graphRTV.getListOfVisitsFromUser(request);

            for (Visit visit : requestVisits) {
                if (isFirstTier(request, visit)) {
                    constrRequestServiceLevel.addTerm(1, varVisitSelected(visit));
                }
            }

            constrRequestServiceLevel.addTerm(-1, varRequestServiceLevelAchieved(request));
            String label = String.format("first_tier_visits_request_%s", getVarUser(request));
            model.addConstr(constrRequestServiceLevel, GRB.EQUAL, 0, label);

        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VARIABLES ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected GRBVar varRequestServiceLevelAchieved(User request) {
        return varFirstTierMet[requestIndex.get(request)];
    }

    private void addIsTargetServiceLevelMetVar(User request) throws GRBException {
        String label = "x_sl_" + getVarUser(request);
        varFirstTierMet[requestIndex.get(request)] = model.addVar(0, 1, 1, GRB.BINARY, label);
    }

    private void addClassServiceLevelSlack(Qos qos, int userCount) throws GRBException {
        String label = "x_slack_bad_service" + qos.id;
        int minRequestsSL = (int) Math.ceil(qos.serviceRate * nOfRequestsPerClass[qos.code]);
        varClassServiceLevelViolation[qos.code] = model.addVar(0, userCount, 1, GRB.INTEGER, label);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RESULT PROCESSING ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void extractResultServiceLevel() throws GRBException {

        super.extractResult();

        for (Qos qos : Config.getInstance().qosDic.values()) {
            result.accountNumberOfServiceLevelViolations(qos, getValue(varClassServiceLevelViolation[qos.code]));
            result.accountNumberOfRequestsClass(qos, nOfRequestsPerClass[qos.code]);
        }

        for (User request : requests) {
            if (!isRequestRejected(request)) {
                if (isServiceLevelMet(request)) {
                    result.accountMetServiceLevel(request);
                } else {
                    result.accountUnmetServiceLevel(request);
                }
            }
        }
    }

    protected double getValue(GRBVar grbVar) throws GRBException {
        return grbVar.get(GRB.DoubleAttr.X);
    }

    private boolean isServiceLevelMet(User request) throws GRBException {
        return getValue(varRequestServiceLevelAchieved(request)) > 0.99;
    }

    @Override
    public String toString() {
        return "_OPT-JAVIERSL";
    }

}
