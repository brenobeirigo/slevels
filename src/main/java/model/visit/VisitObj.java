package model.visit;

import model.demand.User;
import model.Vehicle;
import model.learn.StateAction;
import model.node.Node;
import simulation.Environment;

import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public interface VisitObj {

    VisitObj getVisit();

    void setVehicleState(StateAction stateAction);

    int getVisitSequenceSize();

    int compareTo(VisitObj v);

    //boolean isValid();

    void genUserPickupDelayMap(Environment env);

    void discountDelay(int delayServicedUser);

    Map<User, Integer> getUserPickupDelayMap(Environment environment);

    String getUserInfo();

    int getRequestsTotalLoad();

    int getPassengersTotalLoad();

    double getAvgLoadPerVisitLeg();

    int getArrival();

    Integer getDelay();

    Integer getIdle();

    Set<User> getRequests();

    Set<User> getPassengers();

    Set<User> getUsers();

    LinkedList<Node> getSequenceVisits();

    Vehicle getVehicle();

    void setVehicle(Vehicle vehicle);

    Integer getDeparture();

    Node getLastVisitedNode();

    Node getTargetNode();

    boolean isSetup();

    Integer getDelayBonus();

    void setVF(double vf);

    double getVF();

    void setDeparture(int arrivalNextNode);

    String getType();
}
