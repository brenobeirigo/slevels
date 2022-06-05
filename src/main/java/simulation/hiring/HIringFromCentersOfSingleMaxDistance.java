package simulation.hiring;

import config.Config;
import dao.Dao;
import model.User;
import model.Vehicle;

import java.util.Collection;
import java.util.Set;

public class HIringFromCentersOfSingleMaxDistance implements Hiring{

    private int maxDistance;

    public HIringFromCentersOfSingleMaxDistance(int maxDistance) {
    this.maxDistance = maxDistance;
    }

    @Override
    public Set<Vehicle> hire(Collection<User> users, int currentTime) {
        return null;
    }

    public static Vehicle createVehicleAtClosestRegionalCenter(User u, int currentTime, int contractDuration) {

        int closestRegionCenterId = Dao.getInstance().getClosestRegion(u.getNodePk().getNetworkId(), u.getPerformanceClass());

        // When vehicle have to be released
        int contractDeadline = currentTime;
        int distOriginPkUser = Dao.getInstance().getDistSec(closestRegionCenterId, u.getNodePk().getNetworkId());

        int distPkDp = u.getDistFromTo();

        // Deadline is the delivery time of user who caused hiring
        if (contractDuration == Config.DURATION_SINGLE_RIDE) {
            contractDeadline += distOriginPkUser + distPkDp;
            //Logging.logger.info("  (single) Contract deadline: " + contractDeadline);

        } else {
            contractDeadline += contractDuration;
            //Logging.logger.info("          Contract deadline: " + contractDeadline);
        }

        Vehicle hiredVehicle = new Vehicle(u.getNumPassengers(), closestRegionCenterId, currentTime, true, contractDeadline);
        hiredVehicle.addUserHiredMustPickup(u);
        return hiredVehicle;
    }
}
