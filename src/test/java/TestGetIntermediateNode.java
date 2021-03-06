import dao.Dao;
import dao.Logging;

import java.util.stream.Collectors;

public class TestGetIntermediateNode {

    public static Dao dao = Dao.getInstance();

    public static void testPair(int from, int to, int elapsed) {
        int intermediate = dao.getIntermediateNodeNetworkId(from, to, (short) elapsed);
        String sp = dao.getShortestPathBetween(from, to).stream().map(p -> String.format("%5d", p)).collect(Collectors.joining());
        Logging.logger.info("{}", String.format("Dist. %5d --> %5d/%5d --> %5d [%5d] (%s)", from, elapsed, dao.getDistSec(from, to), to, intermediate, sp));
    }

    public static void main(String[] a) {

        // Nodes and distances between 0 and 1
        //0   17   28   38   48   58   68   78   88   98  107  117  126  136  146  156  166  176  186  196  205  215  224  235  245  255  265  275  285  294  304  314  324  333  342  352  362  372  382  392  401  410  419  429  438  447  457  467  476  486  495  504  513  522  531  540  549  559  569  588  597  606  615  624  633  642  652  662  672  682  692  701  710  714  719  728  737  746  755  765  775  781  786  795  797  814  820  830  839  848  851  860  874  891  901  907  925  931  940
        //0 2505 1268 2094 2491 2081 2841 2430  961 1336 1834 2484 3010 1583 2480 2474 2990 2025 2457  988 2452 2443 2442 3694  379 1479 2437 1123 2434 3386 2432 2488 2427 2425 4234  587 2415 2406 2398 3466 2395 1604 2394 2392 2390  148 1441 2385 2384 2383 1469 1155 1976 2382 1981  840 1034 2376 3282 3283 3922 2637 3973 1499 2350 3970 2883 3483 1282 2136 3967 2598 3558  196 4260 4288 2570 2014 4332 2737 1682 4202 1678 1679 3416 3240 1676 2051 1675 1890   13 3322 1672 1666 4261 4267 2082 2080    1


        testPair(0, 1, 17);
        testPair(0, 2505, 17);
        testPair(0, 2505, 0);

        //[3011, 2983, 2973, 2978, 2968, 2954, 2950, 2952, 341, 2960, 2955, 960, 961, 1336, 1834, 2484, 3010, 1583, 2480, 2474, 2990, 2025, 2457, 988, 2452, 2443, 2442, 3694, 379, 1479, 2437, 1123, 2434, 3386, 2432, 2488, 2427, 2425, 4234, 587, 2415, 2406, 2398, 3466, 2395, 1604, 2394, 2392, 2390, 148, 1441, 2385, 2384, 2383, 1469, 1478]
        //[0, 10, 20, 30, 41, 50, 60, 70, 80, 90, 100, 103, 120, 130, 139, 149, 158, 168, 178, 188, 198, 208, 218, 228, 237, 247, 256, 267, 277, 287, 297, 307, 317, 326, 336, 346, 356, 365, 374, 384, 394, 404, 414, 424, 433, 442, 451, 461, 470, 479, 489, 499, 508, 518, 527, 546]

        testPair(787, 1586, 94);


        testPair(1400, 4357, 120);
        testPair(2611, 3336, 125);


        testPair(1051, 434, 108);

        testPair(2989, 434, 1);


        int intermediate = dao.getDistSec(3682, 4327);
        Logging.logger.info("THE FUCK:" + intermediate);
        /*
        TODO fi94x nodes with zero distance

        Reading distance data...
        174 - 3312 - 0 - 0.000000-32768
        2989 - 434 - 0 - 3.421629-32768
        3312 - 174 - 0 - 0.000000-32768

        Dist.  1051 -->   108/  117 -->   434 [ 2989] ( 1051 3071  815 2118 2119 2123 2125 2126 1159 3243 2128 1413 2133 2989  434)
        Dist.  2989 -->     1/    0 -->   434 [   -1] ( 2989  434)




        for (int i = 0; i <= 4470; i++) {

            for (int j = 0; j <= 4470; j++) {

                if(i == j) continue;
                try{


                if(dao.getDistSec(i,j)==0){
                    Logging.logger.info(i + " - " + j);
                }

                }catch(IndexOutOfBoundsException e){
                    continue;
                }

            }

        }
        */
    }
}
