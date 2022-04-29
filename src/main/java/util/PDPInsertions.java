package util;

import model.User;
import model.Vehicle;
import model.node.Node;
import model.node.NodeMiddle;

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
        this.sequence2 = new Node[sequence.size()+2];
        this.sequence2[0] = request.getNodePk();
        this.sequence2[1] = request.getNodeDp();
        for (int i = 0; i <sequence.size(); i++) {
           this.sequence2[i+2] = sequence.get(i);
        }
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


    public Node[] next2() {

        if (hasNext()) {

            jIndex2++;
            if (jIndex2 == 1){
                jIndex2++;
                return sequence2;
            }


            if (jIndex2 > sequence.size()) {
                iIndex2++;
                // 1 1' 2 2'
                // 1 2 1' 2'
                // 1 2 2' 1'
                // 1 2 2' 1'
                // 1 1 2' 1'

                // 1 1' 2 2'
                // 1 1' 2 2' 3 3'
                // 1 2 1'
                jIndex2 = iIndex2+1;
            }
            swap(sequence2, jIndex-1, jIndex);

            return sequence2;
        }
        return null;
    }
}
