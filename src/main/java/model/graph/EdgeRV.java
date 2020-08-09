package model.graph;

import model.User;
import model.Vehicle;

class EdgeRV implements Comparable<EdgeRV> {

    protected final Integer delay;
    protected final Object from;
    protected final Object target;

    public EdgeRV(int delay, Object from, Object target) {
        this.delay = delay;
        this.from = from;
        this.target = target;
    }

    @Override
    public int compareTo(EdgeRV edge) {
        return this.delay.compareTo(edge.delay);
    }

    public boolean isHiringEdge() {
        if (this.from instanceof Vehicle) {
            return ((Vehicle) this.from).isHired();
        }else{
            return false;
        }

    }
    public boolean isHiringEdgeAndUserHasPromptedHiring() {
        return isHiringEdge() && ((Vehicle) this.from).getUserHiredMustPickup() == this.target;
    }

    public boolean isRV() {
        return this.from instanceof Vehicle && this.target instanceof User;
    }

    public boolean isIncumbentVehicleEdge() {
        return this.isRV() && this.getRequestFromRV().getCurrentVehicle() == this.getVehicleFromRV();
    }

    private User getRequestFromRV() {
        return (User) this.target;
    }

    private Vehicle getVehicleFromRV() {
        return (Vehicle) this.from;
    }

    @Override
    public String toString() {
        String rrOrRv = "EdgeRR";
        if (this.isRV()) {
            rrOrRv = "EdgeRV";
        }

        return rrOrRv + "{" +
                "from=" + from +
                ", target=" + target +
                ", delay=" + delay +
                '}';
    }
}