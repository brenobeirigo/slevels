import config.Config;

import java.util.Date;

public class TestConfig {

    public static void main(String[] arg) {
        Date firstDate = Config.getInstance().getEarliestTime();
        String d1 = "2011-02-12 00:04:00";
        System.out.println("String to date (int):" + Config.getInstance().date2Seconds(d1));
        System.out.println("First date:" + Config.formatter_date_time.format(firstDate));
        System.out.println("Sum secs:" + Config.formatter_date_time.format(Config.getInstance().seconds2Date(3)));
    }
}
