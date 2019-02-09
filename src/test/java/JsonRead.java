import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class JsonRead {
    public static void main(String a[]) {
        ObjectMapper mapper = new ObjectMapper();
        String jsonInput = "{\"key\": \"value\"}";
        TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {
        };
        try {
            //Path of the directory
            Path dirsPath = Paths.get("C:\\Users\\LocalAdmin\\IdeaProjects\\slevels\\src\\main\\resources\\instance_settings_test_rebalancing.json");

            String file = new FileReader("C:\\Users\\LocalAdmin\\IdeaProjects\\slevels\\src\\main\\resources\\instance_settings_test_rebalancing.json").toString();
            System.out.println(file);
            Map<String, String> map = mapper.readValue(jsonInput, typeRef);
            System.out.println(map);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
