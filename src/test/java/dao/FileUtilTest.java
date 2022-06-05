package dao;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilTest {

    @Test
    void readJson() {
        String a = "D:\\projects\\dev\\slevels\\config.json";
        String d = FileUtil.readJson(a);
        Logging.logger.info(d);
    }
}