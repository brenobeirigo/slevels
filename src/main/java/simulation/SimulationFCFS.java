package simulation;

import config.Config;
import dao.Dao;
import helper.MethodHelper;
import model.User;
import model.Vehicle;
import model.Visit;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class SimulationFCFS extends Simulation {

    /* METHOD CONFIGURATION*/
    private int maxPermutationsFCFS;
    private boolean allPermutations;
    private boolean stopAtFirstBest;
    private boolean checkInParallel;
    private Path outputFile;

    /* Construct FCFS simulation */
    public SimulationFCFS(String methodName,
                          int initialFleet,
                          int vehicleMaxCapacity,
                          int maxRequestsIteration,
                          int timeWindow,
                          int timeHorizon,
                          int maxRoundsIdleBeforeRebalance,
                          int deactivationFactor,
                          int maxDelayExtensionsBeforeHiring,
                          boolean isAllowedToHire,
                          boolean isAllowedToLowerServiceLevel,
                          String serviceRateScenarioLabel,
                          String segmentationScenarioLabel) {


        // Build generic Simulation object
        super(initialFleet,
                vehicleMaxCapacity,
                maxRequestsIteration,
                timeWindow,
                timeHorizon,
                maxRoundsIdleBeforeRebalance,
                deactivationFactor,
                maxDelayExtensionsBeforeHiring,
                isAllowedToHire,
                isAllowedToLowerServiceLevel);

        // Service rate and segmentation scenarios
        this.serviceRateScenarioLabel = serviceRateScenarioLabel;
        this.segmentationScenarioLabel = segmentationScenarioLabel;

        /* METHOD CONFIGURATION*/
        maxPermutationsFCFS = 100; //Restrict the number of permutations
        allPermutations = true;  //True, if all permutations (considering user+vehicle stops) should be tested
        stopAtFirstBest = false;  //True, if solution is returned when first user/vehicle combination is found
        checkInParallel = false; //True, if visits should be checked in parallel

        // Initialize solution
        sol = new Solution(
                methodName,
                initialFleet,
                maxRequestsIteration,
                vehicleMaxCapacity,
                timeWindow,
                timeHorizon,
                maxRoundsIdleBeforeRebalance,
                deactivationFactor,
                maxDelayExtensionsBeforeHiring,
                isAllowedToHire,
                isAllowedToLowerServiceLevel,
                serviceRateScenarioLabel,
                segmentationScenarioLabel);
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
    boolean hireNewVehicleToUser(User u) {

        if (!this.isAllowedToHire) {
            return false;
        }

        // User was already chosen to be delayed
        if (u.getNumberOfDelayExtensions() > 0) {
            return false;
        }

        // Draw to decide if customer will be serviced
        double draw = Dao.getInstance().rand.nextDouble();

        // Get service rate of user u
        double serviceRate = Config.getInstance().qosDic.get(u.getPerformanceClass()).serviceRate;

        //System.out.print(String.format("\n2) %f <= %f : User: %s", draw, serviceRate, u));

        // E.g., B - 0.8 <= 1? Yes! Add vehicle and try again to service customer
        // E.g., A - 1.0 <= 1? Yes! Add vehicle and try again to service customer
        return (draw <= serviceRate || u.getNumberOfDelayExtensions() >= this.maxDelayExtensionsBeforeHiring);
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
    //@Override
    public Set<User> getServicedUsersDynamicSizedFleet(int currentTime) {

        // Set of users serviced
        Set<User> setServicedUsers = new HashSet<>();

        // Loop users
        for (User u : setWaitingUsers) {

            // User waits one or more rounds
            u.increaseRoundsWaiting();

            // Compute hot point
            Vehicle.computeHotPoint(u);

            // Aux. best visit for comparison
            Visit bestVisit = u.getBestVisitByInsertion(listVehicles, currentTime, stopAtFirstBest);

            //##########################################################################################################
            //## VISIT WAS NOT FOUND ###################################################################################
            //##########################################################################################################

            // Depending on user class, decide if new vehicle will be created.
            if (bestVisit == null) {

                // Add unserviced point to set of hot points (customers not serviced)
                Vehicle.computeMissedUserLocation(u);

                if (hireNewVehicleToUser(u)) {

                    //if(u.getNumberOfDelayExtensions()>0){
                    //    System.out.println(String.format("DELAY FAILED :-(     %s(%d) WILL BE SERVICED BY A THIRD-PARTY VEHICLE.", u, u.getNumberOfDelayExtensions()));
                    //}

                    Vehicle v = null;

                    u.computeHiring();

                    // Customer gets a private ride every
                    Visit candidateVisit = null;

                    // Add extra delay to user service level and increase urgency to be picked up (=pickup delay)
                    //u.lowerServiceLevel();

                    //int hiring = 0;
                    // Update best visit if delay of candidate visit is shorter

                    while (candidateVisit == null) {

                        //TODO MAJOR FLAW - Vehicles are created at user's position
                        //v = new Vehicle(u.getNumPassengers(), u.getNodePk().getNetworkId(), currentTime, true);
                        v = MethodHelper.createVehicleAtRandomPosition(u.getNumPassengers(), currentTime); // List of vehicles

                        //System.out.println(++hiring + " - "+ v.getOrigin().getNetworkId() + " - " + u.getNodePk().getNetworkId());

                        //##########################################################################################
                        // Try to get a valid visit by inserting user "u" in newly created vehicle "v"
                        candidateVisit = v.getBestInsertion(u, currentTime);
                        //##########################################################################################
                    }

                    // Update best visit
                    bestVisit = candidateVisit;

                    // New vehicle is added in list
                    listVehicles.add(v);

                } else if (canLowerServiceLevel(u)) {
                    /* Customer is serviced using the TRUE availability of the fleet.
                       The service level is relaxed (unbounded) and an available vehicle is chosen. */

                    // Increase number of insertion trials
                    u.increaseNumberOfDelayExtensions();

                    // Add enough delay to user service level to guarantee he will be picked up
                    u.lowerServiceLevel(24 * 3600);

                    // Find a visit using the TRUE fleet availability
                    bestVisit = u.getBestVisitByInsertion(
                            listVehicles,
                            currentTime,
                            stopAtFirstBest);

                    // There is no vehicle of with capacity == number of passengers
                    if (bestVisit == null) {

                        // Creating vehicle nearby user
                        Vehicle v = MethodHelper.createVehicleAtRandomPosition(u.getNumPassengers(), currentTime); // List of vehicles

                        // Inserting user in newly created vehicle
                        bestVisit = v.getBestInsertion(u, currentTime);

                        // Adding created vehicle to fleet
                        listVehicles.add(v);

                    }
                }
            }

            // If best visit is found, update vehicle with visit data
            if (bestVisit != null) {

                // Add user to vehicle and setup visit
                bestVisit.setup();

                // Model.User u was serviced
                setServicedUsers.add(u);
            }
        }

        // Return all serviced users
        return setServicedUsers;

    }


    private boolean canLowerServiceLevel(User u) {
        return this.isAllowedToLowerServiceLevel && u.getNumberOfDelayExtensions() < this.maxDelayExtensionsBeforeHiring;
    }
}
