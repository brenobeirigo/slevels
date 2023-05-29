package config;

import dao.FileUtil;
import dao.Logging;
import experiment.LoggingConfig;

import java.io.IOException;
import java.util.*;

public class Config {

    // Duration
    public static final int DURATION_SINGLE_RIDE = 0;
    public static final int BEFORE = -1;
    public static final int EQUAL = 0;
    public static final int AFTER = 1;
    public static InstanceConfig instanceSettings;
    public static Map<String, Boolean> infoHandling;
    private static Config ourInstance = new Config();
    private static LoggingConfig configInstance;
    public Map<String, Qos> qosDic;
    private List<Qos> qosListPriority;
    private String spAlgorithm;

    private Config() {
        qosDic = new HashMap<>();
    }

    public static InstanceConfig createInstanceFrom(String source) {

        try {
            configInstance = FileUtil.getMapFrom(source);

            Config.infoHandling = configInstance.info_handling();

            Logging.logger.info("# Reading configuration from '{}'...", source);
             Logging.logger.info("# Round information level: {}", configInstance.info_level());


        } catch (IOException e) {
            Logging.logger.info("Error! Cannot read " + source);
            e.printStackTrace();
        }
        return null;
    }

    public static boolean showRoundInfo() {
        return Config.configInstance.info_handling().get(Logging.SHOW_ROUND_INFO);
    }

    public static boolean showRoundMIPInfo() {
        return Config.configInstance.info_handling().get(Logging.SHOW_ROUND_MIP_INFO);
    }

    public static boolean saveRoundMIPInfo() {
        return Config.configInstance.info_handling().get(Logging.SAVE_ROUND_MIP_INFO_LP);
    }

    public static boolean saveRoundInfo() {
        return Config.configInstance.info_handling().get(Logging.SAVE_ROUND_INFO_CSV);
    }

    public static boolean showRoundFleetStatus() {
        return Config.configInstance.info_handling().get(Logging.SHOW_ROUND_FLEET_STATUS);
    }

    public static void reset() {
        ourInstance = new Config();
    }

    public static Config getInstance() {
        return ourInstance;
    }

    public int getQosCount() {
        return this.qosDic.size();
    }


    public void printQosDic() {
        for (Map.Entry<String, Qos> e : qosDic.entrySet()) {
            Logging.logger.info(e.getKey() + " - " + e.getValue());
        }
    }

    public List<Qos> createSortedQosList() {
        List<Qos> qos = new ArrayList<>(this.qosDic.values());
        Collections.sort(qos);
        return qos;
    }

    public void updateQosDic(Map<String, Qos> qosDic) {
        this.qosDic = qosDic;
        this.qosListPriority = createSortedQosList();
    }

    public List<Qos> getSortedQosList() {
        return qosListPriority;
    }


    public void setShortestPathAlgorithm(String spMethod) {
        this.spAlgorithm = spMethod;
    }

    public String getSpAlgorithm() {
        return spAlgorithm;
    }
}
