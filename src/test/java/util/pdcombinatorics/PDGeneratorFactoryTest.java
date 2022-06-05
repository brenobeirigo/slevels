package util.pdcombinatorics;

import dao.Logging;
import model.User;
import model.Vehicle;
import model.Visit;
import model.node.Node;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PDGeneratorFactoryTest {

    static Set<User> requests;
    static Vehicle emptyVehicle, fullVehicle;

    @BeforeAll
    static void loadData(){
        PDPermutations.loadPrecalculatedPermutationsPUDO("D:\\projects\\dev\\slevels\\data\\permutations\\processed\\precalculated_pudo_permutations_requests=4_passengers=4.dat");

        //Load requests 5 minutes apart
        User u1 = new User(0,300, 600, 300, 1, 0, 1, 3, 2, 3, 2);
        User u2 = new User(0,300, 600, 300, 3, 0, 1, 3, 2, 3, 2);
        User u3 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
        User u4 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
        User u5 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);
        User u6 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);


        List<User> users = List.of(new User[]{u1, u2, u3, u4});
        requests = new HashSet<>(users);

        emptyVehicle = new Vehicle(4, 0, 0, false, 4000);

        fullVehicle = new Vehicle(4, 0, 0, false, 4000);
        //public Visit(Node[] sequencePD, int delay, int idleness, Vehicle vehicle, Set<User> requests) {
        Node[] sequence = new Node[]{u5.getNodeDp(), u6.getNodeDp()};
        fullVehicle.setVisit(new Visit(sequence, 2, 0, fullVehicle, requests));

    }


    @Test
    void getPDGenerator() {
        PDGeneratorFactory factory = new PDGeneratorFactory();
        PDGenerator genInsertion = factory.getPDGenerator(PDGeneratorFactory.PD_INSERTION);
        PDGenerator genPermutation = factory.getPDGenerator(PDGeneratorFactory.PD_PERMUTATION);

        genInsertion.start(requests, emptyVehicle);
        genPermutation.start(requests, emptyVehicle);

        Set<String> setInsertion = new HashSet<>();
        Set<String> setPermutation = new HashSet<>();


        Instant t1 = Instant.now();
        while(genInsertion.hasNext()){
            setInsertion.add(Arrays.toString(genInsertion.next()));
        }
        Instant t2 = Instant.now();

        Instant t3 = Instant.now();
        while(genPermutation.hasNext()){
            setPermutation.add(Arrays.toString(genPermutation.next()));
        }
        Instant t4 = Instant.now();

        Logging.logger.info("{}", String.format("  Insertion: %s\nPermutation: %s\n",(t2.getNano()-t1.getNano())/100000, (t4.getNano()-t3.getNano())/100000));
        assertEquals(setInsertion, setPermutation);

        setInsertion.forEach(s -> Logging.logger.info(s));

    }

    @Test
    void getPDGeneratorFullVehicleInsertion() {
        PDGeneratorFactory factory = new PDGeneratorFactory();
        PDGenerator genInsertion = factory.getPDGenerator(PDGeneratorFactory.PD_INSERTION);

        User u1 = new User(0,300, 600, 300, 1, 0, 1, 3, 2, 3, 2);
        User u2 = new User(0,300, 600, 300, 3, 0, 1, 3, 2, 3, 2);
        User u3 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);

        genInsertion.start(new HashSet<User>(List.of(new User[]{u1})), fullVehicle);

        Set<String> setInsertion = new HashSet<>();

        Instant t1 = Instant.now();
        while(genInsertion.hasNext()){
            setInsertion.add(Arrays.toString(genInsertion.next()));
        }
        Instant t2 = Instant.now();

        Logging.logger.info("{}", String.format("  Insertion: %s\n",(t2.getNano()-t1.getNano())/100000));

        setInsertion.forEach(s -> Logging.logger.info(s));

    }
}