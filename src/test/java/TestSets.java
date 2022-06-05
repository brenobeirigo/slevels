import dao.Logging;

import java.util.HashSet;
import java.util.Set;

public class TestSets {

    public static void main(String[] str) {

        Set<Set<Integer>> setOfSet = new HashSet<>();
        Set<Integer> s1 = new HashSet<>();
        s1.add(1);
        s1.add(2);

        Set<Integer> s2 = new HashSet<>();
        s2.add(3);
        s2.add(4);

        setOfSet.add(s1);
        setOfSet.add(s2);

        Logging.logger.info(setOfSet.toString());


        Set<Integer> s3 = new HashSet<>();
        s3.add(3);
        s3.add(4);

        if (setOfSet.contains(s3)) {
            Logging.logger.info(s3 + " in " + setOfSet);
        }
    }

}
