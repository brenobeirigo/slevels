package model.graph;

import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.paukov.combinatorics.Generator;
import org.paukov.combinatorics.ICombinatoricsVector;
import simulation.Method;

import java.util.*;

public class GraphRV {

    public int vehicleCapacity;

    SimpleGraph<Object, DefaultEdge> graphRV;

    public GraphRV(List<User> allRequests, List<Vehicle> listVehicles, int vehicleCapacity, int maxVehReqEdges, int maxReqReqEdges) {

        this.vehicleCapacity = vehicleCapacity;

        // Which requests can be cobined?
        // What happens with visits with passengers:
        graphRV = getRVGraph(
                allRequests,
                listVehicles,
                maxVehReqEdges,
                maxReqReqEdges
        );
    }


    public void updateRR(List<User> listRequests,
                         SimpleGraph<Object, DefaultEdge> graphRV,
                         int maxNumberOfEdges,
                         int maxVehicleCapacity) {

        for (int i = 0; i < listRequests.size() - 1; i++) {

            // Request r1 data
            User r1 = listRequests.get(i);
            Node pk1 = r1.getNodePk();
            Node dp1 = r1.getNodeDp();

            for (int j = i + 1; j < listRequests.size(); j++) {

                // Request r2 data
                User r2 = listRequests.get(j);
                Node pk2 = r2.getNodePk();
                Node dp2 = r2.getNodeDp();

                LinkedList<Node> seq1 = new LinkedList<>(Arrays.asList(pk1, pk2, dp1, dp2));
                LinkedList<Node> seq2 = new LinkedList<>(Arrays.asList(pk1, pk2, dp2, dp1));
                LinkedList<Node> seq3 = new LinkedList<>(Arrays.asList(pk2, pk1, dp1, dp2));
                LinkedList<Node> seq4 = new LinkedList<>(Arrays.asList(pk2, pk1, dp2, dp1));

                for (LinkedList<Node> seq : Arrays.asList(seq1, seq2, seq3, seq4)) {
                    int delay = Visit.isValidSequence(seq, seq.get(0).getDeparture(), seq.get(0).getLoad(), maxVehicleCapacity);
                    if (delay > 0) {
                        graphRV.addEdge(r1, r2);
                    }
                }
            }
        }
    }

    /**
     * In RV graph, create edges connecting vehicles to requests ( <= maxNumberOfEdges)
     *
     * @param listVehicles     List of all vehicles
     * @param listRequests     List of all requests
     * @param maxNumberOfEdges Max number of vehicles that can serve an user
     */
    public void updateRV(List<Vehicle> listVehicles,
                         List<User> listRequests,
                         SimpleGraph<Object, DefaultEdge> graphRV,
                         int maxNumberOfEdges) {

        // Check if vehicle can visit request
        for (User request : listRequests) {

            // Count of edges connecting requests to candidate vehicles
            int edgesRV = 0;

            // Loop vehicles
            for (Vehicle vehicle : listVehicles) {

                // Stop connecting requests to vehicles if edge count was reached
                if (edgesRV++ >= maxNumberOfEdges) {
                    break;
                }

                // Try to find at least ONE way pickup request
                createEdge(graphRV, request, vehicle);
            }
        }
    }

    private void createEdge(SimpleGraph<Object, DefaultEdge> graphRV, User request, Vehicle vehicle) {

        Generator<Node> gen = Method.getGeneratorOfNodeSequence(new HashSet<>(Collections.singletonList(request)), vehicle);

        for (ICombinatoricsVector<Node> combinationUsersPickupsAndDeliveries : gen) {

            List<Node> sequencePickupsAndDeliveries = combinationUsersPickupsAndDeliveries.getVector();

            LinkedList<Node> sequenceFromVehiclePositionToLastDelivery = Method.addLastVisitedAndMiddleNodesToStart(sequencePickupsAndDeliveries, vehicle);

            if (sequenceFromVehiclePositionToLastDelivery == null)
                continue;

            int delay = Visit.isValidSequence(
                    sequenceFromVehiclePositionToLastDelivery,
                    vehicle.getDepartureCurrent(),
                    vehicle.getCurrentLoad(),
                    vehicle.getCapacity());


            if (delay >= 0) {

                // Connect vehicle to request
                graphRV.addEdge(vehicle, request);

                // If RV exists, there is at least ONE way to pickup up the request.
                // The BEST way will be generated the RTV graph.
                break;

                // System.out.println(vehicle.getLastVisitedNode() + "(" +vehicle.getLastVisitedNode()+ ") - Departure: " + vehicle.getDepartureCurrent() + "(" + currentTime + "), delay=" + delay + " - seq:" + sequenceFromVehiclePositionToLastDelivery + " - Visit:" + vehicle.getVisit());
            }
        }
    }


    /**
     * Pairwise graph of vehicles and requests. Combine vehicles and requests.
     *
     * @param listWaitingUsers
     * @param listVehicles
     * @return
     */
    public SimpleGraph<Object, DefaultEdge> getRVGraph(List<User> listWaitingUsers,
                                                       List<Vehicle> listVehicles,
                                                       int maxVehReqEdges,
                                                       int maxReqReqEdges) {


        // Create RV graph (r1, r2) and (v, r)
        SimpleGraph<Object, DefaultEdge> graphRV = new SimpleGraph<>(DefaultEdge.class);

        // Populating RV graph
        for (User user : listWaitingUsers) {
            graphRV.addVertex(user);
        }

        for (Vehicle vehicle : listVehicles) {
            graphRV.addVertex(vehicle);
        }

        // Add request-request edges
        updateRR(listWaitingUsers, graphRV, maxReqReqEdges, vehicleCapacity);

        // A request r and a vehicle v are connected if the request can be served by the vehicle while satisfying the
        // constraints Z, as given by travel(v, r). Every vehicle is connected to "maxNumberOfEdges" users.
        updateRV(listVehicles, listWaitingUsers, graphRV, maxVehReqEdges);

        return graphRV;
    }

    public Object getEdgeTarget(DefaultEdge edge) {
        return this.graphRV.getEdgeTarget(edge);
    }

    public Set<DefaultEdge> edgesOf(Vehicle vehicle) {
        return graphRV.edgesOf(vehicle);

    }

    public DefaultEdge getEdge(User request1, User request2) {
        return graphRV.getEdge(request1, request2);
    }
}
