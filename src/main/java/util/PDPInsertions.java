package util;

import com.google.common.collect.Lists;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import model.node.NodeMiddle;

import java.lang.reflect.Array;
import java.util.*;

public class PDPInsertions implements Iterator<Node[]> {
    public int iIndex = 0;
    public int jIndex = 1;
    public int jIndex2 = 0;
    public int iIndex2 = 0;
    public User user;
    public ArrayList<Node> sequence;
    public Node[] sequence2;

    public PDPInsertions(User request, List<Node> sequence) {
        this.user = request;
        this.sequence = new ArrayList<>(sequence);
    }

    public PDPInsertions(User request, Vehicle vehicle) {
        this.sequence = vehicle.isServicing() ? new ArrayList<>(vehicle.getVisit().getSequenceVisits()) : new ArrayList<>();

        // Remove middle node from insertion sequence
        if (!this.sequence.isEmpty() && this.sequence.get(0) instanceof NodeMiddle) {
            this.sequence.remove(0);
        }
        this.user = request;
    }

    @Override
    public boolean hasNext() {
        return !(iIndex > this.sequence.size() && jIndex > this.sequence.size());
    }

    public void swap(Node[] sequence, int i, int j){
    Node aux = sequence[i];
    sequence[i] = sequence[j];
    sequence[j] = aux;
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
