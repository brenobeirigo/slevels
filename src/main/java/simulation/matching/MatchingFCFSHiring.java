package simulation.matching;

import config.Config;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import simulation.hiring.HiringFromCenters;
import simulation.rebalancing.Rebalance;

import java.nio.file.Path;
import java.util.*;

public class MatchingFCFSHiring implements RideMatchingStrategy {

    /* METHOD CONFIGURATION*/
    private int maxPermutationsFCFS;
    private boolean allPermutations;
    private boolean stopAtFirstBest;
    private boolean checkInParallel;
    private Path outputFile;

    public MatchingFCFSHiring(int maxPermutationsFCFS, boolean allPermutations, boolean stopAtFirstBest, boolean checkInParallel) {
        this.maxPermutationsFCFS = maxPermutationsFCFS;
        this.allPermutations = allPermutations;
        this.stopAtFirstBest = stopAtFirstBest;
        this.checkInParallel = checkInParallel;
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

        //System.out.print(String.format("\n2) %f <= %f : User: %s", draw, serviceRate, u));

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


    private Visit getVisitHiredVehicleUser(User u, int currentTime, Matching configMatching) {

        // The draw was successful, vehicle will be hired immediately
        Vehicle v = null;

        Visit candidateVisitHiredVehicle = null;

        // Find freelance vehicle around user area
        while (candidateVisitHiredVehicle == null) {

            // For the sake of fairness, position is left to chance
            v = HiringFromCenters.createVehicleAtClosestRegionalCenter(
                    u,
                    currentTime,
                    configMatching.contractDuration); // List of vehicles


            //##########################################################################################
            // Try to get a valid visit by inserting user "u" in newly created vehicle "v"
            candidateVisitHiredVehicle = v.getVisitWithInsertedUser(u, currentTime);
            //##########################################################################################
        }

        return candidateVisitHiredVehicle;
    }

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
            Set<Vehicle> hired,
            Matching configMatching) {

        ResultAssignment result = new ResultAssignment(currentTime);


        Map<User, Visit> userVisitMap = new HashMap<>();
        Map<Vehicle, Visit> vehicleOriginalVisit = new HashMap<>();

        // Loop users
        for (User u : unassignedRequests) {


            // Aux. best visit for comparison
            Visit bestVisit = u.getBestVisitByInsertion(
                    listVehicles,
                    currentTime,
                    stopAtFirstBest);
            System.out.printf("%s - %s\n", u, bestVisit);
            // Depending on user class, decide if new vehicle will be created.
            if (bestVisit == null) {

                // Try to hire new vehicle to user according to user SQ class
                if (hireNewVehicleToUser(u, configMatching)) {

                    result.roundPrivateRides.add(u);

                    bestVisit = getVisitHiredVehicleUser(u, currentTime, configMatching);

                    result.addHiredVehicle(bestVisit.getVehicle());

                } else {

                    if (configMatching.rebalanceUtil.showInfo)
                        System.out.println("CAN'T SERVICE - User:" +
                                u + " - Node PK: " +
                                u.getNodePk() +
                                " - Node PK network id:" +
                                u.getNodePk().getNetworkId() +
                                " - Increasing");
                    // User is rejected if no vehicle could be hired and service level could not be met

                    System.out.println("Cant match user");
                }
            }

            // If best visit is found, update vehicle with visit data
            if (bestVisit != null) {

                Visit.realize(bestVisit, configMatching.rebalanceUtil, currentTime);
                result.addVisit(bestVisit);
                // Save old vehicle configuration
                /*Visit clone = new Visit(bestVisit);
                vehicleOriginalVisit.putIfAbsent(new Vehicle(bestVisit.getVehicle(), clone), clone);

                System.out.println(String.format("# User: %s \n# OLD (%s): %s \n# NEW (%s): %s", u, bestVisit.getVehicle().getVisit().hashCode(), bestVisit.getVehicle().getVisit(), clone.hashCode(), clone));


                bestVisit.getVehicle().setVisit(bestVisit);
                System.out.println(" VEH: " + bestVisit.getVehicle().getVisit() + " - " + bestVisit.getClass());
                for (User visitUser: bestVisit.getRequests())
                    userVisitMap.put(visitUser, bestVisit);

                System.out.println(userVisitMap);*/
            } else {
                result.accountRejected(u);
            }
        }

        /*for (Visit visit : new HashSet<>(userVisitMap.values())) {
            result.addVisit(visit);
        }

        vehicleOriginalVisit.forEach(Vehicle::setVisit);
*/

        // Return all serviced users
        return result;


    }

    @Override
    public void realize(Set<Visit> visits, Rebalance rebalanceUtil, int currentTime) {

    }

    @Override
    public String toString() {
        return "_FCFS";
    }
}