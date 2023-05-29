package model.graph;

import com.google.common.base.Objects;
import model.demand.User;
import model.Vehicle;

class EdgeVR implements EdgeRV {

    protected final Integer delay;
    protected final Vehicle from;
    protected final User target;

    public EdgeVR(int delay, Vehicle from, User target) {
        this.delay = delay;
        this.from = from;
        this.target = target;
    }


    @Override
    public boolean isHiringEdge() {
        return this.from.isHired();
    }

    @Override
    public boolean isHiringEdgeAndUserHasPromptedHiring() {
        return isHiringEdge() && this.from.getUserHiredMustPickup() == this.target;
    }

    @Override
    public boolean isRV() {
        return true;
    }

    @Override
    public boolean isIncumbentVehicleEdge() {
        return this.target.getCurrentVehicle() == this.from;
    }

    @Override
    public int getDelay() {
        return this.delay;
    }

    @Override
    public Object getFrom() {
        return this.from;
    }

    @Override
    public Object getTarget() {
        return this.target;
    }


    @Override
    public String toString() {
        return "EdgeVR{" +
                "from=" + from +
                ", target=" + target +
                ", delay=" + delay +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EdgeVR edgeVR = (EdgeVR) o;
        return Objects.equal(getDelay(), edgeVR.getDelay()) && Objects.equal(getFrom(), edgeVR.getFrom()) && Objects.equal(getTarget(), edgeVR.getTarget());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getDelay(), getFrom(), getTarget());
    }

    @Override
    public int compareTo(EdgeRV o) {
        return this.delay.compareTo(o.getDelay());
    }
}
