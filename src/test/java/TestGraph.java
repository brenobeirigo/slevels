import dao.Dao;
import model.User;
import model.Vehicle;
import model.graph.Graph;
import model.graph.VertexVehicle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestGraph {


    public static void main(String[] str) {

        Dao dao = Dao.getInstance();
        Graph g = new Graph();
        Vehicle v1 = new Vehicle(4, 0, 2.3, 4.5);
        List<User> trips2 = dao.getListTrips(1);
        // Try adding two vertices
        VertexVehicle vv1 = g.addVertexVehicle(v1);
        VertexVehicle vv2 = g.addVertexVehicle(v1);

        // Adding user 1
        User u1 = trips2.get(0);

        // Adding user 2
        User u2 = trips2.get(1);

        System.out.println("TRYING TO ADD G2");

        Set<User> testSet = new HashSet<>();

        Set<Set<User>> setSet = new HashSet<>();

        testSet.add(u1);
        testSet.add(u2);

        setSet.add(testSet);

        Set<User> testSet2 = new HashSet<>();
        testSet2.add(u1);
        testSet2.add(u2);

        setSet.add(testSet);
        setSet.add(testSet2);

        System.out.println("Set of sets:" + setSet);
        System.out.println("Hash code test set:" + testSet + " -- " + testSet.hashCode());
        Graph g2 = new Graph("TEST");

        // Creating trip with u1 and u2
        Set<User> trip212 = new HashSet<>();
        trip212.add(u1);
        trip212.add(u2);

        // Try inserting trip 12
        g2.addEdge(v1, trip212);

        // Creating trip with u1
        Set<User> trip21 = new HashSet<>();
        trip21.add(u1);

        // Try inserting trip 1
        g2.addEdge(v1, trip21);

        // Creating trip with u2
        Set<User> trip22 = new HashSet<>();
        trip22.add(u2);

        // Try inserting trip 2
        g2.addEdge(v1, trip22);

        System.out.println("G2:");
        System.out.println(g2);

        // Try inserting trip 2 AGAIN
        g2.addEdge(v1, trip22);
        System.out.println("G2 Again:");
        System.out.println(g2);
    }


}
