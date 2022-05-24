package dao;

import model.Vehicle;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ServerUtilTest {

//    @Test
//    public void post_simple_json() {
//        String response = ServerUtil.postJsonAndReadResponse("{\"action\":\"hello\"}");
//        System.out.println(response);
//    }

    @Test
    public void getPredictions(){
        String filepath = "D:\\mnt\\d\\projects\\dev\\slevels\\src\\main\\resources\\day\\experiences_track\\0030_post_decisions_step=0030.json";
        List<Double> predictions; // ServerUtil.getPredictionsFromJsonFile(filepath);
        //System.out.println(predictions);
    }

    @Test
    public void postJsonFromFile() {
        String a = "D:\\projects\\dev\\slevels\\config.json";
//        String d = FileUtil.readJson(a);
        String serverURL = "http://localhost:5001/predict/";

//        String jsonInputString = "{\"name\" : \"Upendra\", \"job\": \"Programmer\"}";
        //String filepath = "D:\\projects\\dev\\slevels\\config.json";
        String filepath = a;

        String response = ServerUtil.postJsonFileToURL(filepath, serverURL);
        System.out.println(response);
    }

    @Test
    public void whenPostRequest_thenOk2() throws IOException {
        String serviceUrl = "http://localhost:5001/predict/";
        String jsonInputString = "{\"name\" : \"Upendra\", \"job\": \"Programmer\"}";
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serviceUrl))
                .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.statusCode());
            System.out.println(response.body());
//            assertThat(response.statusCode()).isEqualTo(200);
//            assertThat(response.body()).isEqualTo("{\"message\":\"ok\"}");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


    @Test
    public void whenPostRequest_thenOk() throws IOException {
        URL url = new URL("http://localhost:5001/predict/");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);

        String jsonInputString = "{\"name\" : \"Upendra\", \"job\": \"Programmer\"}";

        String a = "D:\\projects\\dev\\slevels\\config.json";
        String d = FileUtil.readJson(a);

        try (OutputStream os = con.getOutputStream()) {
//            byte[] input = d.getBytes("utf-8");
            byte[] input = Files.readAllBytes(Path.of(a));

            os.write(input, 0, input.length);
        }


        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response.toString());
        }
    }

    @Test
    void postJsonObjectToURL() {
        Vehicle a = new Vehicle(4);
        String r = ServerUtil.postJsonObjectToURL(a, "http://localhost:5001/predict/");
        System.out.println(r);
    }

//    @Test
//    void getPrediction() {
//
//
//        String filepath = "D:\\mnt\\d\\projects\\dev\\slevels\\src\\main\\resources\\day\\experiences_track\\0930_post_decisions_step=0030.json";
//        List<Double> list = ServerUtil.getPrediction("f");
//        System.out.println(list);
//
//        String query_url = "https://gurujsonrpc.appspot.com/guru";
//        String json = "{ \"method\" : \"guru.test\", \"params\" : [ \"jinu awad\" ], \"id\" : 123 }";
//        try {
//            URL url = new URL(query_url);
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setConnectTimeout(5000);
//            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
//            conn.setDoOutput(true);
//            conn.setDoInput(true);
//            conn.setRequestMethod("POST");
//            OutputStream os = conn.getOutputStream();
//            os.write(json.getBytes("UTF-8"));
//            os.close();
//            // read the response
//            InputStream in = new BufferedInputStream(conn.getInputStream());
//            System.out.println(in);
////            String result = IOUtils.toString(in, "UTF-8");
////            System.out.println(result);
////            System.out.println("result after Reading JSON Response");
////            JSONObject myResponse = new Gson(result);
////            System.out.println("jsonrpc- "+myResponse.getString("jsonrpc"));
////            System.out.println("id- "+myResponse.getInt("id"));
////            System.out.println("result- "+myResponse.getString("result"));
//            in.close();
//            conn.disconnect();
//        } catch (Exception e) {
//            System.out.println(e);
//    }
}