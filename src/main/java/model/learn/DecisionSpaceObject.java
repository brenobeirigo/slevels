package model.learn;

import org.checkerframework.checker.units.qual.A;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class DecisionSpaceObject {
    public static final int MAX_CAPACITY_VEHICLE = 4;

    // Tracking sequence
    protected List<Integer> vehicle_ids;

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


    public DecisionSpaceObject(List<VehicleDecisionSpace> vehicleCandidateDecisions, boolean guaranteeStableArraySize) {
        vehicle_ids = new ArrayList<>();
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

        for (VehicleDecisionSpace vehicleDecisions : vehicleCandidateDecisions) {
            vehicle_ids.add(vehicleDecisions.vehicleId);
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
