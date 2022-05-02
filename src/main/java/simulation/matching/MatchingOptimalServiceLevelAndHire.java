package simulation.matching;

import com.google.common.collect.Sets;
import config.Config;
import config.Qos;
import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import model.*;
import org.apache.commons.collections4.MapUtils;

import java.util.*;


public class MatchingOptimalServiceLevelAndHire extends MatchingOptimalServiceLevel {

    private GRBVar[] varVehicleIsHired;
    private Set<Vehicle> hiredCurrentPeriod;
    private Map<Vehicle, Integer> hiredIndex;
    private int hiringPenalty;


    public MatchingOptimalServiceLevelAndHire(int maxVehicleCapacityRTV, int badServicePenalty, int hiringPenalty, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int maxEdgesRR, int rejectionPenalty, boolean allowHiring, String[] objectives, String PDVisitGenerator) {
        super(maxVehicleCapacityRTV, badServicePenalty, mipTimeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, maxEdgesRR, rejectionPenalty, objectives, PDVisitGenerator);
        this.hiringPenalty = hiringPenalty;
    }

    @Override
    public ResultAssignment match(int currentTime, Set<User> unassignedRequests, Set<Vehicle> currentVehicleList, Set<Vehicle> hired) {
        this.currentTime = currentTime;
        this.hiredCurrentPeriod = hired;
        this.result = new ResultAssignment(currentTime);

        Set<Vehicle> allAvailableVehicles = new HashSet<>(currentVehicleList);
        allAvailableVehicles.addAll(hired);

        buildGraphRTV(unassignedRequests, allAvailableVehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV, this.maxEdgesRV, maxEdgesRR);

        // printListOfCandidateVehiclesEachRequest();

        if (this.requests.isEmpty())
            return result;

        try {
            createGurobiModelAndEnvironment();
            initVarsHiring();
            initSlacks();
            addConstraintsHiring();
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

    protected void addConstraintsHiring() throws GRBException {
        addConstraintsStandardAssignment();
        activateVarRequestMetSL();
        guaranteeClassMinimumSLRelaxedGreaterEqual();
        addConstrsVehicleIsHired();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void objHierarchicalHiringVsSlack() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();

        String label = "H_HIRE_VIO_";

        for (Vehicle vehicle : hiredCurrentPeriod) {
            penObjectives.get("N_HIRED").addTerm(1, varVehicleIsHired(vehicle));
        }

        // Violation penalty
        for (Qos qos : sortedQos) {
            penObjectives.put(label + qos.id, new GRBLinExpr());
            penObjectives.get(label + qos.id).addTerm(badServicePenalty, varClassServiceLevelViolation[qos.code]);
            penObjectives.get(label + qos.id).addTerm(hiringPenalty, varVehicleIsHired[qos.code]);
        }
    }

    protected void objHierarchicalHiringAndRejectionServiceLevel() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();
        Map<String, Set<Vehicle>> qosVehicles = new HashMap<>();

        // Violation penalty
        String objGoal = "H_HIRE_RJ_VI";
        for (Qos qos : sortedQos) {
            String objLabel = String.format("%s_%s", objGoal, qos.id);
            penObjectives.put(objLabel, new GRBLinExpr());
            penObjectives.get(objLabel).addTerm(badServicePenalty, varClassServiceLevelViolation[qos.code]);
            qosVehicles.put(objLabel, new HashSet<>());
        }

        for (User request : requests) {
            String objLabel = String.format("%s_%s", objGoal, request.qos.id);
            qosVehicles.get(objLabel).addAll(getCandidatesToHireThatCanPickup(request));
            //penObjectives.get(objLabel).addTerm(rejectionPenalty, varRequestRejected(request));
            //penObjectives.get(objLabel).addTerm(-badServicePenalty, varRequestServiceLevelAchieved(request));
            //penObjectives.get(objLabel).addConstant(badServicePenalty);
        }

        for (Map.Entry<String, Set<Vehicle>> objLabelVehiclesEntry : qosVehicles.entrySet()) {
            String label = objLabelVehiclesEntry.getKey();
            Set<Vehicle> hiredVehiclesCanServiceClass = objLabelVehiclesEntry.getValue();
            for (Vehicle vehicle : hiredVehiclesCanServiceClass) {
                penObjectives.get(label).addTerm(hiringPenalty, varVehicleIsHired(vehicle));
            }
        }

        System.out.println("HIRED CURRENT PERIOD:" + hiredCurrentPeriod);
        MapUtils.debugPrint(System.out, "QOSVEHICLES", qosVehicles);
    }

    protected void objKeepServiceHierarchicalHiringAndRejectionServiceLevel() {
        // Sort QoS order = A, B, C
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();
        Map<String, Set<Vehicle>> qosVehicles = new HashMap<>();

        // Violation penalty
        String objGoal = "H_HIRE_RJ_VI";
        for (Qos qos : sortedQos) {
            String objLabel = String.format("%s_%s", objGoal, qos.id);
            penObjectives.put(objLabel, new GRBLinExpr());
            penObjectives.get(objLabel).addTerm(badServicePenalty, varClassServiceLevelViolation[qos.code]);
            qosVehicles.put(objLabel, new HashSet<>());
        }

        for (User request : requests) {
            String objLabel = String.format("%s_%s", objGoal, request.qos.id);
            qosVehicles.get(objLabel).addAll(getCandidatesToHireThatCanPickup(request));
            //penObjectives.get(objLabel).addTerm(rejectionPenalty, varRequestRejected(request));
            //penObjectives.get(objLabel).addTerm(-badServicePenalty, varRequestServiceLevelAchieved(request));
            //penObjectives.get(objLabel).addConstant(badServicePenalty);
        }

        for (Map.Entry<String, Set<Vehicle>> objLabelVehiclesEntry : qosVehicles.entrySet()) {
            String label = objLabelVehiclesEntry.getKey();
            Set<Vehicle> hiredVehiclesCanServiceClass = objLabelVehiclesEntry.getValue();
            for (Vehicle vehicle : hiredVehiclesCanServiceClass) {
                penObjectives.get(label).addTerm(hiringPenalty, varVehicleIsHired(vehicle));
            }
        }

        System.out.println("HIRED CURRENT PERIOD:" + hiredCurrentPeriod);
        MapUtils.debugPrint(System.out, "QOSVEHICLES", qosVehicles);
    }

    private Sets.SetView<Vehicle> getCandidatesToHireThatCanPickup(User request) {
        Set<Vehicle> hiredThatHaveVisitsIncludingRequest = graphRTV.getHiredVehiclesFromUser(request);
        return Sets.intersection(hiredThatHaveVisitsIncludingRequest, hiredCurrentPeriod);
    }

    public void addObjective(String objective) {
        super.addObjective(objective);
        switch (objective) {
            case Objective.NUMBER_OF_HIRED:
                objNumberHired();
                break;
            case Objective.HIERARCHICAL_HIRING_VS_SLACK:
                objHierarchicalHiringVsSlack();
                break;
            case Objective.HIERARCHICAL_HIRING_AND_REJECTION_SERVICE_LEVEL:
                objHierarchicalHiringAndRejectionServiceLevel();
                break;
            case Objective.NUMBER_OF_HIRED_AND_VIOLATIONS:
                objNumberHiredAndViolation();
                break;
        }
    }

    private void objNumberHired() {

        String label = "HIRED";
        penObjectives.put(label, new GRBLinExpr());
        if (!hiredCurrentPeriod.isEmpty()) {
            for (Vehicle vehicle : hiredCurrentPeriod) {
                penObjectives.get(label).addTerm(hiringPenalty, varVehicleIsHired(vehicle));
            }
        }
    }

    private void objNumberHiredAndViolation() {
        String label = "HIRED_VIOL";
        penObjectives.put(label, new GRBLinExpr());
        if (!hiredCurrentPeriod.isEmpty()) {
            for (Vehicle vehicle : hiredCurrentPeriod) {
                penObjectives.get(label).addTerm(hiringPenalty, varVehicleIsHired(vehicle));
            }
        }
        List<Qos> sortedQos = Config.getInstance().getSortedQosList();
        for (Qos qos : sortedQos) {
            penObjectives.get(label).addTerm(badServicePenalty, varClassServiceLevelViolation[qos.code]);
        }
    }

    private void initVarsHiring() throws GRBException {

        initVarsServiceLevel();

        varVehicleIsHired = new GRBVar[hiredCurrentPeriod.size()];

        hiredIndex = new HashMap<>();
        int i = 0;
        for (Vehicle vehicle : hiredCurrentPeriod) {
            hiredIndex.put(vehicle, i);
            i++;
        }

        for (Vehicle vehicle : hiredCurrentPeriod) {
            addIsHiredVehicleVar(vehicle);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // CONSTRAINTS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void addConstrsVehicleIsHired() throws GRBException {

        Map<Vehicle, GRBLinExpr> hiredVehicleVisits = new HashMap<>();

        for (Vehicle vehicle : hiredCurrentPeriod) {
            hiredVehicleVisits.put(vehicle, new GRBLinExpr());
        }

        for (Visit visit : visits) {

            if (isVisitFromHiringCandidate(visit)) {
                hiredVehicleVisits.get(visit.getVehicle()).addTerm(1, varVisitSelected(visit));
            }
        }

        for (Vehicle vehicle : hiredCurrentPeriod) {
            hiredVehicleVisits.get(vehicle).addTerm(-1, varVehicleIsHired(vehicle));
            model.addConstr(hiredVehicleVisits.get(vehicle), GRB.EQUAL, 0, "hiring_" + getVarVehicle(vehicle));
        }
    }

    private boolean isVisitFromHiringCandidate(Visit visit) {
        return hiredCurrentPeriod.contains(visit.getVehicle()) && visit.getVehicle().isHired() && !(visit instanceof VisitStop) && !(visit instanceof VisitRelocation);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // VARIABLES ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private GRBVar varVehicleIsHired(Vehicle vehicle) {
        return varVehicleIsHired[hiredIndex.get(vehicle)];
    }

    protected void addIsHiredVehicleVar(Vehicle vehicle) throws GRBException {
        String label = String.format("x_hired_%s", getVarVehicle(vehicle));
        varVehicleIsHired[hiredIndex.get(vehicle)] = model.addVar(0, 1, 1, GRB.BINARY, label);
    }

    private boolean isVehicleHired(Vehicle vehicle) throws GRBException {
        return getValue(varVehicleIsHired(vehicle)) > 0.99;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // RESULT PROCESSING ///////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void extractResultServiceLevel() throws GRBException {
        super.extractResultServiceLevel();
        extractCurrentHiredVehicles();
    }

    private void extractCurrentHiredVehicles() throws GRBException {
        for (Vehicle vehicle : hiredCurrentPeriod) {
            if (isVehicleHired(vehicle)) {
                result.addHiredVehicle(vehicle);
            }
        }
    }


    @Override
    public String toString() {
        return "_OPT-ERTV";
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// PRINTS /////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void printAllVehiclesVisits() {
        Map<Vehicle, String> visitsVehicle = new HashMap<>();
        System.out.printf("## Vehicle count = %d", graphRTV.getListVehicles().size());
        for (Vehicle vehicle : graphRTV.getListVehicles()) {
            visitsVehicle.put(vehicle, "");
        }
        for (Visit visit : visits) {
            StringBuilder reqs = new StringBuilder();
            reqs.append(getVarVisit(visit));
            reqs.append("=[");
            for (User request : visit.getRequests()) {
                reqs.append(String.format("%s(%3d)  ", request, (int) getDelayOfRequestInVisit(request, visit)));
            }
            reqs.append("]");
            visitsVehicle.computeIfPresent(visit.getVehicle(), (vehicle, s) -> s + " >> " + reqs);
        }

        visitsVehicle.forEach((vehicle, s) ->
        {
            if (hiredCurrentPeriod.contains(vehicle)) {
                System.out.printf("\n# === %s --- %s", vehicle, s);
            } else {
                System.out.printf("\n# %s --- %s", vehicle, s);
            }
        });
    }


    private void printListOfCandidateVehiclesEachRequest() {
        Map<User, Set<Vehicle>> usersVehicle = new HashMap<>();

        System.out.printf("#Visits: %d\n", visits.size());
        for (Visit visit : visits) {
            Set<User> users = visit.getUsers();
            for (User user : users) {
                usersVehicle.putIfAbsent(user, new HashSet<>());
                usersVehicle.get(user).add(visit.getVehicle());
            }
        }

        usersVehicle.forEach((user, vehicles1) -> {
            List<Vehicle> vehicles = new ArrayList<>(vehicles1);
            vehicles.sort(Comparator.comparing(Vehicle::toString));
            System.out.printf("%s (vehicles = %4d)=%s\n", user, vehicles1.size(), vehicles);
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /// ASSERTIONS /////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private boolean allRoundRequestsCanBePickedUp() {

        for (Vehicle vehicle : hiredCurrentPeriod) {
            //System.out.println(vehicle + " - " + graphRTV.getListOfVisitsFromVehicle(vehicle));
            boolean atLeastOneVisitHasFAVUnser = false;
            for (Visit visit : graphRTV.getListOfVisitsFromVehicle(vehicle)) {
                if (visit.getRequests().contains(vehicle.getUserHiredMustPickup())) {
                    atLeastOneVisitHasFAVUnser = true;
                    break;
                }
            }
            if (!atLeastOneVisitHasFAVUnser) {
                System.out.println(vehicle + " - " + vehicle.getUserHiredMustPickup() + " - Visits:" + graphRTV.getListOfVisitsFromVehicle(vehicle));
                return false;
            }
        }
        return true;
    }
}
