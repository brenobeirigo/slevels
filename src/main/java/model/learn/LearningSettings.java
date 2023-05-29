package model.learn;

import java.util.Date;

public class LearningSettings {
    public LearningConfig learningConfig;
    public int requestSamples;
    public int sizeExperienceReplayBatch;
    public int sizeExperienceReplayBuffer;
    public Date episodeStartDatetimeTraining;
    private Date currentEpisodeStartDatetimeTesting;

    public int targetNetworkUpdateFrequency;
    public int trainingFrequency;
    public Double discountFactor;
    public Double learningRate;
    public String folderModels;
    public String modelInstanceLabel;
    public String experiencesFolder;

    public String getLabel() {
        return String.format("RS-%s_BA-%s_BU-%s_TAR-%s_FR-%s", requestSamples, sizeExperienceReplayBatch, sizeExperienceReplayBuffer, targetNetworkUpdateFrequency, trainingFrequency);
    }

    public String getModelFilePath(String modelInstanceLabel) {
        return String.format("%s/%s/model_%s.h5", folderModels, modelInstanceLabel, getLabel());
    }

    public String getExperiencesFolder() {
        String folder = String.format("%s/%s/experiences_%s", this.folderModels, this.learningConfig.modelInstanceLabel, this.getLabel());
        return folder;
    }

    @Override
    public String toString() {
        return "LearningSettings{" +
                "requestSamples=" + requestSamples +
                ", sizeExperienceReplayBatch=" + sizeExperienceReplayBatch +
                ", sizeExperienceReplayBuffer=" + sizeExperienceReplayBuffer +
                ", episodeStartDatetimeTraining=" + episodeStartDatetimeTraining +
                ", currentEpisodeStartDatetimeTesting=" + currentEpisodeStartDatetimeTesting +
                ", targetNetworkUpdateFrequency=" + targetNetworkUpdateFrequency +
                ", trainingFrequency=" + trainingFrequency +
                ", discountFactor=" + discountFactor +
                ", learningRate=" + learningRate +
                ", folderModels='" + folderModels + '\'' +
                ", modelInstanceLabel='" + modelInstanceLabel + '\'' +
                ", experiencesFolder='" + experiencesFolder + '\'' +
                '}';
    }

    public LearningSettings(int currentRequestSamples, int currentSizeExperienceReplayBatch,
                            int currentSizeExperienceReplayBuffer, Date currentEpisodeStartDatetime,
                            int currentTargetNetworkUpdateFrequency, int currentTrainingFrequency,
                            Double currentDiscountFactor, Double currentLearningRate, String folderModels,
                            String modelInstanceLabel, String experiencesFolder, LearningConfig learningConfig) {
        this.learningConfig = learningConfig;
        this.requestSamples = currentRequestSamples;
        this.sizeExperienceReplayBatch = currentSizeExperienceReplayBatch;
        this.sizeExperienceReplayBuffer = currentSizeExperienceReplayBuffer;
        this.episodeStartDatetimeTraining = currentEpisodeStartDatetime;
        this.targetNetworkUpdateFrequency = currentTargetNetworkUpdateFrequency;
        this.trainingFrequency = currentTrainingFrequency;
        this.discountFactor = currentDiscountFactor;
        this.learningRate = currentLearningRate;
        this.folderModels = folderModels;
        this.modelInstanceLabel = modelInstanceLabel;
        this.experiencesFolder = experiencesFolder;
    }
}
