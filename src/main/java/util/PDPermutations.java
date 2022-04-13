package util;

import config.Config;
import config.CustomerBaseConfig;
import config.InstanceConfig;
import model.Vehicle;
import model.Visit;
import model.node.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import model.User;

public class PDPermutations implements Iterator<Node[]> {

    private Iterator<int[]> configIndexPermutationIterator;
    private Node[] basePUDOVector;
    private static Map<Integer, Map<Integer, int[][]>>  mapPUDO;

    public static int[][] getPUDOPermutations(int nOfPUDOs, int nOfDOs){
        int[][] permutations = mapPUDO.get(nOfPUDOs).get(nOfDOs);
        return permutations;
    }

    public static void loadPrecalculatedPermutationsPUDO(String pathPrecalculatedPUDOPermutations) {

        Path path = Paths.get(pathPrecalculatedPUDOPermutations);
        BufferedReader reader = null;
        mapPUDO = new HashMap<>();

        try {
            reader = Files.newBufferedReader(path);

            System.out.print("#PUDOs  #DOs    #Permut.\n");

            String strCurrentLine;
            while ((strCurrentLine = reader.readLine()) != null) {
                int[] infoPerms = Arrays.stream(strCurrentLine.split(" ")).mapToInt(Integer::parseInt).toArray();
                int nRequests = infoPerms[0];
                int nDropoffs = infoPerms[1];
                int nPermutations = infoPerms[2];

                mapPUDO.putIfAbsent(nRequests, new HashMap<>());
                mapPUDO.get(nRequests).put(nDropoffs, new int[nPermutations][]);

                System.out.printf("%7d %7d %12d\n", nRequests, nDropoffs, nPermutations);
                for (int i = 0; i < nPermutations; i++) {
                    strCurrentLine = reader.readLine();
                    if (!strCurrentLine.equals("")){
                        int[] permutations = Arrays.stream(strCurrentLine.split(" ")).mapToInt(Integer::parseInt).toArray();
                        mapPUDO.get(nRequests).get(nDropoffs)[i] = permutations;
                    }else{
                        mapPUDO.get(nRequests).get(nDropoffs)[i] = null;
                    }

                    //System.out.println(Arrays.toString(mapPUDO.get(nRequests).get(nDropoffs)[i]));
                }

                reader.readLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasNext() {
        return (this.configIndexPermutationIterator.hasNext());
    }

    @Override
    public Node[] next() {
        Node[] currentPUDOPermutation = null;
        if (this.hasNext()){
            currentPUDOPermutation = loadNextPUDOPermutation(this.configIndexPermutationIterator.next(), this.basePUDOVector);
        }
        return currentPUDOPermutation;
    }


    private void loadAllPUDOPermutationsFrom(Set<User> requests, Set<User> passengers) {

        // [PU_1, PU_2, ..., PU_n, DO_n, DO_(n-1), ..., DO_1, DO_(n+1), DO_(n+2),..., DO_(nOfPassengers)]
        basePUDOVector = getSeedSequence(requests, passengers);

        // Precalculated valid permutations
        int[][] validIndexConfigPermutations = getPUDOPermutations(requests.size(), passengers.size());

        configIndexPermutationIterator = Arrays.stream(validIndexConfigPermutations).iterator();

        // System.out.println("BasePUDOVector: " + Arrays.toString(basePUDOVector));

    }

    private void loadAllPUDOPermutationsFrom(Set<User> requests, Vehicle vehicle) {

        // Create seed sequence using PuDo's from requests and Do's from passengers
        Set<User> passengers = getPassengersFromVehicle(vehicle);

        loadAllPUDOPermutationsFrom(requests, passengers);
    }

    public PDPermutations(Set<User> requests, Vehicle vehicle) {
        loadAllPUDOPermutationsFrom(requests, vehicle);
    }

    private static Node[] loadNextPUDOPermutation(int[] configIndexPermutation, Node[] basePUDOVector) {
        // Empty sequence to place permutations according to base
        Node[] currentPUDOPermutation = new Node[configIndexPermutation.length];
        for (int j = 0; j < configIndexPermutation.length; j++) {
            int p = configIndexPermutation[j];
            currentPUDOPermutation[j] = basePUDOVector[p];
        }
        return currentPUDOPermutation;
    }

    private static Set<User> getPassengersFromVehicle(Vehicle vehicle) {
        // Passengers have been picked up
        Set<User> passengers = new HashSet<>();
        if (vehicle.isServicing()) {
            passengers = vehicle.getVisit().getPassengers();
        }
        return passengers;
    }

    /**
     * Prepare seed sequence to create permutations.
     * For $n$ requests and $p$ passengers, the seed sequence follows the form:
     * [PU_1, PU_2, ..., PU_n, DO_n, DO_(n-1), ..., DO_1, DO_(n+1), DO_(n+2),..., DO_m]
     *
     * @param requests
     * @param passengers
     * @return
     */
    private Node[] getSeedSequence(Set<User> requests, Set<User> passengers) {
        int nPassengers = passengers.size();
        int nRequests = requests.size();
        Node[] sequence = new Node[2 * nRequests + nPassengers];

        int i = 0;
        for (User user : requests) {
            sequence[i] = user.getNodePk();
            sequence[nRequests + i] = user.getNodeDp();
            i++;
        }

        int j = 0;
        for (User passenger : passengers) {
            sequence[2 * nRequests + j] = passenger.getNodeDp();
            j++;
        }
        return sequence;
    }

    public static void main(String[] str) {


        String s = "D:\\projects\\dev\\slevels\\config.json";
        InstanceConfig instanceSettings;
        try {
            instanceSettings = Config.createInstanceFrom(s);

            Date earliestTime = Config.formatter_date_time.parse("2011-02-12 00:00:00");
            Config.getInstance().setEarliestTime(earliestTime);
            CustomerBaseConfig customerBaseSettings = instanceSettings.getCustomerBaseSettingsArray().get(0);
            Config.getInstance().updateQosDic(customerBaseSettings.qosDic);


            System.out.println(earliestTime);
            //Load requests 5 minutes apart
            User u1 = new User("2011-02-12 00:00:00", 1, 0, 1, 3, 2, 3, 2);
            User u5 = new User("2011-02-12 00:00:00", 1, 0, 1, 3, 2, 3, 2);
            User u2 = new User("2011-02-12 00:05:00", 1, 2, 3, 3, 2, 3, 2);
            User u3 = new User("2011-02-12 00:10:00", 1, 3, 4, 3, 2, 3, 2);
            User u4 = new User("2011-02-12 00:15:00", 1, 4, 5, 3, 2, 3, 2);

            Set<User> requests = new HashSet<>();
            //requests.add(u1);
            //requests.add(u2);
            //requests.add(u5);




            Set<User> passengers = new HashSet<>();
            passengers.add(u3);
            passengers.add(u4);

            Vehicle v = new Vehicle(4,0,0,false, 4000);
            Visit v1 = new Visit();
            v1.setPassengers(passengers);
            // v1.setRequests(requests);

            v.setVisit(v1);

            PDPermutations perms = new PDPermutations(requests, v);
            while (perms.hasNext()) {
                System.out.println(Arrays.toString(perms.next()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
