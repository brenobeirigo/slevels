package config;

public class Qos implements Comparable<Qos> {
    public static final int PRIVATE_VEHICLE = 0;
    public static final int ALLOWED_SHARING = 1;

    public static final int SERVICE_LEVEL_1 = 1;
    public static final int SERVICE_LEVEL_2 = 2;


    public String id;
    public int code;
    public double serviceRate;
    public int pkDelay, pkDelayTarget, dpDelay;
    public double share;
    public boolean allowedSharing;
    public String serviceRateLabel;
    public String customerSegmentationLabel;
    private Integer priority;

    public Qos(String id, int code, int pkDelay, int dpDelay, double serviceRate) {
        this.code = code;
        this.id = id;
        this.serviceRate = serviceRate;
        this.pkDelay = pkDelay;
        this.dpDelay = dpDelay;

    }


    public Qos(String id, int code, int pkDelay, int dpDelay, double serviceRate, double share, boolean allowedSharing) {
        this.code = code;
        this.id = id;
        this.serviceRate = serviceRate;
        this.pkDelay = pkDelay;
        this.dpDelay = dpDelay;
        this.share = share;
        this.allowedSharing = allowedSharing;
    }

    public Qos(String id, int code, String serviceRateLabel, String segmentationScenarioLabel, int priority, int pkDelay, int pkDelayTarget, int dpDelay, double serviceRate, double share, boolean allowedSharing) {
        this.code = code;
        this.priority = priority;
        this.id = id;
        this.serviceRate = serviceRate;
        this.pkDelay = pkDelay;
        this.pkDelayTarget = pkDelayTarget;
        this.dpDelay = dpDelay;
        this.share = share;
        this.allowedSharing = allowedSharing;
        this.serviceRateLabel = serviceRateLabel;
        this.customerSegmentationLabel = segmentationScenarioLabel;
    }

    @Override
    public String toString() {
        return "[" + serviceRateLabel + " " + customerSegmentationLabel + " " + id + "] service rate = " + serviceRate + " - target pk delay = " + pkDelayTarget + " - pk delay = " + pkDelay + " - dp delay = " + dpDelay + " - share = " + share + " - allow sharing = " + share;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public int compareTo(Qos o) {
        return this.priority.compareTo(o.priority);
    }
}