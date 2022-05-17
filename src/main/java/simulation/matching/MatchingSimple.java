package simulation.matching;

import com.google.common.collect.Iterables;
import gurobi.GRBEnv;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import helper.HelperIO;
import model.*;
import model.graph.GraphRTV;
import model.graph.ParallelGraphRTV;
import model.learn.*;
import simulation.Simulation;

import java.util.*;
import java.util.stream.Collectors;

public class MatchingSimple implements RideMatchingStrategy {


    // There might have relocation trips to the same node, this variable helps creating unique labels
    private static int varVisitId = 0;
    protected String PDVisitGenerator;
    protected int maxVehicleCapacityRTV;
    protected double timeoutVehicleRTV;
    protected double mipTimeLimit;
    protected double mipGap;
    protected int maxEdgesRV;
    protected int maxEdgesRR;
    protected int rejectionPenalty;

    // Model
    protected GRBEnv env;
    protected GRBModel model;
    protected Set<VisitObj> visits;
    protected Set<User> requests;

    // Some vehicles cannot access
    protected Set<Vehicle> vehicles;
    protected Map<VisitObj, Integer> visitIndex;
    protected Map<User, Integer> requestIndex;

    // Assignment variables: x[r][v] == 1 if request r is assigned to trip v
    protected GRBVar[] varVisitSelected;
    protected GRBVar[] varRequestRejected;
    protected String[] orderedListOfObjectiveLabels;
    protected int currentTime;
    protected Map<String, GRBLinExpr> penObjectives;
    GraphRTV graphRTV;
    ResultAssignment result;
    public Map<Vehicle, Set<VisitObj>> vehicleVisitsMap;
    public Map<User, Set<VisitObj>> userVisitsMap;
    public List<Experience> experiences;

    public MatchingSimple(int maxVehicleCapacityRTV, double mipTimeLimit, double timeoutVehicleRTV, double mipGap, int maxEdgesRV, int maxEdgesRR, int rejectionPenalty, String[] orderedListOfObjectiveLabels, String PDVisitGenerator) {
        this.maxVehicleCapacityRTV = maxVehicleCapacityRTV;
        this.mipTimeLimit = mipTimeLimit;
        this.timeoutVehicleRTV = timeoutVehicleRTV;
        this.mipGap = mipGap;
        this.maxEdgesRV = maxEdgesRV;
        this.maxEdgesRR = maxEdgesRR;
        this.rejectionPenalty = rejectionPenalty;
        this.orderedListOfObjectiveLabels = orderedListOfObjectiveLabels;
        this.penObjectives = new LinkedHashMap<>();
        this.PDVisitGenerator = PDVisitGenerator;
        this.experiences = new ArrayList<>();
    }

    @Override
    public void realizeVisit(VisitObj visit) {

        // Does nothing if same visit chosen (e.g., continue rebalancing)
        if (visit.getVehicle().getVisit() == visit)
            return;

        // Dummy visit for parked or rebalancing vehicle
        if (visit instanceof VisitStop) {
            return;
        }

        // Vehicle drop scheduled requests and stop at the closest node
        if (visit instanceof VisitDisplaceAndStop) {
            visit.getVehicle().setVisit(visit);
            return;
        }

        // If vehicle was rebalancing, compute the rebalancing distance until middle
        if (visit.getVehicle().isRebalancing()) {
            visit.getVehicle().computeDistanceTraveledRebalancingUntilMiddle();
            visit.getVehicle().setStoppedRebalanceToPickup(true);
        }

        // Add visit to vehicle (circular)
        visit.getVehicle().setVisit(visit);

        // Update visit for users in vehicle
        for (User request : Iterables.concat(visit.getRequests(), visit.getPassengers())) {
            request.setCurrentVisit(visit);
        }

        // Go through nodes and update arrival so far
        visit.updateArrivalSoFarAtVisitNodes();

        // Vehicle is not idle
        visit.getVehicle().setRoundsIdle(0);

    }

    protected void buildGraphRTV(Set<User> unassignedRequests, Set<Vehicle> listVehicles, int maxVehicleCapacity, double timeoutVehicle, int maxVehReqEdges, int maxReqReqEdges) {

        // BUILDING GRAPH STRUCTURE ////////////////////////////////////////////////////////////////////////////////////
        this.graphRTV = new ParallelGraphRTV(unassignedRequests, listVehicles, maxVehicleCapacity, timeoutVehicle, maxVehReqEdges, maxReqReqEdges);
    }

    @Override
    public ResultAssignment match(int currentTime, Set<User> unassignedRequests, Set<Vehicle> vehicles, Set<Vehicle> hired) {
        buildGraphRTV(unassignedRequests, vehicles, this.maxVehicleCapacityRTV, timeoutVehicleRTV, maxEdgesRV, maxEdgesRR);

        this.visits = new HashSet<>(this.graphRTV.getAllVisits());
        this.requests = new HashSet<>(unassignedRequests);
        this.vehicles = new HashSet<>(vehicles);
        this.vehicleVisitsMap = this.graphRTV.getVehicleVisitsMap();
        this.userVisitsMap = this.graphRTV.getUserVisitsMap();
        this.result = new ResultAssignment(currentTime);

        // Create pre-decision state space with simplified versions of the visits
        StateSpace preDecisionStateSpace = new StateSpace(vehicles, unassignedRequests, vehicleVisitsMap, Simulation.rightTW, Simulation.timeHorizon);
        DecisionSpaceObject preDecisionSpaceObj = preDecisionStateSpace.getDecisionSpaceObject();
        DecisionSpaceObject current = preDecisionStateSpace.getCurrentStateObject();
        String expFolder = "experiences";
        HelperIO.saveJSON(current, String.format("%s/%04d_current.json", expFolder, Simulation.rightTW));
        HelperIO.saveJSON(preDecisionSpaceObj, String.format("%s/%04d_decisions.json", expFolder, Simulation.rightTW));

        // Find best assignment
        Map<Vehicle, Set<VehicleState>> vehiclePreDecisionsMap = preDecisionStateSpace.getVehicleDecisionsMap();
        AssignmentILP assignment = new AssignmentILP(getVisitObjMap(vehiclePreDecisionsMap), unassignedRequests);
        assignment.run();

        // Create reward object sorted according to vehicle list
        // * vehicle_ids
        // * request_count
        // * delays
        RewardObject reward = new RewardObject(vehicles, assignment.getResult());
        HelperIO.saveJSON(reward, String.format("%s/%04d_reward.json", expFolder, Simulation.rightTW));

        int nTimeStepsForward = 1 * Simulation.timeWindow;
        for (int timeStep = Simulation.timeWindow; timeStep <= nTimeStepsForward; timeStep += Simulation.timeWindow) {
            StateSpace postDecisionStateSpace = new PostDecisionStateSpace(preDecisionStateSpace, timeStep);
            DecisionSpaceObject postDecisionStateSpaceObj = postDecisionStateSpace.getDecisionSpaceObject();
            HelperIO.saveJSON(postDecisionStateSpaceObj, String.format("%s/%04d_post_decisions_step=%04d.json", expFolder, Simulation.rightTW, timeStep));
        }
        //assert vehiclesVisitsSameOrder(vehicleVisitsMap, preDecisionStateSpace.getVehicleDecisionsMap());

//        int timestepSec = Simulation.timeWindow;
//        PostDecisionStateSpace postDecisionStateSpace = new PostDecisionStateSpace(preDecisionStateSpace, timestepSec);
//        DecisionSpaceObject postDecisionSpaceObj = postDecisionStateSpace.getStateObject();


        experiences.add(new Experience(currentTime, preDecisionStateSpace, assignment.getResult()));
        if (experiences.size() > 5) {
            Experience pastExp = experiences.get(0);
            System.out.println("# ASSIGNMENT PAST" + Simulation.rightTW);
            AssignmentILP assignmentPast = new AssignmentILP(getVisitObjMap(pastExp.decisions), requests);
            Experience e = new Experience(currentTime, pastExp.state, pastExp.decisions, assignmentPast.getResult());
            assert e.rewardRequest == pastExp.rewardRequest;
            assert e.rewardDelay == pastExp.rewardDelay;

        }

        return assignment.getResult();
    }

    private Map<Vehicle, Set<VisitObj>> getVisitObjMap(Map<Vehicle, Set<VehicleState>> vehiclePreDecisionsMap) {
        Map<Vehicle, Set<VisitObj>> map = new HashMap<>();
        for (Map.Entry<Vehicle, Set<VehicleState>> e : vehiclePreDecisionsMap.entrySet()) {
            map.put(e.getKey(), e.getValue().stream().map(o -> (VisitObj) o).collect(Collectors.toSet()));
        }
        return map;
    }


    @Override
    public void realize(Set<VisitObj> visits) {
        visits.forEach(this::realizeVisit);
    }
}

