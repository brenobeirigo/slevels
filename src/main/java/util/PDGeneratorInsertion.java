package util;

import model.User;
import model.Vehicle;
import model.node.Node;

import java.util.*;

public class PDGeneratorInsertion implements PDGenerator {

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
        Collections.sort(requestList, new Comparator<User>() {
            @Override
            public int compare(User o1, User o2) {
                return o2.getNodePk().getEarliest().compareTo(o1.getNodePk().getEarliest());
            }
        });
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

        buildPartialInsertionList();

        if (pdPartialInsertionList.getLast().hasNext()) {

            PDSequence = pdPartialInsertionList.getLast().next();

            // If last sequence has been exhausted, prepare sequences for next iteration
            if (!pdPartialInsertionList.getLast().hasNext()) {
                popPartialInsertionList();
            }
        }
        return PDSequence;
    }

    private void popPartialInsertionList() {
        while (!pdPartialInsertionList.isEmpty() && !pdPartialInsertionList.getLast().hasNext()) {
            PDGeneratorSingleInsertion exhaustedPUDOGenerator = pdPartialInsertionList.removeLast();
            sortedRequests.add(exhaustedPUDOGenerator.user);
        }
    }

    private void buildPartialInsertionList() {

        while (!sortedRequests.isEmpty()) {
            ArrayList<Node> sequence = new ArrayList<>(List.of(pdPartialInsertionList.getLast().next()));
            PDGeneratorSingleInsertion PDSingleInsertion = new PDGeneratorSingleInsertion(sortedRequests.removeLast(), sequence);
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
