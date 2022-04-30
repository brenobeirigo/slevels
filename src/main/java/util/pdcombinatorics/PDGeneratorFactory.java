package util.pdcombinatorics;

import util.pdcombinatorics.PDGenerator;
import util.pdcombinatorics.PDGeneratorInsertion;
import util.pdcombinatorics.PDPermutations;

public class PDGeneratorFactory {

    public static final String PD_PERMUTATION = "pd_permutation";
    public static final String PD_INSERTION =  "pd_insertion";

    public PDGeneratorFactory(){

    }
    public PDGenerator getPDGenerator(String PDStrategyLabel) {
        switch (PDStrategyLabel){
            case PD_INSERTION: return new PDGeneratorInsertion();
            case PD_PERMUTATION:return new PDPermutations();
            default: return new PDGeneratorInsertion();
        }
    }
}
