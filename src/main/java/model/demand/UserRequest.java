package model.demand;

public class UserRequest implements Comparable<UserRequest>{

    private NodeSource nodePickup;
    private NodeDestination nodeDestination;
    int requestId;

    public UserRequest(int requestId, NodeSource nodePickup, NodeDestination nodeDestination) {
        this.requestId = requestId;
        this.nodePickup = nodePickup;
        this.nodeDestination = nodeDestination;
        this.nodePickup.setUserRequest(this);
        this.nodeDestination.setUserRequest(this);
    }

    @Override
    public int compareTo(UserRequest request) {
        return this.nodePickup.getEarliestDateTime().compareTo(request.nodePickup.getEarliestDepartureDateTime());
    }
}
