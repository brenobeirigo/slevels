package model.visit;

import model.PathBuilder;
import model.Vehicle;
import model.demand.User;
import model.node.Node;
import simulation.Environment;
import util.pdcombinatorics.PDGeneratorSingleInsertion;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class VisitBuilder {

    Environment env;

    public VisitBuilder(Environment env) {
        this.env = env;
    }


    public VisitObj getBestVisitFromInsertion(Vehicle vehicle, User request) {
        // A single request can be inserted in a vehicle in multiple ways. Only the best (i.e., the lowest delay)
        // visit is inserted in the RTV graph.

        //Logging.logger.info("{}", String.format("User %s, vehicle=%s, visit=%s\n", request, vehicle, vehicle.getVisit()));

        // Do not try inserting request in the same vehicle again
        if (vehicle.hasAlreadyBeenAssignedToUser(request)) {
            //Logging.logger.info("{}", String.format("User %s already in %s. Visit=%s.\n", request, vehicle, vehicle.getVisit()));
            return vehicle.getVisit();
        }

        Visit visit = null;
        Node[] lowestDelaySequence = null;
        PathBuilder bestDraftVisit = null;

        PDGeneratorSingleInsertion perms = new PDGeneratorSingleInsertion(request, vehicle);

        while (perms.hasNext()) {

            Node[] PDPermutation = perms.next();
            //Logging.logger.info(Arrays.asList(PDPermutation));
            //Logging.logger.info(vehicle.getVisit());
            PathBuilder draftVisit = env.getDraftVisit(vehicle, PDPermutation);

            // Update if delay is valid
            if (draftVisit != null) {
                if (draftVisit.compareTo(bestDraftVisit) < 0) {
                    bestDraftVisit = draftVisit;
                    lowestDelaySequence = PDPermutation;
                }
            }
        }

        if (lowestDelaySequence != null) {

            // Setup new visit
            Set<User> requests = new HashSet<>();
            requests.add(request);
            if (vehicle.isServicing()) {
                requests.addAll(vehicle.getVisit().getRequests());
            }

            visit = new Visit(lowestDelaySequence, bestDraftVisit.delay, bestDraftVisit.idleness, vehicle, requests);
        }

        return visit;
    }

    public VisitObj getValidVisitForUser(Vehicle v, User candidateRequest) {
        return getBestVisitFromInsertion(v, candidateRequest);
    }

    /**
     * Try to insert the user in a list of vehicles, and return the best insertion.
     */
    public VisitObj getBestVisitByInsertion(Set<Vehicle> listVehicles, int currentTime, boolean stopAtFirstBest, User candidateRequest) {

        VisitObj bestVisit = null;

        // Try to insert user in each vehicle
        for (Vehicle v : listVehicles) {
            VisitObj candidateVisit = getValidVisitForUser(v, candidateRequest);

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
        return bestVisit;
    }


    public Visit getVisitWithoutRequest(Vehicle vehicle, User request) {

        // Create request set out of one request
        Set<User> requestsWithoutUser = new HashSet<>(vehicle.getVisit().getRequests());
        requestsWithoutUser.remove(request);

        // Remove request from current sequence
        List<Node> sequenceWithoutRequest = new LinkedList<Node>(vehicle.getVisit().getSequenceVisits());
        sequenceWithoutRequest.remove(request.getNodePk());
        sequenceWithoutRequest.remove(request.getNodeDp());

        Visit visit;

        // Vehicle only had this request. Therefore, removing it means rebalancing vehicle to closest middle node
        // in the path to picking up this request.
        if (sequenceWithoutRequest.isEmpty()) {
            visit = this.env.getVisitRelocationToMiddle(vehicle);
        } else {
            // There is a visit where vehicle's remaining requests and passengers will be picked up
            Node[] sequence = sequenceWithoutRequest.toArray(new Node[sequenceWithoutRequest.size()]);
            PathBuilder draftVisit = env.getDraftVisit(vehicle, sequence);
            visit = new Visit(sequence, draftVisit.getDelay(), draftVisit.getIdleness(), vehicle, requestsWithoutUser);
        }

        visit.setVehicle(vehicle);
        return visit;
    }

    public Visit getBestVisitWithoutRequest(Vehicle vehicle, User request) {
        Set<User> requests = new HashSet<>(vehicle.getVisit().getRequests());
        requests.remove(request);
        // Find best visit without removed request
        Visit bestVisit = Environment.getBestVisitFromPDPermutationsSummarized(vehicle, requests, env);

        // When bestVisit is null, it means there are no requests or passengers
        if (bestVisit == null) {
            bestVisit = this.env.getVisitRelocationToMiddle(vehicle);
        }

        return bestVisit;
    }

}
