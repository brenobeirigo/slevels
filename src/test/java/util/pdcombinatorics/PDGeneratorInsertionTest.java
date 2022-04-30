package util.pdcombinatorics;

import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PDGeneratorInsertionTest {

    @org.junit.jupiter.api.Test
    public void testSorted(){
        //Load requests 5 minutes apart
        User u1 = new User(1,300, 600, 300, 1, 0, 1, 3, 2, 3, 2);
        User u2 = new User(2,300, 600, 300, 3, 0, 1, 3, 2, 3, 2);
        User u3 = new User(3,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
        User u4 = new User(4,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);

        Vehicle v1 = new Vehicle(4, 0, 0, false, 4000);


        System.out.println("# Requests");
        HashSet<User> requests = new HashSet<>(List.of(new User[]{u1, u2, u3}));
        requests.forEach(user -> System.out.printf("%s-%s\n", user, user.getReqTime()));
        PDGeneratorInsertion pd3 = new PDGeneratorInsertion(requests, v1);

        System.out.println("# Sorted requests");
        List<User> sortedRequests = pd3.getRequestListSortedByReversedEarliestTime(requests);
        sortedRequests.forEach(user -> System.out.printf("%s-%s\n", user, user.getReqTime()));

        while(pd3.hasNext()){
            List<Node> sequence = Arrays.asList(pd3.next());
//            System.out.println(sequence);
        }
    }

    @org.junit.jupiter.api.Test
    public void testSortedFullVehicle(){

        //Load requests 5 minutes apart
        User u1 = new User(4,300, 600, 300, 1, 0, 1, 3, 2, 3, 2);
        User u2 = new User(3,300, 600, 300, 3, 0, 1, 3, 2, 3, 2);
        User u3 = new User(2,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
        User u4 = new User(1,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
        User u5 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
        User u6 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);

        Vehicle fullVehicle = new Vehicle(4, 0, 0, false, 4000);
        //public Visit(Node[] sequencePD, int delay, int idleness, Vehicle vehicle, Set<User> requests) {
        Node[] sequence = new Node[]{u5.getNodeDp(), u6.getNodeDp()};
        HashSet<User> requests = new HashSet<>(List.of(new User[]{u1, u2}));
        fullVehicle.setVisit(new Visit(sequence, 2, 0, fullVehicle, requests));

        PDGeneratorInsertion pd3 = new PDGeneratorInsertion(requests, fullVehicle);

        while(pd3.hasNext()){
            List<Node> nextElement = Arrays.asList(pd3.next());
            //System.out.println(nextElement);
        }
    }
    @org.junit.jupiter.api.Test
    public void testEmptyVehicle() {


        //Load requests 5 minutes apart
        User u1 = new User(1,300, 600, 300, 1, 0, 1, 3, 2, 3, 2);
        User u2 = new User(2,300, 600, 300, 3, 0, 1, 3, 2, 3, 2);
        User u3 = new User(3,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);

        Vehicle v1 = new Vehicle(4, 0, 0, false, 4000);

        PDGeneratorInsertion pd3 = new PDGeneratorInsertion(new HashSet<>(List.of(new User[]{u1, u2, u3})), v1);

        System.out.println("# Requests");
        HashSet<User> requests = new HashSet<>(List.of(new User[]{u1, u2, u3}));
        requests.forEach(user -> System.out.printf("%s-%s\n", user, user.getReqTime()));

        System.out.println("# Sorted requests");
        List<User> sortedRequests = pd3.getRequestListSortedByReversedEarliestTime(requests);
        sortedRequests.forEach(user -> System.out.printf("%s-%s\n", user, user.getReqTime()));


        while(pd3.hasNext()){
            System.out.println(Arrays.asList(pd3.next()));
        }

        PDGeneratorInsertion pd2 = new PDGeneratorInsertion(new HashSet<>(List.of(new User[]{u1, u2})), v1);
        Set<Node[]> pd2Set = new HashSet<>();
        List<Node[]> pd2List = new ArrayList<>();
        while(pd2.hasNext()){
            Node[] sequence = pd2.next();
            pd2List.add(sequence);
            assertEquals(pd2List.size(),new HashSet<>(pd2List).size());
            System.out.println(Arrays.asList(sequence));
        }

        PDGeneratorInsertion pd1 = new PDGeneratorInsertion(new HashSet<>(List.of(new User[]{u1})), v1);
        while(pd1.hasNext()){
            System.out.println(Arrays.asList(pd1.next()));
        }

        PDGeneratorInsertion pd0 = new PDGeneratorInsertion(new HashSet<>(List.of(new User[]{})), v1);
        while(pd0.hasNext()){
            System.out.println(Arrays.asList(pd0.next()));
        }



    }}