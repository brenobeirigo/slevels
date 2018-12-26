package config;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Config {

    public static DateFormat formatter_t = new SimpleDateFormat("HH:mm:ss");
    public static DateFormat formatter_date_time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static Config ourInstance = new Config();
    public static final int BEFORE = -1;
    public static final int EQUAL = 0;
    public static final int AFTER = 1;
    public Map<String, Qos> qosDic;
    private Date firstDate;


    private Config() {

        qosDic = new HashMap<>();

        try {
            this.firstDate = Config.formatter_date_time.parse("2011-02-01 00:00:00");
        } catch (ParseException e) {
            e.printStackTrace();
        }
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

    public int date2Seconds(String departureDate) {
        int secs = -1;
        try {
            secs = (int) (Config.formatter_date_time.parse(departureDate).getTime() - this.firstDate.getTime()) / 1000;
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return secs;
    }

    public Date getFirstDate() {
        return firstDate;
    }

    public Date seconds2Date(int departureDate) {
        return new Date(departureDate * 1000 + this.firstDate.getTime());
    }


    public void printQosDic() {
        for (Map.Entry<String, Qos> e : qosDic.entrySet()) {
            System.out.println(e.getKey() + " - " + e.getValue());
        }
    }

    public static class Qos {
        public static final int PRIVATE_VEHICLE = 0;
        public static final int ALLOWED_SHARING = 1;
        public String id;
        public double serviceRate;
        public int pkDelay, dpDelay;
        public double share;
        public boolean allowedSharing;

        public Qos(String id, int pkDelay, int dpDelay, double serviceRate) {
            this.id = id;
            this.serviceRate = serviceRate;
            this.pkDelay = pkDelay;
            this.dpDelay = dpDelay;

        }

        public Qos(String id, int pkDelay, int dpDelay, double serviceRate, double share, boolean allowedSharing) {
            this.id = id;
            this.serviceRate = serviceRate;
            this.pkDelay = pkDelay;
            this.dpDelay = dpDelay;
            this.share = share;
            this.allowedSharing = allowedSharing;
        }

        @Override
        public String toString() {
            return "[SQ " + id + "] service rate = " + serviceRate + " - pk delay = " + pkDelay + " - dp delay = " + dpDelay + " - share = " + share + " - allow sharing = " + share;
        }

        @Override
        public int hashCode() {
            return this.id.hashCode();
        }
    }
}
