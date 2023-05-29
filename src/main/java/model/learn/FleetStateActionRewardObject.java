package model.learn;

import dao.Logging;
import model.demand.User;
import model.Vehicle;
import model.visit.VisitObj;
import simulation.matching.ResultAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FleetStateActionRewardObject {
    protected List<Integer> delay_arrival_latest;
    protected List<Integer> vehicle_ids;
    protected List<Integer> request_count;
//    protected List<Double> vfs;
    protected List<Integer> delay_earliest_arrival;
    protected List<List<Integer>> requests;
    protected List<Double> vf;

    public FleetStateActionRewardObject(Set<Vehicle> vehicles, ResultAssignment result) {

        this.vehicle_ids = new ArrayList<>();
        this.requests = new ArrayList<>();
        this.request_count = new ArrayList<>();
        this.vf = new ArrayList<>();
        this.delay_earliest_arrival = new ArrayList<>(); // Arrival - earliest time
        this.delay_arrival_latest = new ArrayList<>(); // Latest time - arrival

        for (Vehicle v : vehicles) {
            VisitObj bestVisit = result.getChosenVisitForVehicle(v);
            if (bestVisit == null){

                result.vehicleBestVisitMap.forEach((a,b)->
                        Logging.logger.info(a + "-" + b));
            }
            this.vehicle_ids.add(v.getId());
            this.delay_earliest_arrival.add(bestVisit.getDelay());
            this.delay_arrival_latest.add(bestVisit.getDelayBonus());
            this.request_count.add(bestVisit.getRequestsTotalLoad());
            this.requests.add(bestVisit.getRequests().stream().map(User::getId).collect(Collectors.toList()));
            this.vf.add(bestVisit.getVF());
        }
    }
}

