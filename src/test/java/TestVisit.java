

import dao.Dao;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;

import java.util.LinkedList;
import java.util.List;

public class TestVisit {

    public static void main(String[] arg) {
        Dao dao = Dao.getInstance();
        List<User> users = dao.getListTrips(1);

        List<Node> seqVisit = new LinkedList<>();
        for (User t : users) {
            seqVisit.add(t.getNodePk());
            seqVisit.add(t.getNodeDp());
        }

        Vehicle v = new Vehicle(4, 1, 2.3, 9.4);
        Visit visit = new Visit(2, users, seqVisit, 0, v, 0);
        System.out.println(visit);
    }
}
