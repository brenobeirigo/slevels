package util.pdcombinatorics;

import model.User;
import model.Vehicle;
import model.node.Node;

import java.util.*;

public class PDGeneratorInsertion implements PDGenerator {

    public static Comparator<User> userComparatorDescendingEarliestTime = new Comparator<User>() {
        @Override
        public int compare(User user1, User user2) {
            return user2.getNodePk().getEarliest().compareTo(user1.getNodePk().getEarliest());
        }
    };

    LinkedList<PDGeneratorSingleInsertion> pdPartialInsertionList;
    private LinkedList<User> sortedRequests;

    public PDGeneratorInsertion() {
        this.pdPartialInsertionList = new LinkedList<>();
    }

    public PDGeneratorInsertion(Set<User> requests, Vehicle vehicle) {
        this();
        this.start(requests, vehicle);
    }

    public LinkedList<User> getRequestListSortedByReversedEarliestTime(Set<User> requests) {
        LinkedList<User> requestList = new LinkedList<>(requests);
        Collections.sort(requestList, userComparatorDescendingEarliestTime);
        return requestList;
    }

    // 3 2 1
    // (3,[]) = [3,3]
    // (2, [3,3]) = [2,2,3,3], [2,3,2,3], [3,2,3,2], [3,3,2,2]
    // (1, [2,2,3,3]), (1, [2,3,2,3]), (1, [3,2,3,2]), (1, [3,3,2,2])
    // [1,1,2,2,3,3]
    // [1,2,1,2,3,3]
    // [1,2,2,1,3,3]
    // [1,2,2,3,1,3]
    // [1,2,2,3,3,1]
    // [2,1,1,2,3,3]
    // [2,1,2,1,3,3]
    // [2,1,2,3,1,3]
    // [2,2,1,1,3,3]
    // [2,2,1,3,1,3]
    // [2,2,1,3,3,1]
    // [2,2,3,1,1,3]
    // [2,2,3,1,3,1]
    // [2,2,3,3,1,1]

    @Override
    public boolean hasNext() {
        return !pdPartialInsertionList.isEmpty();
    }

    @Override
    public Node[] next() {
        Node[] PDSequence = null;

        pushInsertionGenerators();

        if (pdPartialInsertionList.getLast().hasNext()) {

            PDSequence = pdPartialInsertionList.getLast().next();

            // If last sequence has been exhausted, prepare sequences for next iteration
            if (!pdPartialInsertionList.getLast().hasNext()) {
                popExhaustedInsertionGenerators();
            }
        }
        return PDSequence;
    }

    private void popExhaustedInsertionGenerators() {
        while (!pdPartialInsertionList.isEmpty() && !pdPartialInsertionList.getLast().hasNext()) {
            PDGeneratorSingleInsertion exhaustedPUDOGenerator = pdPartialInsertionList.removeLast();
            System.out.println(pdPartialInsertionList.getLast().user +" - "+pdPartialInsertionList.getLast().sequence);
            sortedRequests.add(exhaustedPUDOGenerator.user);
        }
    }

    /*
    Build partial list of generators. Example:
    u1 - e=1
    u2 - e=2
    u3 - e=3
    # Sorted requests
    3(2B)-3
    2(3B)-2
    1(1B)-1
    2(3B) - [       PK3,        DP3]
    1(1B) - [       PK2,        DP2,        PK3,        DP3]
    ... All permutations with 1(1B)
    2(3B) - [       PK3,        DP3] --------------------> dropped [       PK2,        DP2,        PK3,        DP3]
    1(1B) - [       PK2,        PK3,        DP2,        DP3]
    ... All permutations with 1(1B)
    2(3B) - [       PK3,        DP3] --------------------> dropped [       PK2,        PK3,        DP2,        DP3]
    1(1B) - [       PK2,        PK3,        DP3,        DP2]
    ... All permutations with 1(1B)
    2(3B) - [       PK3,        DP3]
    1(1B) - [       PK3,        PK2,        DP2,        DP3]
    ... All permutations with 1(1B)
    2(3B) - [       PK3,        DP3]
    1(1B) - [       PK3,        PK2,        DP3,        DP2]
    ... All permutations with 1(1B)
    2(3B) - [       PK3,        DP3]
    1(1B) - [       PK3,        DP3,        PK2,        DP2]
    ... All permutations with 1(1B)
    2(3B) - [       PK3,        DP3]
    3(2B) - []

     */
    private void pushInsertionGenerators() {

        while (!sortedRequests.isEmpty()) {
            ArrayList<Node> sequence = new ArrayList<>(List.of(pdPartialInsertionList.getLast().next()));
            PDGeneratorSingleInsertion PDSingleInsertion = new PDGeneratorSingleInsertion(sortedRequests.removeFirst(), sequence);
            System.out.println(PDSingleInsertion.user +" - "+PDSingleInsertion.sequence);
            pdPartialInsertionList.add(PDSingleInsertion);
        }
    }

    @Override
    public void start(User request, Vehicle vehicle) {

    }

    @Override
    public void start(User request, Node[] sequence) {

    }

    @Override
    public void start(Set<User> requests, Vehicle vehicle) {
        if (!requests.isEmpty()) {
            this.sortedRequests = getRequestListSortedByReversedEarliestTime(requests);
            User request = sortedRequests.removeFirst();
            PDGeneratorSingleInsertion pd = new PDGeneratorSingleInsertion(request, vehicle);
            pdPartialInsertionList.add(pd);
        }
    }
}
