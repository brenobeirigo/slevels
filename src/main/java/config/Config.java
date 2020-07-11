package config;

import dao.FileUtil;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Config {

    public static final byte PRINT_ALL_ROUND_INFO = 1;
    public static final byte PRINT_SUMMARY_ROUND_INFO = 2;
    public static final byte PRINT_NO_ROUND_INFO = 0;
    public static final String SAVE_VEHICLE_ROUND_GEOJSON = "save_vehicle_round_geojson";
    public static final String SAVE_REQUEST_INFO_CSV = "save_request_info_csv";
    public static final String SAVE_ROUND_INFO_CSV = "save_round_info_csv";
    // Print info in console
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
    public static Map<String, Boolean> infoHandling;
    private static Config ourInstance = new Config();
    public Map<String, Qos> qosDic;
    private Date earliestTime;
    private List<Qos> qosListPriority;


    private Config() {
        qosDic = new HashMap<>();
    }

    public static InstanceConfig createInstanceFrom(String source) {


        String jsonConfigFilePath;
        Map<String, Boolean> infoHandling = new HashMap<>();

        try {

            System.out.println(String.format("# Reading configuration from \"%s\"...", source));
            // Reading input settings
            Map jsonConfig = FileUtil.getMapFrom(source);

            // Instances
            jsonConfigFilePath = jsonConfig.get("instance_file_path").toString();
            System.out.println("# Executing configuration at \"" + jsonConfigFilePath + "\"...");

            // Round information level (no information, round summary, all information)
            String infoLevelLabel = jsonConfig.get("info_level").toString();
            System.out.println("# Round information level: " + infoLevelLabel);

            //TODO read infoHandling from file
            infoHandling.put(SAVE_VEHICLE_ROUND_GEOJSON, false);
            infoHandling.put(SAVE_REQUEST_INFO_CSV, true);
            infoHandling.put(SAVE_ROUND_INFO_CSV, true);

            // Print info in console
            infoHandling.put(SHOW_ALL_VEHICLE_JOURNEYS, false);
            infoHandling.put(SHOW_ROUND_FLEET_STATUS, false);
            infoHandling.put(SHOW_ROUND_INFO, true);

        } catch (Exception e) {
            System.out.println("Cannot load configuration: " + e);
            System.out.println("Loading default instance (show round summary)...");
            // Json with instance
            jsonConfigFilePath = "C:\\Users\\LocalAdmin\\IdeaProjects\\slevels\\src\\main\\resources\\default_instance.json";

            infoHandling.put(SAVE_VEHICLE_ROUND_GEOJSON, true);
            infoHandling.put(SAVE_REQUEST_INFO_CSV, true);
            infoHandling.put(SAVE_ROUND_INFO_CSV, true);

            // Print info in console
            infoHandling.put(SHOW_ALL_VEHICLE_JOURNEYS, true);
            infoHandling.put(SHOW_ROUND_FLEET_STATUS, true);
            infoHandling.put(SHOW_ROUND_INFO, true);
        }

        Config.infoHandling = infoHandling;

        return InstanceConfig.getInstance(jsonConfigFilePath);
    }

    public static boolean showRoundInfo() {
        return Config.infoHandling.get(SHOW_ROUND_INFO);
    }

    public static boolean saveRoundInfo() {
        return Config.infoHandling.get(SAVE_ROUND_INFO_CSV);
    }

    public static boolean showRoundFleetStatus() {
        return Config.infoHandling.get(SHOW_ROUND_FLEET_STATUS);
    }

    public static void reset() {
        ourInstance = new Config();
    }

    public static String sec2TStamp(int sec) {
        return Config.formatter_t.format(Config.getInstance().seconds2Date(sec));
    }

    public static String sec2Datetime(int sec) {
        return Config.formatter_date_time.format(Config.getInstance().seconds2Date(sec));
    }

    public static Config getInstance() {
        return ourInstance;
    }

    public int getQosCount() {
        return this.qosDic.size();
    }

    public int date2Seconds(String departureDate) {
        int secs = -1;
        try {
            secs = (int) (Config.formatter_date_time.parse(departureDate).getTime() - this.earliestTime.getTime()) / 1000;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return secs;
    }

    public Date getEarliestTime() {
        return earliestTime;
    }

    public Date seconds2Date(int departureDate) {
        return new Date(departureDate * 1000 + this.earliestTime.getTime());
    }


    public void printQosDic() {
        for (Map.Entry<String, Qos> e : qosDic.entrySet()) {
            System.out.println(e.getKey() + " - " + e.getValue());
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

    public void setEarliestTime(Date earliestTime) {
        this.earliestTime = earliestTime;
    }
}
