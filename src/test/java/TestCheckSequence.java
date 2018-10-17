import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;

import java.util.List;
import java.util.Scanner;

public class TestCheckSequence {

    public static void main(String[] arg) {

        Scanner keyboard = new Scanner(System.in);
        Dao dao = Dao.getInstance();
        System.out.println("Get second batch...");

        System.out.println("10 seconds...");
        List<User> trips = dao.getListTrips(10);

        // Test vehicles of size 4
        TestHelperMethods.testValid(4, trips);

        // Consume 1 hour
        dao.getListTrips(3600);

        System.out.println("10 seconds...");
        List<User> trips2 = dao.getListTrips(100);

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

            int currentTime;
            do {
                System.out.println("Enter an integer to cut sequence arrival:");
                System.out.println(visitBis);
                currentTime = keyboard.nextInt();
                System.out.println("Cutting sequence...");
                int start = Model.Visit.bisect_right(visitBis.getSequenceArrivals(), currentTime);
                System.out.println("Elements to remove: " + start);
                //Set<Model.User> usersFinished = gen22.getServicedUsersUntil(5000,0);
                //System.out.println("Users: "+usersFinished);
                //System.out.println(usersFinished);
            } while(currentTime>=0);
            */


        System.out.println("-----------------------------------------------------------------");
        System.out.println("######## TESTING VISIT: REMOVE BEFORE:");
        Visit visitUpdate = TestHelperMethods.getValidVisitOfSize(4, false, 1000);


        int currentTimeVisit;
        do {
            System.out.println("Enter an integer to cut sequence arrival:");
            System.out.println(visitUpdate);

            currentTimeVisit = keyboard.nextInt();
            //visitUpdate.getServicedUsersUntil(currentTimeVisit,0);
            System.out.println(visitUpdate);
        } while (currentTimeVisit >= 0);
    }
}
