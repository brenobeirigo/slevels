package simulation;

import model.demand.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class RequestManager {
    /* SETS OF VEHICLES AND REQUESTS */
    protected Set<model.demand.User> unassignedRequests; // Requests whose pickup time is lower than the current time
    protected Set<model.demand.User> listPooledUsersTW;  // Requests pooled within TW
    protected Set<model.demand.User> deniedRequests; // Requests with expired pickup time
    protected Set<model.demand.User> finishedRequests; // Requests whose DP node was visited	public
    Map<Integer, User> allRequests; // Dictionary of all users
    private Environment env;
    private Random randomSeed;

    RequestManager(Environment environment, int randomSeed) {
        this(environment);
        this.randomSeed = new Random(randomSeed);
    }

    RequestManager(Environment environment) {
        this.env = environment;
        // Dictionary of all users
        this.allRequests = new HashMap<>();
        // Requests whose pickup time is lower than the current time
        this.unassignedRequests = new HashSet<>();
        // Requests with expired pickup time
        this.deniedRequests = new HashSet<>();
        // Requests whose DP node was visited
        this.finishedRequests = new HashSet<>();
    }

    private List<Request> subset(int nSamples, List<Request> trips) {
        if (trips.size() > nSamples) {
            return trips.stream().limit(trips.size()).collect(Collectors.toList());
        }
        return new ArrayList<>(trips);
    }

    public Set<UserRequest> getRequestsBetween(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        List<Request> requests = this.env.getRequestsBetween(startDateTime, endDateTime);

        if (samplingRequests()) {
            requests = getSampleFrom(requests);
        }

        return createUsersFromRequests(requests);
    }

    private List<Request> getSampleFrom(List<Request> requests) {
        requests = shuffleTrips(requests);
        int nSamples = getNumberOfSamples(requests);
        requests = subset(nSamples, requests);
        Collections.sort(requests);
        return requests;
    }


    private Set<UserRequest> createUsersFromRequests(List<Request> requests) {
        Set<UserRequest> users = new TreeSet<>();
        for (Request request : requests) {
            UserRequest u = getUserFromRequest(request);
            users.add(u);
        }
        return users;
    }

    private UserRequest getUserFromRequest(Request request) {
        String userClass = this.env.getRandomClassForRequest(this.randomSeed);
        ServiceLevel serviceLevelConfig = this.env.demandConfig.serviceLevelMap().get(userClass);

        int numPassengers = Math.min(
                this.env.maxVehicleCapacity(),
                request.passengerCount());


        LocalDateTime pickupLatest = request.pickupDateTime().plusSeconds(serviceLevelConfig.pickupDelaySec());

        int delay = this.env.distBetweenOriginDestinationSec(request);
        LocalDateTime dropoffEarliest = request.pickupDateTime().plusSeconds(delay);
        LocalDateTime dropoffLatest = dropoffEarliest.plusSeconds(serviceLevelConfig.pickupDelaySec());

        NodeSource pk = new NodeSource(
                request.id(),
                request.pickupNodeNetworkId(),
                request.pickupDateTime(),
                pickupLatest,
                numPassengers
        );

        NodeDestination dp = new NodeDestination(
                request.id(),
                request.dropoffNodeNetworkId(),
                dropoffEarliest,
                dropoffLatest,
                numPassengers
        );
        UserRequest user = new UserRequest(request.id(), pk, dp);
        return user;
    }


    private int getNumberOfSamples(List<Request> trips) {
        return (int) (trips.size() * this.env.demandConfig.percentageRequests());
    }

    private List<Request> shuffleTrips(List<Request> trips) {

        ArrayList<Request> shuffled = new ArrayList<>(trips);
        Collections.shuffle(shuffled, this.randomSeed);
        return shuffled;
    }

    private boolean samplingRequests() {
        return this.env.demandConfig.percentageRequests() < 1;
    }
}