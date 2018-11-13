import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import simulation.Method;

import java.util.List;
import java.util.Scanner;

public class TestCheckSequence {

    public static void main(String[] arg) {

        Scanner keyboard = new Scanner(System.in);
        Dao dao = Dao.getInstance();
        System.out.println("Get second batch...");

        System.out.println("10 seconds...");
        List<User> trips = dao.getListTrips(10, 10);

        // Test vehicles of size 4
        TestHelperMethods.testValid(4, trips);

        // Consume 1 hour
        dao.getListTrips(3600, 10);

        System.out.println("10 seconds...");
        List<User> trips2 = dao.getListTrips(100, 10);

        // Test vehicles of size 4
        System.out.println("Visits with idle time:");
        TestHelperMethods.testValid(2, trips2);

        System.out.println("Adding 2 users...");

        System.out.println("-----------------------------------------------------------------");
        System.out.println("######## SAMPLING 4 USERS:");

        Vehicle v1 = new Vehicle(4, 0, 12, 23);
        List<User> ListUser2 = dao.getListTrips(10, 2);

        for (User user : ListUser2) {
            System.out.println(user);
        }

        System.out.println("-----------------------------------------------------------------");
        System.out.println("######## GENERATING VISITS:");

        Visit gen21 = TestHelperMethods.getValidVisitOfSize(4, false, 10);
        System.out.println(String.format("Find best = True\nModel.Vehicle: %s\nMax trips: 10\nModel.Visit:%s", v1, gen21));

        Visit gen22 = TestHelperMethods.getValidVisitOfSize(4, true, 1000);
        System.out.println(String.format("Find best = True\nModel.Vehicle: %s\nMax trips: 10\nModel.Visit:%s", v1, gen22));

            /*
            System.out.println("-----------------------------------------------------------------");
            System.out.println("######## TESTING BISECT:");
            Model.Visit visitBis = getValidVisitOfSize(4, true, 1000);

            int departureVehicleCurrent;
            do {
                System.out.println("Enter an integer to cut sequence arrival:");
                System.out.println(visitBis);
                departureVehicleCurrent = keyboard.nextInt();
                System.out.println("Cutting sequence...");
                int getServicedUsersDynamicSizedFleet = Model.Visit.bisect_right(visitBis.getSequenceArrivals(), departureVehicleCurrent);
                System.out.println("Elements to remove: " + getServicedUsersDynamicSizedFleet);
                //Set<Model.User> usersFinished = gen22.getServicedUsersUntil(5000,0);
                //System.out.println("Users: "+usersFinished);
                //System.out.println(usersFinished);
            } while(departureVehicleCurrent>=0);
            */


        System.out.println("-----------------------------------------------------------------");
        System.out.println("######## TESTING VISIT: REMOVE BEFORE:");
        Visit visitUpdate = TestHelperMethods.getValidVisitOfSize(4, false, 1000);


        int currentTimeVisit;
        int t = 0, i = 0, j = 0;
        do {
            System.out.println("User:" + ListUser2.get(0));
            System.out.println("Enter i and j to swap sequence:");

            i = keyboard.nextInt();
            j = keyboard.nextInt();
            t = keyboard.nextInt();

            Visit insert = Method.getVisitByInsertPosition(ListUser2.get(0), visitUpdate.getSequenceVisits(), visitUpdate.getVehicle(), i, j, t);
            System.out.println(visitUpdate);
        } while (i >= 0);
    }
}
