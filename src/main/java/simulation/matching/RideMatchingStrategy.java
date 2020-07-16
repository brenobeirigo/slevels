package simulation.matching;

import model.User;
import model.Vehicle;
import model.Visit;
import simulation.rebalancing.Rebalance;

import java.util.List;
import java.util.Set;

public interface RideMatchingStrategy {
    ResultAssignment match(int currentTime, List<User> requests, List<Vehicle> vehicles, Set<Vehicle> hired, Matching configMatching);
    void realize(Set<Visit> visits, Rebalance rebalanceUtil, int currentTime);
}
