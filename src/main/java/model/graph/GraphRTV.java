package model.graph;

import model.demand.User;
import model.Vehicle;
import model.visit.VisitObj;

import java.util.*;

public interface GraphRTV  {
    Set<User> getAllRequests();

    Set<VisitObj> getListOfVisitsFromVehicle(Vehicle vehicle);


    Map<Integer, Integer> getPickupLocationCandidateVehicleCountMap();

    GraphRV getGraphRV();

    Set<VisitObj> getAllVisits();

    Set<Vehicle> getListVehiclesFromRTV();

    Set<VisitObj> getListOfVisitsFromUser(User request);

    double getWeightFromRequestVisitEdge(User request, VisitObj visit);

    Set<Vehicle> getHiredVehiclesFromUser(User request);

    Set<Vehicle> getListVehicles();

    Map<Vehicle, Set<VisitObj>> getVehicleVisitsMap();

    Map<User, Set<VisitObj>> getUserVisitsMap();

    void printAllVisitsPerVehicle();
}
