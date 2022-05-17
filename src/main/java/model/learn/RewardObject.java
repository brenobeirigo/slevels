package model.learn;

import model.User;
import model.Vehicle;
import model.VisitObj;
import simulation.matching.ResultAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RewardObject {
    protected List<Integer> remaining;
    protected List<Integer> vehicle_ids;
    protected List<Integer> request_count;
    protected List<Integer> delays;
    protected List<List<Integer>> requests;

    public RewardObject(Set<Vehicle> vehicles, ResultAssignment result) {

        this.vehicle_ids = new ArrayList<>();
        this.request_count = new ArrayList<>();
        this.delays = new ArrayList<>(); // Arrival - earliest time
        this.remaining = new ArrayList<>(); // Latest time - arrival
        this.requests = new ArrayList<>();

        for (Vehicle v : vehicles) {
            VisitObj bestVisit = result.getChosenVisitForVehicle(v);
            this.vehicle_ids.add(v.getId());
            this.delays.add(bestVisit.getDelay());
            this.remaining.add(bestVisit.getDelayBonus());
            this.request_count.add(bestVisit.getRequestsTotalLoad());
            this.requests.add(bestVisit.getRequests().stream().map(User::getId).collect(Collectors.toList()));
        }
    }
}

