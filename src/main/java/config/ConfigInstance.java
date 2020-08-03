package config;

import java.util.List;
import java.util.Map;

public class ConfigInstance {

    private String instance_file_path;
    private String info_level;
    private List<String> info_level_options;
    private Map<String, Boolean> info_handling;

    public void setInstanceFilePath(String instanceFilePath) {
        this.instance_file_path = instanceFilePath;
    }

    public void setInfoLevel(String infoLevel) {
        this.info_level = infoLevel;
    }

    public void setInfoLevelOptions(List<String> infoLevelOptions) {
        this.info_level_options = infoLevelOptions;
    }

    public void setInfoHandling(Map<String, Boolean> infoHandling) {
        this.info_handling = infoHandling;
    }

    public String getInstanceFilePath() {
        return instance_file_path;
    }

    public String getInfoLevel() {
        return info_level;
    }

    public List<String> getInfoLevelOptions() {
        return info_level_options;
    }

    public Map<String, Boolean> getInfoHandling() {
        return info_handling;
    }
}