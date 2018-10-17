import dao.Dao;
import model.User;
import model.Vehicle;
import simulation.Method;

import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class TestFCFS {


    public static void update_current_time(List<Vehicle> vehicles, int current_time) {

        for (Vehicle v : vehicles) {

            if (v.getVisit() != null) {
                v.getCurrentNode().setArrival(Math.max(current_time, v.getCurrentNode().getArrival()));
            }
            if (v.getVisit() != null && !v.getVisit().getSequenceVisits().isEmpty()) {
                // Current node stores the last arrival time
                // This time is updated
                v.getCurrentNode().setArrival(Math.max(current_time, v.getCurrentNode().getArrival()));
            }
        }
    }

    public static void main(String[] arg) {

        Scanner keyboard = new Scanner(System.in);

        Dao dao = Dao.getInstance();
        Vehicle v1 = new Vehicle(4, 0, 12, 23);


        System.out.println("10 seconds...");
        List<Vehicle> listV = TestHelperMethods.createListVehicles(5, 4);

        System.out.println("VEHICLE LIST:");
        for (Vehicle v : listV) {
            System.out.println(v);
        }

        List<User> listU = dao.getListTrips(1, 20);

        System.out.println("USER LIST:");
        for (User u : listU) {
            System.out.println(u);
        }

        for (int i = 0; i < 5 * 3600; i = i + 30) {
            System.out.println("TIME:" + i);
            Set<User> ok = Method.getSolutionFCFS(listU, listV, false, true, i, 10, false);

            System.out.println("USER OK:");
            for (Vehicle v : listV) {
                if (v.getVisit() != null) {
                    System.out.println(v.get_info());
                }
            }

            System.out.println("Updating...");
            update_current_time(listV, i);

        }


        //System.out.println("USER OK:");
        //for (Model.User u:ok) {
        //    System.out.println(ok);
        //}


    }
}
