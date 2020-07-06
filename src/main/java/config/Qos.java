package config;

public class Qos {
    public static final int PRIVATE_VEHICLE = 0;
    public static final int ALLOWED_SHARING = 1;

    public static final int SERVICE_LEVEL_1 = 1;
    public static final int SERVICE_LEVEL_2 = 2;

    private static int countQos = 0;

    public String id;
    public int code;
    public double serviceRate;
    public int pkDelay, pkDelayTarget, dpDelay;
    public double share;
    public boolean allowedSharing;
    public String serviceRateLabel;
    public String customerSegmentationLabel;

    public Qos(String id, int pkDelay, int dpDelay, double serviceRate) {
        this.code = countQos++;
        this.id = id;
        this.serviceRate = serviceRate;
        this.pkDelay = pkDelay;
        this.dpDelay = dpDelay;

    }


    public Qos(String id, int pkDelay, int dpDelay, double serviceRate, double share, boolean allowedSharing) {
        this.code = countQos++;
        this.id = id;
        this.serviceRate = serviceRate;
        this.pkDelay = pkDelay;
        this.dpDelay = dpDelay;
        this.share = share;
        this.allowedSharing = allowedSharing;
    }

    public Qos(String id, String serviceRateLabel, String segmentationScenarioLabel, int pkDelay, int pkDelayTarget, int dpDelay, double serviceRate, double share, boolean allowedSharing) {
        this.code = countQos++;
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
        return "[" + serviceRateLabel + " " + customerSegmentationLabel + " " + id + "] service rate = " + serviceRate + " - pk delay = " + pkDelay + " - dp delay = " + dpDelay + " - share = " + share + " - allow sharing = " + share;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }
}