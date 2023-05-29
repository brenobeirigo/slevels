package model.demand;

import java.time.LocalDateTime;

public record Request(int id, LocalDateTime pickupDateTime, int pickupNodeNetworkId, int dropoffNodeNetworkId, int passengerCount) implements Comparable<Request>{
    public static final String PICKUP_DATETIME = "pickup_datetime";
    public static final String USER_ID = "id";
    public static final String PASSENGER_COUNT = "passenger_count";
    public static final String PICKUP_NODE_ID = "pickup_node_id";
    public static final String DROPOFF_NODE_ID = "dropoff_node_id";
    public static final String PICKUP_LATITUDE = "pickup_latitude";
    public static final String PICKUP_LONGITUDE = "pickup_longitude";
    public static final String DROPOFF_LATITUDE = "dropoff_latitude";
    public static final String DROPOFF_LONGITUDE = "dropoff_longitude";

    @Override
    public int compareTo(Request o) {
        return this.pickupDateTime.compareTo(o.pickupDateTime);
    }
};
