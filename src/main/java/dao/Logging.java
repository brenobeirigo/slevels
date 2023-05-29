package dao;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Logging {
    public static final String SAVE_VEHICLE_ROUND_GEOJSON = "save_vehicle_round_geojson";
    public static final String SAVE_REQUEST_INFO_CSV = "save_request_info_csv";
    public static final String SAVE_ROUND_INFO_CSV = "save_round_info_csv";
    public static final String SAVE_ROUND_MIP_INFO_LP = "save_round_mip_info_lp";
    // Print info in console
    public static final String SHOW_ROUND_MIP_INFO = "show_round_mip_info";
    public static final String SHOW_ALL_VEHICLE_JOURNEYS = "show_all_vehicle_journeys";
    public static final String SHOW_ROUND_FLEET_STATUS = "show_round_fleet_status";
    public static final String SHOW_ROUND_INFO = "show_round_info";

    public static Logger logger = LoggerFactory.getLogger(Logging.class);

}
