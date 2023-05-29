package simulation.hiring;

import model.demand.User;
import model.Vehicle;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class HiringDisabled  implements Hiring{
    @Override
    public Set<Vehicle> hire(Collection<User> users, int currentTime) {
        return Collections.emptySet();
    }
}
