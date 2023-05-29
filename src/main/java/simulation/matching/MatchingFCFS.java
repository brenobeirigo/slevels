package simulation.matching;

import com.google.common.collect.Iterables;
import config.Config;
import dao.Dao;
import model.demand.User;
import model.Vehicle;
import model.visit.Visit;
import model.visit.VisitBuilder;
import model.visit.VisitObj;
import model.node.Node;
import simulation.Environment;
//import simulation.hiring.HiringFromCenters;
import simulation.rebalancing.Rebalance;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MatchingFCFS implements RideMatchingStrategy {

    /* METHOD CONFIGURATION*/
    private int maxPermutationsFCFS;
    private boolean allPermutations;
    private boolean stopAtFirstBest;
    private boolean checkInParallel;
    private Path outputFile;
    private Environment env;
    private VisitBuilder visitBuilder;

    public MatchingFCFS(int maxPermutationsFCFS, boolean allPermutations, boolean stopAtFirstBest, boolean checkInParallel) {
        this.maxPermutationsFCFS = maxPermutationsFCFS;
        this.allPermutations = allPermutations;
        this.stopAtFirstBest = stopAtFirstBest;
        this.checkInParallel = checkInParallel;
    }

    public void setEnv(Environment env){
        this.env = env;
        this.visitBuilder = new VisitBuilder(env);
    }

    @Override
    public void realizeVisit(VisitObj visit) {

        // Does nothing if same visit chosen (e.g., continue rebalancing)
        if (visit.getVehicle().getVisit() == visit)
            return;


        // If vehicle was rebalancing, compute the rebalancing distance until middle
        if (visit.getVehicle().isRebalancing()) {
            visit.getVehicle().computeDistanceTraveledRebalancingUntilMiddle();
            visit.getVehicle().setStoppedRebalanceToPickup(true);
        }

        // Add visit to vehicle (circular)
        visit.getVehicle().setVisit(visit);

        // Assign visit to requests
        for (User request : visit.getRequests()) {

            // If request was assigned to another vehicle, remove it from there
            if (request.getCurrentVehicle() != null && !request.getCurrentVehicle().equals(visit.getVehicle())) {
                Vehicle vehiclePreviouslyAssignedToRequest = request.getCurrentVehicle();
                Visit visitWithoutRequest = visitBuilder.getVisitWithoutRequest(vehiclePreviouslyAssignedToRequest, request);
                vehiclePreviouslyAssignedToRequest.setVisit(visitWithoutRequest);

                // Assign visit without requests to its users
                for (User u : Iterables.concat(visitWithoutRequest.getRequests(), visitWithoutRequest.getPassengers())) {
                    u.setCurrentVisit(visitWithoutRequest);
                }
            }

            request.setCurrentVisit(visit);

        }

        // Assign visit to passengers
        for (User passenger : visit.getPassengers()) {
            passenger.setCurrentVisit(visit);
        }

        // Go through nodes and update arrival so far
        env.updateArrivalSoFarAtVisitNodes(visit);

        // Vehicle is not idle
        visit.getVehicle().setRoundsIdle(0);

        //TODO WARNING!!!!!!!!!!! FIX
//        env.updateMiddle(Environment.currentTime, visit.getVehicle());

    }

    /**
     * Get a new vehicle to user:
     * - According to a probability
     * > For 100% service rate class, every missed pickup entails the creation of a new vehicle.
     * > For 0% service rate class, vehicle is never created
     * - If user has been delayed "maxDelayExtensionsBeforeHiring" times
     *
     * @param u User that will potentially have a new vehicle hired
     * @return True, if vehicle should be hired
     */
    boolean hireNewVehicleToUser(User u, Matching configMatching) {

        if (!configMatching.isAllowedToHire) {
            return false;
        }

        // Draw to decide if customer will be serviced
        double draw = Dao.getInstance().rand.nextDouble();

        // Get service rate of user u
        double serviceRate = Config.getInstance().qosDic.get(u.getPerformanceClass()).serviceRate;

        //Logging.logger.info("{}", String.format("\n2) %f <= %f : User: %s", draw, serviceRate, u));

        // E.g., B - 0.8 <= 1? Yes! Add vehicle and try again to service customer
        // E.g., A - 1.0 <= 1? Yes! Add vehicle and try again to service customer
        return (draw <= serviceRate);
    }

    /**
     * Test whether it is beneficial send different vehicles to service users in the same locations.
     *
     * @param u
     */
    public void computeAttractivenessLocationUser(User u, Rebalance rebalanceUtil) {

        Node pk = u.getNodePk();

        // This node should have more vehicles around it
        pk.increaseHotness();

        //if (!rebalanceUtil.allowManyToOneTarget) {
        // Vehicles are sent to the same attractive places in the network repeatedly
        Vehicle.setOfHotPoints.add(pk);

        // Do not send vehicles to the same places
        //} else
        if (!Node.tabu.contains(pk.getNetworkId())) {
            // Only add points not yet being addressed by other vehicles
            Vehicle.setOfHotPoints.add(pk);
        }
    }
//
//
//    private Visit getVisitHiredVehicleUser(User u, int currentTime, Matching configMatching) {
//
//        // The draw was successful, vehicle will be hired immediately
//        Vehicle v = null;
//
//        Visit candidateVisitHiredVehicle = null;
//
//        // Find freelance vehicle around user area
//        while (candidateVisitHiredVehicle == null) {
//
//            // For the sake of fairness, position is left to chance
//            v = HiringFromCenters.createVehicleAtClosestRegionalCenter(
//                    u,
//                    currentTime,
//                    configMatching.contractDuration); // List of vehicles
//
//
//            //##########################################################################################
//            // Try to get a valid visit by inserting user "u" in newly created vehicle "v"
//            candidateVisitHiredVehicle = v.getVisitWithInsertedUser(u, currentTime);
//            //##########################################################################################
//        }
//
//        return candidateVisitHiredVehicle;
//    }

    /**
     * Try to insert a user in every vehicle, and return the set of users inserted.
     * <p>
     * If user cannot be inserted:
     * - Hire a new vehicle
     * - Lower user service level (increase maximum delays)
     *
     * @return setServicedUsers Set of all users that could be inserted into vehicles
     */
    @Override
    public ResultAssignment match(
            int currentTime,
            Set<User> unassignedRequests,
            Set<Vehicle> listVehicles,
            Set<Vehicle> hired) {

        ResultAssignment result = new ResultAssignment(currentTime);


        Map<User, Visit> userVisitMap = new HashMap<>();
        Map<Vehicle, Visit> vehicleOriginalVisit = new HashMap<>();
        VisitBuilder b = new VisitBuilder(env);
        // Loop users
        for (User u : unassignedRequests) {

            // Aux. best visit for comparison
            VisitObj bestVisit = b.getBestVisitByInsertion(
                    listVehicles,
                    currentTime,
                    stopAtFirstBest,
            u);

            if (bestVisit != null) {
                realizeVisit(bestVisit);
                assertRequestsAndPassengersAreInVehicleCarryingOutVisit(bestVisit);
                result.addVisit(bestVisit);
            } else {
                result.accountRejected(u);
            }
        }

        // Return all serviced users
        return result;


    }

    @Override
    public void realize(Set<VisitObj> visits) {

    }

    @Override
    public String toString() {
        return "_OPT-FCFS";
    }

    private void assertRequestsAndPassengersAreInVehicleCarryingOutVisit(VisitObj visit) {
        for (User requests : visit.getRequests()) {
            assert requests.getCurrentVehicle() == visit.getVehicle() : String.format("Request current vehicle = %s, Best visit vehicle=%s", requests.getCurrentVehicle(), visit.getVehicle());
        }
        for (User passengers : visit.getPassengers()) {
            assert passengers.getCurrentVehicle() == visit.getVehicle() : String.format("Passenger current vehicle = %s, Best visit vehicle=%s", passengers.getCurrentVehicle(), visit.getVehicle());
        }
    }
}