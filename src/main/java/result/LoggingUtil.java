package result;

import dao.Dao;
import dao.Logging;
import model.demand.User;
import model.node.Node;
import model.visit.Visit;
import simulation.Environment;

import java.util.List;

public class LoggingUtil {
    /**
     * Try to insert the user in a list of vehicles, and return the best best insertion.
     *
     * @return Best visit, or null
     * @param user
     */
//    public Visit getBestVisitByInsertion2(List<Vehicle> listVehicles, int currentTime, boolean stopAtFirstBest) {
//
//        Visit bestVisit = null;
//
//        // Try to insert user in each vehicle
//        for (Vehicle v : listVehicles) {
//
//            // Rebalancing vehicles cannot service users
//            //if(v.isRebalancing()){
//            //   continue;
//            //}
//
//                /*todo - CHECK IN PARALLEL
//                if (checkInParallel) {
//
//                    // Sequence with user to be added in vehicle
//                    Set<User> auxUserSequence = new HashSet<>();
//                    auxUserSequence.add(u);
//
//                    candidateVisit = Method.getBestInsertionParallel(auxUserSequence, v, 2, true, maxPermutationsFCFS);
//
//                */
//
//            //######################################################################################################
//            Visit candidateVisit = v.getVisitWithInsertedUser(this, currentTime);
//            //Visit candidateVisit = v.getBestInsertionOld(u, currentTime);
//            //######################################################################################################
//            //if (candidateVisit != null)
//            //    visitList.add(candidateVisit);
//
//            // Update best visit if delay of candidate visit is shorter
//            if (candidateVisit != null && candidateVisit.compareTo(bestVisit) < 0) {
//
//                // Updating visit
//                bestVisit = candidateVisit;
//
//                // Stop at the first improvement
//                if (stopAtFirstBest) {
//                    break;
//                }
//            }
//        }
//        return bestVisit;
//    }
    public static void printDetailed(User user, Environment env) {
        Logging.logger.info("{}", String.format("%s(%s) - %s[%d](%d << %d - %d << %d) -> %s[%d](%d << %d - %d << %d) - Distance(s): %d - Distance(km): %f -  #Passengers: %d",
                user,
                user.getPerformanceClass(),
                user.getNodePk(),
                user.getNodePk().getNetworkId(),
                user.getNodePk().getEarliest(),
                user.getNodePk().getArrival(),
                user.getNodePk().getDeparture(),
                user.getNodePk().getLatest(),
                user.getNodeDp(),
                user.getNodeDp().getNetworkId(),
                user.getNodeDp().getEarliest(),
                user.getNodeDp().getArrival(),
                user.getNodeDp().getDeparture(),
                user.getNodeDp().getLatest(),
                env.getNetwork().getDistSec(user.getNodePk().getId(), user.getNodeDp().getNetworkId()),
                env.getNetwork().getDistance(user.getNodePk().getNetworkId(), user.getNodeDp().getNetworkId()),
                user.getNumPassengers()));
    }

    /**
     * Detailed information of visit leg.
     * Example:
     * --> { 183} -->DP2164916[ 1] (earliest=  1, departure=     453,   arrival=0, latest=419)
     *
     * @param visit
     * @param currentNode
     * @param loadVehicleAtCurrentNode
     * @param arrivalAtCurrentNode
     * @param distBetweenPreviousAndCurrentNodes
     * @return
     */
    public static String legInfo(Visit visit, Node currentNode, int loadVehicleAtCurrentNode, Integer arrivalAtCurrentNode, int distBetweenPreviousAndCurrentNodes) {
        return String.format(
                "%s%s(%s)[%2d/%2d] (e=%7s, a=%7s, l=%7s / Δe=%3s, Δa=%3s, Δl=%3s)",
                distBetweenPreviousAndCurrentNodes == Integer.MIN_VALUE ? "" : String.format("--> {%4s} -->", distBetweenPreviousAndCurrentNodes),
                currentNode,
                currentNode.getNetworkId(),
                loadVehicleAtCurrentNode,
                visit.getVehicle().getCapacity(),
                currentNode.getEarliest(),
                arrivalAtCurrentNode == null ? "INF" : arrivalAtCurrentNode,
                currentNode.getLatest() == Integer.MAX_VALUE ? "INF" : currentNode.getLatest(),
                currentNode.getEarliest() == 0 || arrivalAtCurrentNode == null ? "-" : arrivalAtCurrentNode - currentNode.getEarliest(),
                currentNode.getArrivalSoFar() == null || arrivalAtCurrentNode == null ? "INF" : currentNode.getArrivalSoFar() - arrivalAtCurrentNode,
                currentNode.getLatest() == Integer.MAX_VALUE || arrivalAtCurrentNode == null ? "INF" : currentNode.getLatest() - arrivalAtCurrentNode
        );
    }
}
