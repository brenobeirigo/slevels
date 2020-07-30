package simulation.hiring;

import config.Config;
import dao.Dao;
import model.User;
import model.Vehicle;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HiringFromCenters implements Hiring {
    private int contractDuration;

    /**
     * Create a vehicle of capacity "capacity" positioned at a random node.
     *
     * @param u Capacity of vehicle
     * @return Vehicle at random position
     */
    public static Vehicle createVehicleAtClosestRegionalCenter(User u, int currentTime, int contractDuration) {

        int closestRegionCenterId = Dao.getInstance().getClosestRegion(u.getNodePk().getNetworkId(), u.getPerformanceClass());

        // When vehicle have to be released
        int contractDeadline = currentTime;
        int distOriginPkUser = Dao.getInstance().getDistSec(closestRegionCenterId, u.getNodePk().getNetworkId());

        int distPkDp = u.getDistFromTo();
        //System.out.println("Distance origin pickup user: " + distOriginPkUser);
        //System.out.println("   Distance pickup delivery: " + distPkDp);
        //System.out.println("               Current time: " + contractDeadline);
        //System.out.println("              User deadline: (pk:" + u.getNodePk().getLatest() + " / dp:"+ u.getNodeDp().getLatest()+ ")");
        //System.out.println("   Distance pickup delivery: " + distPkDp);

        // Deadline is the delivery time of user who caused hiring
        if (contractDuration == Config.DURATION_SINGLE_RIDE) {
            contractDeadline += distOriginPkUser + distPkDp;
            //System.out.println("  (single) Contract deadline: " + contractDeadline);

        } else {
            contractDeadline += contractDuration;
            //System.out.println("          Contract deadline: " + contractDeadline);
        }

        return new Vehicle(u.getNumPassengers(), closestRegionCenterId, currentTime, true, contractDeadline);
    }

    @Override
    public Set<Vehicle> hire(Collection<User> users, int currentTime) {
        Set<Vehicle> closest =  users.stream().map(user->createVehicleAtClosestRegionalCenter(user, currentTime, this.contractDuration)).collect(Collectors.toSet());
        return closest;
    }

    public HiringFromCenters(int contractDuration) {
        this.contractDuration = contractDuration;
    }
}
