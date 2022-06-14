package model.learn;

import com.google.common.base.Objects;

import java.util.*;
import java.util.stream.Collectors;


public class FleetStateActionSpaceObject {
    public static final int MAX_CAPACITY_VEHICLE = 4;
    protected int id;

    // Tracking sequence
    public Map<Integer,Integer> vehicle_decision_count;

    // Reward data
    protected List<List<Integer>> obj_request_count;
    protected List<List<Integer>> obj_total_delay;
    protected List<List<Integer>> obj_total_delay_bonus;
    protected List<List<List<Integer>>> obj_request_ids;

    // Reward data normalized
    protected List<List<Double>> num_requests_input;
    protected List<List<List<Double>>> delay_input;

    // Vehicle data
    protected List<List<Double>> vehicle_capacity_input;

    protected List<List<Double>> current_time_input;
    protected List<List<Double>> other_agents_input;
    protected List<List<List<Integer>>> path_location_input;
    protected List<List<List<Double>>> occupancy_input;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FleetStateActionSpaceObject that = (FleetStateActionSpaceObject) o;
        return Objects.equal(obj_request_count, that.obj_request_count) && Objects.equal(obj_total_delay, that.obj_total_delay) && Objects.equal(obj_total_delay_bonus, that.obj_total_delay_bonus) && Objects.equal(num_requests_input, that.num_requests_input) && Objects.equal(delay_input, that.delay_input) && Objects.equal(vehicle_capacity_input, that.vehicle_capacity_input) && Objects.equal(current_time_input, that.current_time_input) && Objects.equal(other_agents_input, that.other_agents_input) && Objects.equal(path_location_input, that.path_location_input) && Objects.equal(occupancy_input, that.occupancy_input);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                obj_request_count,
                obj_total_delay,
                obj_total_delay_bonus,
                num_requests_input,
                delay_input,
                vehicle_capacity_input,
                current_time_input,
                other_agents_input,
                path_location_input,
                occupancy_input);


    }

    public FleetStateActionSpaceObject(List<VehicleStateActionSpace> vehicleCandidateDecisions, boolean guaranteeStableArraySize) {
        vehicle_decision_count = new LinkedHashMap<>();
        obj_total_delay = new ArrayList<>();
        obj_total_delay_bonus = new ArrayList<>();
        obj_request_ids = new ArrayList<>();
        obj_request_count = new ArrayList<>();

        vehicle_capacity_input = new ArrayList<>();
        occupancy_input = new ArrayList<>();
        current_time_input = new ArrayList<>();
        num_requests_input = new ArrayList<>();
        delay_input = new ArrayList<>();
        path_location_input = new ArrayList<>();
        other_agents_input = new ArrayList<>();

        for (VehicleStateActionSpace vehicleDecisions : vehicleCandidateDecisions) {
            vehicle_decision_count.put(vehicleDecisions.vehicleId, vehicleDecisions.decisionCount);
            vehicle_capacity_input.add(vehicleDecisions.normalCapacity);
            obj_total_delay.add(vehicleDecisions.totalDelay);
            obj_total_delay_bonus.add(vehicleDecisions.totalDelayBonus);
            obj_request_ids.add(vehicleDecisions.requestIds);

            obj_request_count.add(vehicleDecisions.requestCounts);

            current_time_input.add(vehicleDecisions.currentTimes);
            num_requests_input.add(vehicleDecisions.numRequests);
            other_agents_input.add(vehicleDecisions.surroundingVehiclesCount);
            if (guaranteeStableArraySize) {
                delay_input.add(vehicleDecisions.arrivalDelays.stream().map(this::getSingleSizeArray).collect(Collectors.toList()));
                path_location_input.add(vehicleDecisions.nextNetworkIds.stream().map(this::getSingleSizeArrayInt).collect(Collectors.toList()));
                occupancy_input.add(vehicleDecisions.occupancyRates.stream().map(this::getSingleSizeArray).collect(Collectors.toList()));
            }else{
                delay_input.add(vehicleDecisions.arrivalDelays);
                path_location_input.add(vehicleDecisions.nextNetworkIds);
                occupancy_input.add(vehicleDecisions.occupancyRates);

            }
        }

        this.id = this.hashCode();
    }

    /**
     * Origin + Middle + PDs
     * @param pdData
     * @return
     */
    private List<Double> getSingleSizeArray(List<Double> pdData) {
        Double[] fixedSizedPDSequence = new Double[2 + 2 * MAX_CAPACITY_VEHICLE];
        System.arraycopy(pdData.toArray(), 0, fixedSizedPDSequence, 0, pdData.size());
        return Arrays.asList(fixedSizedPDSequence);
    }

    /**
     * Origin + PDs
     * @param pdData
     * @return
     */
    private List<Integer> getSingleSizeArrayInt(List<Integer> pdData) {
        Integer[] fixedSizedPDSequence = new Integer[2 + 2 * MAX_CAPACITY_VEHICLE];
        System.arraycopy(pdData.toArray(), 0, fixedSizedPDSequence, 0, pdData.size());
        return Arrays.asList(fixedSizedPDSequence);
    }
}
