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
    public Map<Character, Qos> qosDic = new HashMap<>();
    private Date firstDate;

    private Config() {

        this.qosDic.put('A', new Qos('A', 120, 0, 1));
        this.qosDic.put('B', new Qos('B', 300, 600, 0.9));
        this.qosDic.put('C', new Qos('C', 600, 900, 0.8));


        try {
            this.firstDate = Config.formatter_date_time.parse("2011-02-12 00:00:00");
        } catch (ParseException e) {
            e.printStackTrace();
        }
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

    public class Qos {
        public char id;
        public double serviceRate;
        public int pkDelay, dpDelay;

        public Qos(char id, int pkDelay, int dpDelay, double serviceRate) {
            this.id = id;
            this.serviceRate = serviceRate;
            this.pkDelay = pkDelay;
            this.dpDelay = dpDelay;
        }
    }
}
