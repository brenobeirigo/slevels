package helper;

import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import simulation.Method;

import java.util.ArrayList;
import java.util.List;

public class MethodHelper {


    public static Visit getValidVisitOfSize(int n, boolean findBest, int maxTrips) {

        Vehicle v1 = new Vehicle(n, 0, 12, 23);

        Visit v = null;

        while (v == null) {

            Dao dao = Dao.getInstance();
            List<User> listUser = dao.getListTrips(100, n);
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

        //TODO: initial distribution (vsize = [1,2,3,4,...,n]
        int nPerSize = n / size;
        int vSize = 0;
        //TODO: Scatter origins (hotpoints)
        for (int i = 0; i < n; i++) {
            //Increment vehicle size
            if (i % nPerSize == 0 && vSize < size) {
                vSize++;
            }
            int randomOrigin = (int) (Math.random() * Dao.getInstance().getDistMatrix().length);
            listVehicle.add(new Vehicle(vSize, randomOrigin, 2.3, 3.4));
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


    public static void main(String[] str) {
        System.out.println("Getting latest distances...");
        for (int i = 0; i < 1000; i++) {
            for (int j = 0; j < 1000; j++) {
                int latest = Method.getEarliestDp(0, i, j, 'A');
                System.out.println("Earliest:" + 0 + " - Latest time: " + latest);
            }
        }

        for (int i = 0; i < Node.MAX_NUMBER_NODES; i++) {
            System.out.println(User.status[i][0] + " - " + User.status[i][1] + " - " + User.status[i][2]);

        }
    }
}