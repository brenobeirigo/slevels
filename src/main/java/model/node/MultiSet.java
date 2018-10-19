package model.node;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class MultiSet {

    private List<Integer> seed;
    private List<List<Integer>> permutations = new ArrayList<>();


/*
    This module encodes functions to generate the permutations of a multiset
    following this algorithm:

    Algorithm 1
    Visits the permutations of multiset E. The permutations are stored
    in a singly-linked list pointed to by head pointer h. Each node in the linked
    list has a value field v and a next field n. The init(E) call creates a
    singly-linked list storing the elements of E in non-increasing order with h, i,
    and j pointing to its first, second-last, and last nodes, respectively. The
    null pointer is given by φ. Note: If E is empty, then init(E) should exit.
    Also, if E contains only one element, then init(E) does not need to provide a
    value for i.
    [h, i, j] ← init(E)
    visit(h)
    while j.n ≠ φ orj.v <h.v do
            if j.n ≠    φ and i.v ≥ j.n.v then
    s←j
    else
    s←i
    end if
    t←s.n
    s.n ← t.n
    t.n ← h
    if t.v < h.v then
    i←t
    end if
    j←i.n
    h←t
    visit(h)
    end while
            ... from "Loopless Generation of ListElement Permutations using a Constant Number
    of Variables by Prefix Shifts."  Aaron Williams, 2009
    */

    public MultiSet(List<Integer> seed) {
        this.seed = seed;
    }

    public static void main(String[] str) throws IOException {
        MultiSet m = new MultiSet(Arrays.asList(1, 1, 2, 2));

        m.getPermutations(1);
        FileWriter out = new FileWriter("book_new.csv");

        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            m.permutations.forEach(x -> {
                try {
                    System.out.println(x);
                    printer.printRecord(x);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        System.out.println(m.permutations.size());
    }

    public ListElement[] init(List<Integer> multiset) {
        // ensures proper non-increasing order
        Collections.sort(multiset);

        ListElement h = new ListElement(multiset.get(0), null);

        for (int j = 1; j < multiset.size(); j++) {
            h = new ListElement(multiset.get(j), h);
        }
        ListElement[] trio = {h, h.nth(multiset.size() - 2), h.nth(multiset.size() - 1)};
        return trio;
    }

    public List<Integer> visit(ListElement h) {
        // Converts our bespoke linked list to a python list.
        ListElement o = h;
        List<Integer> l = new LinkedList<>();
        while (o != null) {
            l.add(o.value);
            o = o.next;
        }
        return l;
    }

    /**
     * Return 1 to maxPermutations
     *
     * @param maxPermutations Maximum number of permutations (>=1)
     * @return List of permutations
     */
    public List<List<Integer>> getPermutations(int maxPermutations) {

        //Generator providing all multiset permutations of a multiset.

        ListElement trio[] = init(seed);
        ListElement h = trio[0];
        ListElement i = trio[1];
        ListElement j = trio[2];


        permutations.add(visit(h));


        while (j.next != null || j.value < h.value) {

            // Stop when max number of permutations is reached
            if (permutations.size() >= maxPermutations) break;

            ListElement s;
            if (j.next != null && i.value >= j.next.value) {
                s = j;
            } else {
                s = i;
            }

            ListElement t = s.next;
            s.next = t.next;
            t.next = h;
            if (t.value < h.value) {
                i = t;
            }

            j = i.next;
            h = t;

            permutations.add(visit(h));


        }
        return permutations;
    }

    private class ListElement {
        public Integer value;
        public ListElement next;
        public int count = 0;

        public ListElement(Integer value, ListElement next) {
            this.value = value;
            this.next = next;
        }

        public ListElement nth(int n) {
            ListElement o = this;
            int i = 0;
            while (i < n && o.next != null) {
                o = o.next;
                i += 1;
            }
            return o;
        }
    }

}
