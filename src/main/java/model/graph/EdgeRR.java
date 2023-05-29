package model.graph;

import com.google.common.base.Objects;
import model.demand.User;

class EdgeRR implements EdgeRV {

    protected final Integer delay;
    protected final User from;
    protected final User target;

    public EdgeRR(int delay, User from, User target) {
        this.delay = delay;
        this.from = from;
        this.target = target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EdgeRR edgeRR = (EdgeRR) o;
        return Objects.equal(getDelay(), edgeRR.getDelay()) && Objects.equal(getFrom(), edgeRR.getFrom()) && Objects.equal(getTarget(), edgeRR.getTarget());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getDelay(), getFrom(), getTarget());
    }

    @Override
    public boolean isHiringEdge() {
        return false;
    }

    @Override
    public boolean isHiringEdgeAndUserHasPromptedHiring() {
        return false;
    }

    @Override
    public boolean isRV() {
        return false;
    }

    @Override
    public boolean isIncumbentVehicleEdge() {
        return false;
    }

    @Override
    public int getDelay() {
        return this.delay;
    }


    @Override
    public String toString() {
        return "EdgeRR{" +
                "from=" + from +
                ", target=" + target +
                ", delay=" + delay +
                '}';
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
    public int compareTo(EdgeRV o) {
        return this.delay.compareTo(o.getDelay());
    }
}
