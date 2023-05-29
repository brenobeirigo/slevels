package helper;

import dao.Dao;
import model.Vehicle;

import java.util.*;

public class MethodHelper {


    /**
     * Create a vehicle of capacity "capacity" positioned at a random node.
     *
     * @param capacity Capacity of vehicle
     * @return Vehicle at random position
     */
    public static Vehicle createVehicleAtRandomPosition(int capacity, int currentTime, int numberOfContractedRounds) {
        short randomOrigin = (short) (Dao.getInstance().rand.nextDouble() * Dao.getInstance().getDistMatrix().length);
        return new Vehicle(capacity, randomOrigin, currentTime, true, numberOfContractedRounds);
    }


    public static List<Vehicle> createListVehicles(Map<Integer, Integer> vPerCapacity) {

        //Logging.logger.info("Creating vehicles...");
        List<Vehicle> listVehicle = new ArrayList<>();

        for (Map.Entry<Integer, Integer> e : vPerCapacity.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                short randomOrigin = (short) (Math.random() * Dao.getInstance().getDistMatrix().length);
                listVehicle.add(new Vehicle(e.getKey(), randomOrigin, 2.3, 3.4));
            }
        }

        return listVehicle;
    }

    /**
     * Return the index where to insert item x in list a, assuming a is sorted.
     * The return value i is such that all e in a[:i] have e <= x, and all e in
     * a[i:] have e > x.  So if x already appears in the list, a.insert(x) will
     * insert just after the rightmost x already there.
     * <p>
     * Optional args lo (default 0) and hi (default len(a)) bound the
     * slice of a to be searched.
     *
     * @param a
     * @param x
     * @return
     */
    public static int bisect_right(List<Integer> a, int x) {

        int lo = 0;
        int hi = a.size();
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (x < a.get(mid))
                hi = mid;
            else
                lo = mid + 1;
        }

        return lo;
    }

//
//    public static void main(String[] str) {
//        Logging.logger.info("Getting latest distances...");
//        for (int i = 0; i < 1000; i++) {
//            for (int j = 0; j < 1000; j++) {
//                int latest = Method.getEarliestDp(0, i, j, "A");
//                Logging.logger.info("Earliest:" + 0 + " - Latest time: " + latest);
//            }
//        }
//
//        for (int i = 0; i < Node.MAX_NUMBER_NODES; i++) {
//            Logging.logger.info(User.status[i][0] + " - " + User.status[i][1] + " - " + User.status[i][2]);
//
//        }
//    }
}