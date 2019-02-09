package config;

import java.util.Map;

public class CustomerBaseConfig {

    public String serviceRateLabel;
    public String customerSegmentationLabel;

    public Map<String, Config.Qos> qosDic;

    public CustomerBaseConfig(String serviceRateLabel, String customerSegmentationLabel, Map<String, Config.Qos> qosDic) {
        this.serviceRateLabel = serviceRateLabel;
        this.customerSegmentationLabel = customerSegmentationLabel;
        this.qosDic = qosDic;
    }
}
