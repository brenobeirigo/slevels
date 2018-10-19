import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import simulation.Method;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestHelperMethods {

    public static Visit getValidVisitOfSize(int n, boolean findBest, int maxTrips) {

        Vehicle v1 = new Vehicle(n, 0, 12, 23);

        Visit v = null;

        while (v == null) {

            Dao dao = Dao.getInstance();
            Set<User> listUser = new HashSet<>();

            listUser.addAll(dao.getListTrips(100, n));

            v = Method.getVisit(listUser, v1, findBest, maxTrips);
        }
        return v;
    }

    public static void testValid(int size_seq, List<User> trips) {
        //#users
        int cont = 0;

        //Model.Vehicle to test sequence
        Vehicle v = new Vehicle(size_seq, 0, 2.34, 4.3);

        //Generating sequence
        List<Integer> sequence = new ArrayList<>();

        // Create sequences from trips
        for (User l : trips) {
            cont++;
            sequence.add(l.getId());
            sequence.add(l.getId());


            if (cont % size_seq == 0) {

                Visit valid = Method.getValidVisit(sequence, v);
                System.out.println("SEQUENCE:" + sequence);
                System.out.println("   NODES:" + valid);
                sequence = new ArrayList<>();
                cont = 0;
            }
        }
    }

    public static List<Vehicle> createListVehicles(int n, int size) {
        List<Vehicle> listVehicle = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            listVehicle.add(new Vehicle(size, 0, 2.3, 3.4));
        }
        return listVehicle;
    }

}
