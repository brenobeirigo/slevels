import model.graph.GraphRV;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.max;

public class TestStream {

    public static void main(String[]  str){


        Integer[] v = new Integer[]{0,1,2,3};
        List<Integer> vv = new ArrayList<>();
        vv.add(0);
        vv.add(1);
        vv.add(2);
        vv.add(3);

        int n = 1000;
        long s1 = System.nanoTime();
        List<String>  a = IntStream.range(0, n-1).boxed().flatMap(i->IntStream.range(i+1, n).boxed().map(j->String.valueOf(i)+ String.valueOf(j))).collect(Collectors.toList());
        long s2 = System.nanoTime();
        System.out.println(s2-s1);

        long s3 = System.nanoTime();
        List<String>  b = IntStream.range(0, n-1).boxed().flatMap(i->IntStream.range(i+1, n).boxed().parallel().map(j->String.valueOf(i)+ String.valueOf(j))).collect(Collectors.toList());
        long s4 = System.nanoTime();
        System.out.println(s4-s3);

        long s5 = System.nanoTime();
        List<String>  c = IntStream.range(0, n-1).boxed().parallel().flatMap(i->IntStream.range(i+1, n).boxed().map(j->String.valueOf(i)+ String.valueOf(j))).collect(Collectors.toList());
        long s6 = System.nanoTime();
        System.out.println(s6-s5);

        long s7 = System.nanoTime();
        List<String>  d = new ArrayList<>();
        for (int i = 0; i <n-1; i++) {
            for (int j = i+1; j < n; j++) {
                d.add(String.valueOf(i) + String.valueOf(j));
            }
        }
        long s8 = System.nanoTime();
        System.out.println(s8-s7);


    }
}
