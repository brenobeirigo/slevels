package simulation;

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


    public SimulationFCFS() {
        super();

        /* METHOD CONFIGURATION*/
        maxPermutationsFCFS = 100; //Restrict the number of permutations
        allPermutations = true;  //True, if all permutations (considering user+vehicle stops) should be tested
        stopAtFirstBest = true;  //True, if solution is returned when first user/vehicle combination is found
        checkInParallel = false; //True, if visits should be checked in parallel

        // Initialize solution
        sol = new Solution("FCFS", nOfVehicles, maxNumberOfTrips, vehicleCapacity, timeHorizon, totalHorizon);
    }

    /**
     * Try to insert a user in every vehicle, and return the set of users inserted.
     *
     * @return setServicedUsers Set of all users that could be inserted into vehicles
     */

    @Override
    public Set<User> getServicedUsers(int currentTime) {

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
                    Set<User> auxUserSequence = new HashSet<>();
                    auxUserSequence.add(u);

                    // #################################################################################################
                    // Get a candidate visit by trying to add user u to vehicle v
                    //candidateVisit = Method.getVisitByPermutation(auxUserSequence, v, allPermutations, maxPermutationsFCFS);
                    candidateVisit = Method.getBestInsertionNoAddFirst(auxUserSequence, v, currentTime, 2, true, 100);
                    // #################################################################################################
                }

                // Update best visit if delay of candidate visit is shorter
                if (candidateVisit != null && candidateVisit.getDelay() < bestVisit.getDelay()) {

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

        // Return all serviced users
        return setServicedUsers;
    }
}
