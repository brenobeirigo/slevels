package util.pdcombinatorics;

import model.demand.User;
import model.Vehicle;
import model.node.Node;
import model.node.NodeWaypoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class PDGeneratorSingleInsertion implements Iterator<Node[]> {
    public int iIndex;
    public int jIndex;
    public User user;
    public ArrayList<Node> sequence;

    public PDGeneratorSingleInsertion() {
        this.iIndex = 0;
        this.jIndex = 1;
    }

    public PDGeneratorSingleInsertion(User request, List<Node> sequence) {
        this();
        this.start(request, sequence);
    }

    public void start(User request, List<Node> sequence) {
        this.user = request;
        this.sequence = new ArrayList<>(sequence);
    }

    public PDGeneratorSingleInsertion(User request, Vehicle vehicle) {
        this();
        this.start(request, vehicle);
    }

    public void start(User request, Vehicle vehicle) {
        this.sequence = vehicle.isServicing() ? new ArrayList<>(vehicle.getVisit().getSequenceVisits()) : new ArrayList<>();

        // Remove middle node from insertion sequence
        if (!this.sequence.isEmpty() && this.sequence.get(0) instanceof NodeWaypoint) {
            this.sequence.remove(0);
        }
        this.user = request;
    }

    @Override
    public boolean hasNext() {
        return !(iIndex > this.sequence.size() && jIndex > this.sequence.size());
    }

    @Override
    public Node[] next() {

        if (hasNext()) {
            List<Node> insertedP = new ArrayList<>(sequence);
            insertedP.add(iIndex, this.user.getNodePk());
            insertedP.add(jIndex, this.user.getNodeDp());

            jIndex++;
            if (jIndex >= insertedP.size()) {
                iIndex++;
                jIndex = iIndex + 1;
            }

            return insertedP.toArray(new Node[insertedP.size()]);
        }
        return null;
    }

    @Override
    public String toString() {
        return "PDPInsertions{" +
                "user=" + user +
                ", sequence=" + Arrays.asList(sequence) +
                '}';
    }
}
