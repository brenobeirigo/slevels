package util.pdcombinatorics;

import model.demand.User;
import model.Vehicle;
import model.node.Node;

import java.util.Iterator;
import java.util.Set;

public interface PDGenerator extends Iterator<Node[]> {
    public void start(User request, Vehicle vehicle);
    public void start(User request, Node[] sequence);
    public void start(Set<User> requests, Vehicle vehicle);
}
