package model.graph;

import model.User;
import model.Vehicle;
import model.Visit;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface GraphRTV  {
    Set<User> getAllRequests();

    Set<Visit> getListOfVisitsFromVehicle(Vehicle vehicle);

    Set<Visit> getAllVisits();

    Set<Vehicle> getListVehiclesFromRTV();

    Set<Visit> getListOfVisitsFromUser(User request);

    double getWeightFromRequestVisitEdge(User request, Visit visit);

    Set<Vehicle> getHiredVehiclesFromUser(User request);

    Set<Vehicle> getListVehicles();
}
