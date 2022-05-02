package simulation.matching;

import model.User;
import model.Vehicle;
import model.Visit;
import simulation.rebalancing.Rebalance;


import java.util.Set;

public interface RideMatchingStrategy {
    ResultAssignment match(int currentTime, Set<User> requests, Set<Vehicle> vehicles, Set<Vehicle> hired);
    void realize(Set<Visit> visits);
    void realizeVisit(Visit visit);
}
