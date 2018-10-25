import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import simulation.Method;

import java.util.ArrayList;
import java.util.List;

public class TestDao {

    public static void main(String[] arg) {
        Dao dao = Dao.getInstance();
        List<User> users = dao.getListTrips(1, 10);
        for (User l : users) {
            System.out.println(l);
        }

        System.out.println("Get second batch...");
        List<User> trips2 = dao.getListTrips(1, 10);
        for (User l : trips2) {
            System.out.println(l);
        }

        System.out.println("Get second batch...");
        List<User> trips3 = dao.getListTrips(100, 10);

        int size = 3;
        int cont = 0;
        List<Integer> a = new ArrayList<>();
        for (User l : trips3) {
            cont++;
            a.add(l.getId());
            a.add(l.getId());
            if (cont % size == 0) {
                //System.out.println(a);
                Vehicle v = new Vehicle(10, 0, 2.34, 4.3);
                Visit valid = Method.getValidVisit(a, v);
                System.out.println(valid);
                a = new ArrayList<>();
            }
        }
    }
}
