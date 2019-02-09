package model;

public class RebalanceEpisode {
    private int origin; // Node where vehicle started rebalancing
    private int target; // Target node where vehicle is heading
    private int middle; // Node where vehicle was matched
    private int roundsIdle; // How many rounds vehicle was idle before rebalancing
    private double rebalancingMileage; // How far vehicle spend rebalancing
    private double distanceOT; // Distance between origin and target
    private int userFoundInRounds; // How many rounds to find user?

    public RebalanceEpisode(int origin, int target, int middle, int roundsIdle, double rebalancingMileage, double distanceOT, int userFoundInRounds) {
        this.origin = origin;
        this.target = target;
        this.middle = middle;
        this.roundsIdle = roundsIdle;
        this.rebalancingMileage = rebalancingMileage;
        this.distanceOT = distanceOT;
        this.userFoundInRounds = userFoundInRounds;
    }

    @Override
    public String toString() {
        return "RebalanceEpisode{" +
                "origin=" + origin +
                ", target=" + target +
                ", middle=" + middle +
                ", roundsIdle=" + roundsIdle +
                ", rebalancingMileage=" + rebalancingMileage +
                ", distanceOT=" + distanceOT +
                ", userFoundInRounds=" + userFoundInRounds +
                '}';
    }
}
