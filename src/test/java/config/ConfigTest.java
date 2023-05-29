package config;

import org.junit.jupiter.api.Test;


class ConfigTest {

    @Test
    public void testReadConfigFile() {
        String source = "instance_config.json";
        InstanceData a = new InstanceData(source);
        System.out.println(a.getLabel());
    }


}