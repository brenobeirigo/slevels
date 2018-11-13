package simulation;

import config.Config;
import dao.Dao;
import helper.MethodHelper;
import model.User;
import model.Vehicle;
import model.Visit;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SimulationFCFS extends Simulation {

    /* METHOD CONFIGURATION*/
    private int maxPermutationsFCFS;
    private boolean allPermutations;
    private boolean stopAtFirstBest;
    private boolean checkInParallel;
    private int maxInsertionTrials = 2;

    /* Construct FCFS simulation */
    public SimulationFCFS(int initialFleet,
                          int vehicleMaxCapacity,
                          int maxRequestsIteration,
                          int timeWindow,
                          int timeHorizon,
                          String serviceRateScenarioLabel,
                          String segmentationScenarioLabel) {

        // Build generic Simulation object
        super(initialFleet,
                vehicleMaxCapacity,
                maxRequestsIteration,
                timeWindow,
                timeHorizon);

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
                "FCFS",
                initialFleet,
                maxRequestsIteration,
                vehicleMaxCapacity,
                timeWindow,
                timeHorizon,
                serviceRateScenarioLabel,
                segmentationScenarioLabel);

    }

    /**
     * Try to insert a user in every vehicle, and return the set of users inserted.
     *
     * @return setServicedUsers Set of all users that could be inserted into vehicles
     */

    //@Override
    public Set<User> getServicedUsersFixedSizeFleet(int currentTime) {

        // Set of users serviced
        Set<User> setServicedUsers = new HashSet<>();

        // Loop users
        for (User u : setWaitingUsers) {
            //System.out.println(u+" - " + u.getNodePk().getEarliest() + " - "+ departureVehicleCurrent);

            // Aux. best visit for comparison
            Visit bestVisit = new Visit();

            // Sort vehicles according to arrival time
            Collections.sort(listVehicles);

            // Try to insert user in each vehicle
            for (Vehicle v : listVehicles) {

                // Final visit
                Visit candidateVisit;

                // Check in parallel
                if (checkInParallel) {

                    // Sequence with user to be added in vehicle
                    Set<User> auxUserSequence = new HashSet<>();
                    auxUserSequence.add(u);

                    candidateVisit = Method.getBestInsertionParallel(auxUserSequence, v, 2, true, maxPermutationsFCFS);

                        /*TODO implement parallel check
                         visit = Trip.gen_visit_parallel([r],
                                v=veh,
                                find_best=find_best_visits,
                                max_trips = max_trips,
                                distance_data = distance_data)
                        */
                } else {

                    // Sequence with user to be added in vehicle
                    //Set<User> auxUserSequence = new HashSet<>();
                    //auxUserSequence.add(u);

                    // #################################################################################################
                    // Get a candidate visit by trying to add user u to vehicle v
                    candidateVisit = v.getBestInsertion(u, currentTime);
                    // #################################################################################################
                }

                //System.out.println(u +","+ v + "="+ candidateVisit);

                // Update best visit if delay of candidate visit is shorter
                if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {

                    // Updating visit
                    bestVisit = candidateVisit;

                    // Stop at the first improvement
                    if (stopAtFirstBest) {
                        break;
                    }
                }
            }

            // if best visit is found, update vehicle with visit data
            if (bestVisit.getSetUsers() != null) {

                // Add user to vehicle and setup visit
                bestVisit.setup();

                // Model.User u was serviced
                setServicedUsers.add(u);

                //TODO: update latest nodes to arrival time
            }
        }

        //Collections.sort(listVehicles);

        //for(Vehicle v:listVehicles){
        //    System.out.println(v.getInfo());
        //}

        // Return all serviced users
        return setServicedUsers;
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

            // Aux. best visit for comparison
            Visit bestVisit = new Visit();

            // Sort vehicles according to arrival time
            //Collections.sort(listVehicles);

            // Try to insert user in each vehicle
            for (Vehicle v : listVehicles) {

                //######################################################################################################
                Visit candidateVisit = v.getBestInsertion(u, currentTime);
                //######################################################################################################

                // Update best visit if delay of candidate visit is shorter
                if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {

                    // Updating visit
                    bestVisit = candidateVisit;

                    // Stop at the first improvement
                    if (stopAtFirstBest) {
                        break;
                    }
                }
            }

            //##########################################################################################################
            //## VISIT WAS NOT FOUND ###################################################################################
            //##########################################################################################################

            // Depending on user class, decide if new vehicle will be created.
            if (bestVisit.getSetUsers() == null) {

                // If user will be rejected in the next round, this is the last opportunity to:
                //  - Service user
                //  - Increase delays ("maxInsertionTrials" times)
                if (u.getNodePk().getLatest() < currentTime + this.timeWindow) {

                    // Draw to decide if customer will be serviced
                    double draw = Dao.getInstance().rand.nextDouble();

                    // Get service rate of user u
                    double serviceRate = Config.getInstance().qosDic.get(u.getPerformanceClass()).serviceRate;

                    //System.out.print(String.format("\n2) %f <= %f : User: %s", draw, serviceRateScenarioLabel, u));

                    /* Get a new vehicle to user:
                       - According to a probability (Notice that for 100% service rate class,
                       every customer entails the creation of a new vehicle.)
                       - If user has been delayed "maxInsertionTrials" times */
                    if (draw <= serviceRate || u.getNumberOfDelayExtensions() >= maxInsertionTrials) {

                        //System.out.print(" OK");
                        //System.out.println(String.format("2) %f <= %f : User: %s", draw, serviceRateScenarioLabel, u));

                        // E.g., B - 0.8 <= 1? Yes! Add vehicle and try again to service customer
                        // E.g., A - 1.0 <= 1? Yes! Add vehicle and try again to service customer
                        Vehicle v = null;

                        Visit candidateVisit = null;

                        // Update best visit if delay of candidate visit is shorter
                        while (candidateVisit == null) {

                            v = MethodHelper.createVehicleAtRandomPosition(u.getNumPassengers()); // List of vehicles

                            //##########################################################################################
                            // Try to get a valid visit by inserting user "u" in newly created vehicle "v"
                            candidateVisit = v.getBestInsertion(u, currentTime);
                            //##########################################################################################
                        }

                        // Update best visit
                        bestVisit = candidateVisit;

                        // New vehicle is added in list
                        listVehicles.add(v);

                    } else {
                        /* POLICY: Every customer is serviced
                          - Lower service level of customer by adding extra delays
                        */

                        // Extra delay is equals to original pickup delay of customer class
                        int extraDelay = Config.getInstance().qosDic.get(u.getPerformanceClass()).pkDelay;

                        // Add extra delay to user service level
                        u.lowerServiceLevel(extraDelay);

                        // Increase number of insertion trials
                        u.increaseNumberOfDelayExtensions();
                    }
                }
            }

            // If best visit is found, update vehicle with visit data
            if (bestVisit.getSetUsers() != null) {

                // Add user to vehicle and setup visit
                bestVisit.setup();

                // Model.User u was serviced
                setServicedUsers.add(u);

                //TODO: update latest nodes to arrival time
            }
        }

        // Return all serviced users
        return setServicedUsers;

    }
}
