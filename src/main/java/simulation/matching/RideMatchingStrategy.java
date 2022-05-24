package simulation.matching;

import model.User;
import model.Vehicle;
import model.Visit;
import model.VisitObj;


import java.util.Set;

public interface RideMatchingStrategy {
    ResultAssignment match(int currentTime, Set<User> requests, Set<Vehicle> vehicles, Set<Vehicle> hired);
    void realize(Set<VisitObj> visits);
    void realizeVisit(VisitObj visit);
}
