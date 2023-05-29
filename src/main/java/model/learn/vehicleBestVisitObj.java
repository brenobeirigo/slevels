package model.learn;


import simulation.matching.ResultAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FleetStateActionRewardObject {
    protected List<Integer> vehicle_ids;
    protected List<Integer> delay_arrival_latest;
    protected List<Integer> request_count;
    //    protected List<Double> vfs;
    protected List<Long> delay_earliest_arrival;
    protected List<List<Integer>> requests;
    protected List<Double> vf;

    public FleetStateActionRewardObject(ResultAssignment result) {

        this.vehicle_ids = new ArrayList<>();
        this.requests = new ArrayList<>();
        this.request_count = new ArrayList<>();
        this.vf = new ArrayList<>();
        this.delay_earliest_arrival = new ArrayList<>(); // Arrival - earliest time
        this.delay_arrival_latest = new ArrayList<>(); // Latest time - arrival

        result.vehicleIdBestVisitMap.forEach((v, bestVisit)-> {
            if (bestVisit == null){

                this.vehicle_ids.add(v);
                this.delay_earliest_arrival.add(bestVisit.getDelay());
//            this.delay_arrival_latest.add(bestVisit.getDelayBonus());
                this.request_count.add(bestVisit.getRequests().size());
                this.requests.add(bestVisit.getRequests().stream().map(UserRequest::getRequestId).collect(Collectors.toList()));
                this.vf.add(bestVisit.getVF());
            }
        }
    }