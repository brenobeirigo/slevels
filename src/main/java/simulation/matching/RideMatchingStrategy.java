package simulation.matching;

import model.demand.User;
import model.Vehicle;
import model.visit.VisitObj;


import java.util.Set;

public interface RideMatchingStrategy {
    ResultAssignment match(int currentTime, Set<User> requests, Set<Vehicle> vehicles, Set<Vehicle> hired);
    void realize(Set<VisitObj> visits);
    void realizeVisit(VisitObj visit);
}
