package model.node;

import model.demand.NodeSimple;
import model.demand.UserRequest;

import java.time.LocalDateTime;

public class NodePUDO extends NodeSimple {

    private LocalDateTime arrivalDateTime;
    protected int requestId;
    protected LocalDateTime earliestDateTime;
    protected LocalDateTime latestDateTime;
    private UserRequest userRequest;
    protected int load;

    public NodePUDO(int requestId, int networkId, LocalDateTime earliest, LocalDateTime latest, int load) {
        super(networkId);
        this.requestId = requestId;
        this.earliestDateTime = earliest;
        this.latestDateTime = latest;
        this.load = load;
        this.arrivalDateTime = null;

    }

    public void setUserRequest(UserRequest userRequest) {
        this.userRequest = userRequest;
    }

    public LocalDateTime getEarliestDateTime() {
        return earliestDateTime;
    }

    public LocalDateTime getLatestDateTime() {
        return latestDateTime;
    }
}
