package model.learn;

import simulation.Environment;

import java.util.*;

public class LearningConfig implements Iterator<LearningSettings> {
    public String[] arrayFilepathTrainingData;
    public String[] arrayFilepathTestingData;
    public int currentRequestSamples;
    public int currentSizeExperienceReplayBatch;
    public int currentSizeExperienceReplayBuffer;
    public Date currentEpisodeStartDatetimeTraining;
    public int currentTargetNetworkUpdateFrequency;
    public int currentTrainingFrequency;
    public Double currentDiscountFactor;
    public Double currentLearningRate;
    public String folderModels;
    public String modelInstanceLabel;
    public String experiencesFolder;
    private Date currentStartDatetimeTesting;
    private Date episodeStartDatetimeTesting;

    public List<Date> episodeStartDatetimeListTest;
    public List<Date> episodeStartDatetimeList;
    public double[] discountFactorArray;
    public double[] learningRateArray;
    public int[] requestSamples;
    public int[] sizeExperienceReplayBatch;
    public int[] sizeExperienceReplayBuffer;
    public int[] targetNetworkUpdateFrequency;
    public int[] trainingFrequencyArray;
    private int[] idx;
    private LinkedList<LearningSettings> a;

    public LearningSettings top() {
        return a.get(0);
    }

    public Iterator<Double> discountFactorIt;
    public Iterator<Integer> targetNetworkUpdateFrequencyIt;
    public Iterator<Integer> trainingFrequencyIt;
    public Iterator<Double> learningRateIt;
    public Iterator<Date> episodeStartDatetimeTrainingIt;
    public Iterator<Date                    > episodeStartDatetimeTestingIt;
    public Iterator<Integer> sizeExperienceReplayBufferIt;
    public Iterator<Integer> sizeExperienceReplayBatchIt;
    public Iterator<Integer> requestSamplesIt;


    public LearningConfig(int[] requestSamples, int[] sizeExperienceReplayBatch, int[] sizeExperienceReplayBuffer
            , String folderModels, List<Date> episodeStartTimestamps, List<Date> episodeStartDatetimeArrayTest, int[] targetNetworkUpdateFrequency,
                          int[] trainingFrequency, double[] learningRateArray, double[] discountFactorArray, String[] arrayFilepathTrainingData, String[] arrayFilepathTestingData) {

        this.arrayFilepathTrainingData = arrayFilepathTrainingData;
        this.arrayFilepathTestingData = arrayFilepathTestingData;
        this.folderModels = folderModels;
        this.modelInstanceLabel = getModelInstanceLabel();

        this.requestSamples = requestSamples;
        this.requestSamplesIt = Arrays.stream(this.requestSamples).iterator();
        this.currentRequestSamples = this.requestSamplesIt.next();


        this.sizeExperienceReplayBatch = sizeExperienceReplayBatch;
        this.sizeExperienceReplayBatchIt = Arrays.stream(this.sizeExperienceReplayBatch).iterator();
        this.currentSizeExperienceReplayBatch = this.sizeExperienceReplayBatchIt.next();


        this.sizeExperienceReplayBuffer = sizeExperienceReplayBuffer;
        this.sizeExperienceReplayBufferIt = Arrays.stream(this.sizeExperienceReplayBuffer).iterator();
        this.currentSizeExperienceReplayBuffer = this.sizeExperienceReplayBufferIt.next();

        this.episodeStartDatetimeList = episodeStartTimestamps;
        this.episodeStartDatetimeListTest = episodeStartDatetimeArrayTest;
//            this.episodeStartDatetimeTrainingIt      = this.episodeStartDatetimeList.iterator();
//            this.currentEpisodeStartDatetimeTraining = this.episodeStartDatetimeTrainingIt.next();

        this.targetNetworkUpdateFrequency = targetNetworkUpdateFrequency;
        this.targetNetworkUpdateFrequencyIt = Arrays.stream(this.targetNetworkUpdateFrequency).iterator();
        this.currentTargetNetworkUpdateFrequency = this.targetNetworkUpdateFrequencyIt.next();

        this.trainingFrequencyArray = trainingFrequency;
        this.trainingFrequencyIt = Arrays.stream(this.trainingFrequencyArray).iterator();
        //this.currentTrainingFrequency = trainingFrequencyIt.next();

        this.discountFactorArray = discountFactorArray;
        this.discountFactorIt = Arrays.stream(this.discountFactorArray).iterator();
        this.currentDiscountFactor = discountFactorIt.next();

        this.learningRateArray = learningRateArray;
        this.learningRateIt = Arrays.stream(this.learningRateArray).iterator();
        this.currentLearningRate = learningRateIt.next();


        this.a = new LinkedList<LearningSettings>();

        for (int j = 0; j < this.targetNetworkUpdateFrequency.length; j++) {
            for (int k = 0; k < this.requestSamples.length; k++) {
                for (int l = 0; l < this.sizeExperienceReplayBuffer.length; l++) {
                    for (int m = 0; m < this.sizeExperienceReplayBatch.length; m++) {
                        for (int n = 0; n < this.trainingFrequencyArray.length; n++) {
                            currentRequestSamples = this.requestSamples[k];
                            currentTargetNetworkUpdateFrequency = this.targetNetworkUpdateFrequency[j];
                            currentSizeExperienceReplayBatch = this.sizeExperienceReplayBatch[m];
                            currentSizeExperienceReplayBuffer = this.sizeExperienceReplayBuffer[l];
                            currentTrainingFrequency = this.trainingFrequencyArray[n];

                            LearningSettings s = new LearningSettings(currentRequestSamples, currentSizeExperienceReplayBatch,
                                    currentSizeExperienceReplayBuffer, currentEpisodeStartDatetimeTraining,
                                    currentTargetNetworkUpdateFrequency, currentTrainingFrequency,
                                    currentDiscountFactor, currentLearningRate, folderModels,
                                    modelInstanceLabel, experiencesFolder, this);

                            a.add(s);


                        }

                    }

                }

            }

        }
    }


    public void setModelInstanceLabel(String modelInstanceLabel) {
        this.modelInstanceLabel = modelInstanceLabel;
    }

    public String getModelInstanceLabel() {
        return modelInstanceLabel;
    }


    public String getExperiencesFolder() {
        String folder = String.format("%s/%s/experiences", this.folderModels, this.modelInstanceLabel);
        return folder;
    }

//    public static boolean isNotTerminal(int timeStep) {
//        // TODO WARNING
//        return timeStep < Environment.timeHorizon;
//    }

    @Override
    public boolean hasNext() {
        return a.size() > 0;
    }


    @Override
    public LearningSettings next() {
        return a.pop();
    }
}
