import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import simulation.Method;

import java.util.HashSet;
import java.util.Set;

public class TestInsertionVisit {

    public static void main(String[] str) {


        //Load users 5 minutes apart
        User u1 = new User("2011-02-12 00:00:00", 1, 0, 1, 3, 2, 3, 2);
        User u2 = new User("2011-02-12 00:05:00", 1, 2, 3, 3, 2, 3, 2);
        User u3 = new User("2011-02-12 00:10:00", 1, 3, 4, 3, 2, 3, 2);
        User u4 = new User("2011-02-12 00:15:00", 1, 4, 5, 3, 2, 3, 2);


        System.out.println("User 1:" + u1.getInfo() + " - Nodes:" + u1.getNodePk().getInfo() + " - " + u1.getNodeDp().getInfo());
        System.out.println("User 2:" + u2.getInfo() + " - Nodes:" + u2.getNodePk().getInfo() + " - " + u2.getNodeDp().getInfo());
        System.out.println("User 3:" + u3.getInfo() + " - Nodes:" + u3.getNodePk().getInfo() + " - " + u3.getNodeDp().getInfo());
        System.out.println("User 4:" + u4.getInfo() + " - Nodes:" + u4.getNodePk().getInfo() + " - " + u4.getNodeDp().getInfo());

        // Vehicle
        Vehicle v1 = new Vehicle(10, u1.getNodePk().getNetworkId());

        int dist1 = Dao.getInstance().getDistSec(v1.getCurrentNode(), u1.getNodePk());

        System.out.println("Distance v1 & u1:" + dist1);

        System.out.println("Vehicle:" + v1.get_info() + " - Node:" + v1.getOrigin());


        Set<User> listUsers1 = new HashSet<>();
        listUsers1.add(u1);
        listUsers1.add(u2);

        //Set<User> listUsers2 = new HashSet<>();
        //listUsers2.add(u3);

        // Visit 1
        Visit visit1 = Method.getVisit(listUsers1, v1, false, 1000);
        System.out.println(visit1);

        v1.setVisit(visit1);
        visit1.setVehicle(v1);
        visit1.setSetUsers(listUsers1);
        Set<User> listUsers = new HashSet<>();

        System.out.println("Vehicle:" + v1);
        listUsers.add(u3);
        listUsers.add(u4);

        Visit v = Method.getBestInsertion(listUsers, v1);


        System.out.println("BEST:" + v);

    }
}
