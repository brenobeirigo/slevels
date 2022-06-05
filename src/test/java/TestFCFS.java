import dao.Dao;
import dao.Logging;
import model.User;
import model.Vehicle;

import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class TestFCFS {


    public static void update_current_time(List<Vehicle> vehicles, int current_time) {

        for (Vehicle v : vehicles) {

            if (v.getVisit() != null) {
                v.getLastVisitedNode().setArrival(Math.max(current_time, v.getLastVisitedNode().getArrival()));
            }
            if (v.getVisit() != null && !v.getVisit().getSequenceVisits().isEmpty()) {
                // Current node stores the last arrival time
                // This time is updated
                v.getLastVisitedNode().setArrival(Math.max(current_time, v.getLastVisitedNode().getArrival()));
            }
        }
    }

    public static void main(String[] arg) {

        Scanner keyboard = new Scanner(System.in);

        Dao dao = Dao.getInstance();
        Vehicle v1 = new Vehicle(4, 0, 12, 23);


        Logging.logger.info("10 seconds...");
        List<Vehicle> listV = TestHelperMethods.createListVehicles(5, 4);

        Logging.logger.info("VEHICLE LIST:");
        for (Vehicle v : listV) {
            Logging.logger.info(v.toString());
        }

//        Set<User> listU = dao.getListTrips(1, 20);
//
//        Logging.logger.info("USER LIST:");
//        for (User u : listU) {
//            Logging.logger.info(u);
//        }

        for (int i = 0; i < 5 * 3600; i = i + 30) {
            Logging.logger.info("TIME:" + i);
            //Set<User> ok = Method.getSolutionFCFS(new HashSet<>(listU), listV, false, true, i, 10, false);

            Logging.logger.info("USER OK:");
            for (Vehicle v : listV) {
                if (v.getVisit() != null) {
                    Logging.logger.info(v.getInfo());
                }
            }

            Logging.logger.info("Updating...");
            update_current_time(listV, i);

        }


        //Logging.logger.info("USER OK:");
        //for (Model.User u:ok) {
        //    Logging.logger.info(ok);
        //}


    }
}
