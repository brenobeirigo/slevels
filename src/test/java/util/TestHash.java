package util;

import org.testng.annotations.Test;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.List;

public class TestHash {
    @Test
    public void testObjHash() {
        List<Double> a = new ArrayList<Double>();
        a.add(1.3);
        a.add(3.4);

        List<Integer> a1 = new ArrayList<>();
        a1.add(1);
        a1.add(3);

        List<Double> b = new ArrayList<Double>();
        b.add(1.3);
        b.add(3.4);

        List<Integer> b1 = new ArrayList<>();
        b1.add(1);
        b1.add(3);
        assert Objects.hashCode(a, a1) == Objects.hashCode(b, b1);
    }
}
