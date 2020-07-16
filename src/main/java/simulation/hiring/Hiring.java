package simulation.hiring;

import model.User;
import model.Vehicle;

import java.util.List;
import java.util.Set;

public interface Hiring {
    public Set<Vehicle> hire(List<User> users, int currentTime);
}
