package config;

import dao.FileUtil;
import dao.Logging;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Config {

    public static final byte PRINT_ALL_ROUND_INFO = 1;
    public static final byte PRINT_SUMMARY_ROUND_INFO = 2;
    public static final byte PRINT_NO_ROUND_INFO = 0;
    public static final String SAVE_VEHICLE_ROUND_GEOJSON = "save_vehicle_round_geojson";
    public static final String SAVE_EXPERIENCES = "save_experiences";
    public static final String SAVE_REQUEST_INFO_CSV = "save_request_info_csv";
    public static final String SAVE_ROUND_INFO_CSV = "save_round_info_csv";
    public static final String SAVE_ROUND_MIP_INFO_LP = "save_round_mip_info_lp";
    // Print info in console
    public static final String SHOW_ROUND_MIP_INFO = "show_round_mip_info";
    public static final String SHOW_ALL_VEHICLE_JOURNEYS = "show_all_vehicle_journeys";
    public static final String SHOW_ROUND_FLEET_STATUS = "show_round_fleet_status";
    public static final String SHOW_ROUND_INFO = "show_round_info";
    // Duration
    public static final int DURATION_SINGLE_RIDE = 0;
    public static final int DURATION_1H = 3600;
    public static final int DURATION_3H = 3 * 3600;
    public static final int BEFORE = -1;
    public static final int EQUAL = 0;
    public static final int AFTER = 1;
    public static InstanceConfig instanceSettings;
    public static DateFormat formatter_t = new SimpleDateFormat("HH:mm:ss");
    public static DateFormat formatter_date_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static DateFormat formatter_date = new SimpleDateFormat("yyyy-MM-dd");
    public static Map<String, Boolean> infoHandling;
    private static Config ourInstance = new Config();
    private static ConfigInstance configInstance;
    public Map<String, Qos> qosDic;
    private List<Qos> qosListPriority;
    private String spAlgorithm;

    private Config() {
        qosDic = new HashMap<>();
    }

    public static InstanceConfig createInstanceFrom(String source) throws IOException {

        configInstance = FileUtil.getMapFrom(source);
        Config.infoHandling = configInstance.getInfoHandling();

        Logging.logger.info("# Reading configuration from '{}'...", source);
        Logging.logger.info("# Executing configuration at '{}'...", configInstance.getInstanceFilePath());
        Logging.logger.info("# Round information level: {}", configInstance.getInfoLevel());

        return InstanceConfig.getInstance(configInstance.getInstanceFilePath());
    }

    public static boolean showRoundInfo() {
        return Config.configInstance.getInfoHandling().get(SHOW_ROUND_INFO);
    }

    public static boolean showRoundMIPInfo() {
        return Config.configInstance.getInfoHandling().get(SHOW_ROUND_MIP_INFO);
    }

    public static boolean saveRoundMIPInfo() {
        return Config.configInstance.getInfoHandling().get(SAVE_ROUND_MIP_INFO_LP);
    }

    public static boolean saveRoundInfo() {
        return Config.configInstance.getInfoHandling().get(SAVE_ROUND_INFO_CSV);
    }

    public static boolean showRoundFleetStatus() {
        return Config.configInstance.getInfoHandling().get(SHOW_ROUND_FLEET_STATUS);
    }

    public static void reset() {
        ourInstance = new Config();
    }

    public static String sec2TStamp(Date earliestDatetime, int sec) {
        return Config.formatter_t.format(Config.getInstance().seconds2Date(earliestDatetime, sec));
    }

    public static String sec2Datetime(Date earliestDatetime, int sec) {
        return Config.formatter_date_time.format(Config.getInstance().seconds2Date(earliestDatetime, sec));
    }

    public static Config getInstance() {
        return ourInstance;
    }

    public int getQosCount() {
        return this.qosDic.size();
    }

    public int date2Seconds(Date earliestTime, String departureDate) {
        int secs = -1;
        try {
            secs = (int) (Config.formatter_date_time.parse(departureDate).getTime() - earliestTime.getTime()) / 1000;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return secs;
    }


    public Date seconds2Date(Date earliestTime, int departureDate) {
        return new Date(departureDate * 1000L + earliestTime.getTime());
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
