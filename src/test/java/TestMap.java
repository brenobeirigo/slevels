import dao.Logging;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public class TestMap {

    @Test
    public void testMapInsertion() {
        Map<Integer, Integer> m = new HashMap<>();
        m.put(1,1);
        m.put(3,2);
        m.put(3,3);
        Integer i = m.get(4);
        Logging.logger.info(m.toString());
    }
}
