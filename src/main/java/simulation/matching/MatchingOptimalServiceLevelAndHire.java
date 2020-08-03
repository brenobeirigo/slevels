package simulation.matching;

import gurobi.GRB;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBVar;
import model.*;

import java.util.*;


public class MatchingOptimalServiceLevelAndHire extends MatchingOptimalServiceLevel {

    private GRBVar[] varVehicleIsHired;
    private Set<Vehicle> hiredCurrentPeriod;
    private Map<Vehicle, Integer> hiredIndex;


    public MatchingOptimalServiceLevelAndHire(int maxVehicleCapacityRTV, int badServicePenalty, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int rejectionPenalty, boolean allowHiring) {
        super(maxVehicleCapacityRTV, badServicePenalty, mipTimeLimit, timeoutVehicleRTV, mipGap, maxEdgesRV, rejectionPenalty);
    }

    @Override
    public ResultAssignment match(int currentTime, List<User> unassignedRequests, List<Vehicle> currentVehicleList, Set<Vehicle> hired, Matching configMatching) {
        this.currentTime = currentTime;
        this.hiredCurrentPeriod = hired;
        this.result = new ResultAssignment(currentTime);

        List<Vehicle> allAvailableVehicles = new ArrayList<>(currentVehicleList);
        allAvailableVehicles.addAll(hired);

        buildGraphRTV(unassignedRequests, allAvailableVehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV, this.maxEdgesRV);

        // printListOfCandidateVehiclesEachRequest();

        if (this.requests.isEmpty())
            return result;

        try {
            createGurobiModelAndEnvironment();
            initVarsHiring();
            addConstraintsHiring();
            setupObjectiveHiredHierarchicalPenaltyThenTotalWaiting();
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
        guaranteeClassMinimumSLRelaxed();
        addConstrsVehicleIsHired();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // OBJECTIVE ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private void setupObjectiveHiredHierarchicalPenaltyThenTotalWaiting() throws GRBException {


        Map<String, GRBLinExpr> penObjectives = new LinkedHashMap<>();


        objHierarchicalRejection(penObjectives);

        if (!hiredCurrentPeriod.isEmpty())
            objNumberHired(penObjectives);

        //objHierarchicalSlack(penObjectives);
        objHierarchicalServiceLevelViolation(penObjectives);

        // objHierarchicalServiceLevel(penObjectives);

        objTotalWaitingTime(penObjectives);

        addHierarchicalObjectives(penObjectives);
    }

    private void objNumberHired(Map<String, GRBLinExpr> penObjectives) {

        penObjectives.put("N_HIRED", new GRBLinExpr());
        for (Vehicle vehicle : hiredCurrentPeriod) {
            penObjectives.get("N_HIRED").addTerm(1, varVehicleIsHired(vehicle));
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
        return "_OPT-ERTV_HIRE";
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
            if (!atLeastOneVisitHasFAVUnser){
                System.out.println(vehicle + " - " + vehicle.getUserHiredMustPickup() +" - Visits:" + graphRTV.getListOfVisitsFromVehicle(vehicle));
                return false;
            }
        }
        return true;
    }
}
