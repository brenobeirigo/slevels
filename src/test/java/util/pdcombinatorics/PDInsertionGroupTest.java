package util.pdcombinatorics;

import model.User;
import model.Vehicle;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class PDInsertionGroupTest {

    @Test
    public void testEmptyVehicle() {


        //Load requests 5 minutes apart
        User u1 = new User(0,300, 600, 300, 1, 0, 1, 3, 2, 3, 2);
        User u2 = new User(0,300, 600, 300, 3, 0, 1, 3, 2, 3, 2);
        User u3 = new User(0,300, 600, 300, 2, 0, 1, 3, 2, 3, 2);

        Vehicle v1 = new Vehicle(4, 0, 0, false, 4000);
        Vehicle v2 = new Vehicle(4, 1, 0, false, 4000);
        Vehicle v3 = new Vehicle(4, 2, 0, false, 4000);

        PDGeneratorInsertion pd3 = new PDGeneratorInsertion(new HashSet<>(List.of(new User[]{u1, u2, u3})), v1);
        while(pd3.hasNext()){
            System.out.println(Arrays.asList(pd3.next()));
        }

        PDGeneratorInsertion pd2 = new PDGeneratorInsertion(new HashSet<>(List.of(new User[]{u1, u2})), v1);
        while(pd2.hasNext()){
            System.out.println(Arrays.asList(pd2.next()));
        }

        PDGeneratorInsertion pd1 = new PDGeneratorInsertion(new HashSet<>(List.of(new User[]{u1})), v1);
        while(pd1.hasNext()){
            System.out.println(Arrays.asList(pd1.next()));
        }

        PDGeneratorInsertion pd0 = new PDGeneratorInsertion(new HashSet<>(List.of(new User[]{})), v1);
        while(pd0.hasNext()){
            System.out.println(Arrays.asList(pd0.next()));
        }


    }

}