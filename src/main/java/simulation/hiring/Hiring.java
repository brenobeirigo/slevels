package simulation.hiring;

import model.demand.User;
import model.Vehicle;

import java.util.Collection;
import java.util.Set;

public interface Hiring {
    public Set<Vehicle> hire(Collection<User> users, int currentTime);
}
