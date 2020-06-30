package simulation;

import model.User;
import model.Vehicle;

import java.util.List;

public interface RideMatchingStrategy {
    ResultAssignment match(int currentTime, List<User> requests, List<Vehicle> vehicles, Matching configMatching);
}
