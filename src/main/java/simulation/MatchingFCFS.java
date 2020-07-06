package simulation;

import config.Config;
import config.Rebalance;
import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import java.nio.file.Path;
import java.util.*;

public class MatchingFCFS implements RideMatchingStrategy {

    /* METHOD CONFIGURATION*/
    private int maxPermutationsFCFS;
    private boolean allPermutations;
    private boolean stopAtFirstBest;
    private boolean checkInParallel;
    private Path outputFile;


    /**
     * Create a vehicle of capacity "capacity" positioned at a random node.
     *
     * @param u Capacity of vehicle
     * @return Vehicle at random position
     */
    public static Vehicle createVehicleAtRandomPosition(User u, int currentTime, int contractDuration) {

        int closestRegionCenterId = Dao.getInstance().getClosestRegion(u.getNodePk().getNetworkId(), u.getPerformanceClass());

        // When vehicle have to be released
        int contractDeadline = currentTime;
        int distOriginPkUser = Dao.getInstance().getDistSec(closestRegionCenterId, u.getNodePk().getNetworkId());

        int distPkDp = u.getDistFromTo();
        //System.out.println("Distance origin pickup user: " + distOriginPkUser);
        //System.out.println("   Distance pickup delivery: " + distPkDp);
        //System.out.println("               Current time: " + contractDeadline);
        //System.out.println("              User deadline: (pk:" + u.getNodePk().getLatest() + " / dp:"+ u.getNodeDp().getLatest()+ ")");
        //System.out.println("   Distance pickup delivery: " + distPkDp);

        // Deadline is the delivery time of user who caused hiring
        if (contractDuration == Simulation.DURATION_SINGLE_RIDE) {
            contractDeadline += distOriginPkUser + distPkDp;
            //System.out.println("  (single) Contract deadline: " + contractDeadline);

        } else {
            contractDeadline += contractDuration;
            //System.out.println("          Contract deadline: " + contractDeadline);
        }

        return new Vehicle(u.getNumPassengers(), closestRegionCenterId, currentTime, true, contractDeadline);
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
    boolean hireNewVehicleToUser(User u,  Matching configMatching) {

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

        if (!rebalanceUtil.allowManyToOneTarget) {
            // Vehicles are sent to the same attractive places in the network repeatedly
            Vehicle.setOfHotPoints.add(pk);

            // Do not send vehicles to the same places
        } else if (!Node.tabu.contains(pk.getNetworkId())) {
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
            v = createVehicleAtRandomPosition(
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
            List<User> unassignedRequests,
            List<Vehicle> listVehicles,
            Matching configMatching) {

        ResultAssignment result = new ResultAssignment(currentTime);

        /* METHOD CONFIGURATION*/
        maxPermutationsFCFS = 100; //Restrict the number of permutations
        allPermutations = true;  //True, if all permutations (considering user+vehicle stops) should be tested
        stopAtFirstBest = false;  //True, if solution is returned when first user/vehicle combination is found
        checkInParallel = false; //True, if visits should be checked in parallel

        // Set of users serviced
        Set<User> setServicedUsers = new HashSet<>();

        /*
        // Loop users
        List<Visit> population = new ArrayList<>();

        System.out.println("########## FLEET ###################################################");
        for(Vehicle v:listVehicles){
            System.out.println(v.getJourney());
        }

        for (User u : setWaitingUsers) {

            // Aux. best visit for comparison
            Visit bestVisit = u.getBestVisitByInsertion(
                    listVehicles,
                    currentTime,
                    stopAtFirstBest);
            population.add(bestVisit);
        }
        System.out.println("\n###### POPULATION:");
        for (Visit visit: population) {
            System.out.println(visit);
        }
        */

        // Loop users
        for (User u : unassignedRequests) {

            // User waits one or more rounds
            u.increaseRoundsWaiting();

            //################### REBALANCE ##### REBALANCE ##### REBALANCE ############################################
            // All points are relocation targets BUT failed pickups have priority
            computeAttractivenessLocationUser(u, configMatching.rebalanceUtil);

            // Aux. best visit for comparison
            Visit bestVisit = u.getBestVisitByInsertion(
                    listVehicles,
                    currentTime,
                    stopAtFirstBest);

            //##########################################################################################################
            //## VISIT WAS NOT FOUND ###################################################################################
            //##########################################################################################################

            // Depending on user class, decide if new vehicle will be created.
            if (bestVisit == null) {

                //################### REBALANCE ##### REBALANCE ##### REBALANCE ########################################
                // Some vehicle should be sent there urgently to fix supply-demand imbalance
                if (configMatching.rebalanceUtil.useUrgentKey)
                    u.getNodePk().increaseUrgency();

                // Try to hire new vehicle to user according to user SQ class
                if (hireNewVehicleToUser(u, configMatching)) {

                    result.roundPrivateRides.add(u);

                    bestVisit = getVisitHiredVehicleUser(u, currentTime, configMatching);

                    result.addHiredVehicle(bestVisit.getVehicle());


                } else if (configMatching.isAllowedToLowerServiceLevel) {

                    // Add  delay to user service level to guarantee he will be picked up
                    u.lowerServiceLevel(Config.getInstance().qosDic.get(u.getPerformanceClass()).pkDelay);

                    // Compute need for urgent relocation
                    if (configMatching.rebalanceUtil.useUrgentKey)
                        u.getNodePk().increaseUrgency();

                    // Find a visit using the TRUE fleet availability
                    bestVisit = u.getBestVisitByInsertion(
                            listVehicles,
                            currentTime,
                            stopAtFirstBest);

                    // After lowering service level, user can't be picked up
                    if (bestVisit == null) {
                        bestVisit = getVisitHiredVehicleUser(u, currentTime, configMatching);
                    }
                } else {
                    if (configMatching.rebalanceUtil.showInfo)
                        System.out.println("CAN'T SERVICE - User:" +
                                u + " - Node PK: " +
                                u.getNodePk() +
                                " - Node PK network id:" +
                                u.getNodePk().getNetworkId() +
                                " - Increasing");
                    // User is rejected if no vehicle could be hired and service level could not be met
                }
            }

            // If best visit is found, update vehicle with visit data
            if (bestVisit != null) {
                result.addVisit(bestVisit);
            }
        }

        // Return all serviced users
        return result;


    }
}