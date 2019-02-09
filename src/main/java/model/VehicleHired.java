package model;

public class VehicleHired extends Vehicle {


    public VehicleHired(int capacity, int id_network, int currentTime, boolean hired, int contractDuration) {
        super(capacity, id_network, currentTime);
        //this.contractedRounds = contractDuration;
        //this.contractDeadline = currentTime + contractDuration;
    }
}
