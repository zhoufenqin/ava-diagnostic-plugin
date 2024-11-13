package com.microsoft.azure.agent.plugin.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.agent.plugin.agent.UrlConfig;
import com.microsoft.azure.agent.plugin.agent.entity.PodInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KubernetesService {

    public volatile static String defaultPodName = null;
    public volatile static String defaultContainerName = null;
    public volatile static String defaultNamespace = "default";


    public static List<String> listNamespaces() {
        try {
            Pair<Integer, String> response = callGetUrl(UrlConfig.getNamespacesUrl());
            if (response.getKey() == HttpStatus.SC_OK) {
                ObjectMapper objectMapper = new ObjectMapper();
                // Parse the JSON array string into a List<String>
                return objectMapper.readValue(response.getValue().trim(), List.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
        return List.of();
    }

    public static List<PodInfo> getAllPods() throws Exception {

        try {
            Pair<Integer, String> response = callGetUrl(UrlConfig.getPodsUrl() + "?namespace=" + defaultNamespace);
            if (response.getKey() == HttpStatus.SC_OK) {
                ObjectMapper objectMapper = new ObjectMapper();
                // Parse the JSON array string into a List<String>
                List<String> podStrings = objectMapper.readValue(response.getValue().trim(), List.class);
                // Convert each string into a PodInfo object
                List<PodInfo> pods = new ArrayList<>();
                for (String podString : podStrings) {
                    String[] parts = podString.split(" ");
                    if (parts.length >= 4) {
                        String name = parts[0];    // Pod name
                        String ip = parts[1];      // Pod IP
                        String status = parts[2];  // Pod status
                        boolean isAttach = Boolean.parseBoolean(parts[3]);
                        pods.add(new PodInfo(name, ip, status, isAttach));
                    }
                }
                return pods;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static List<String> getContainerNames(String podName) {
        String url = UrlConfig.getContainersUrl() +"?podName=" + podName + "&namespace=" + defaultNamespace;
        try {
            Pair<Integer, String> response = callGetUrl(url);
            if (response.getKey() == HttpStatus.SC_OK) {
                ObjectMapper objectMapper = new ObjectMapper();
                // Parse the JSON array string into a List<String>
                return objectMapper.readValue(response.getValue().trim(), List.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("failed to get containers");
        }
        return List.of();
    }

    public static boolean attachAgent(String podName, String containerName) throws Exception {
        String requestBody = "{"
                + "\"podName\": \"" + podName + "\","
                + "\"containerName\": \"" + containerName + "\","
                + "\"namespace\": \"" + defaultNamespace + "\""
                + "}";

        // sometime, it need to pull diagnostic agent image first
        Pair<Integer, String> response = callPostUrl(UrlConfig.getAttachUrl(), requestBody, 20000);
        if (response.getKey() == HttpStatus.SC_OK) {
            return true;
        }
        throw new RuntimeException(response.getValue());
    }

    public static boolean addLog(String podName, String containerName, String className, String methodName) {
        String requestBody = "{"
                + "\"podName\": \"" + podName + "\","
                + "\"containerName\": \"" + containerName + "\","
                + "\"namespace\": \"" + defaultNamespace + "\","
                + "\"className\": \"" + className + "\","
                + "\"methodName\": \"" + methodName + "\""
                + "}";
        try {

        } catch (Exception e) {
            throw new RuntimeException("Error occurred while adding around log: " + e.getMessage());
        }
        Pair<Integer, String> response = callPostUrl(UrlConfig.getAddLogsUrl(), requestBody, 5000);
        if (response.getKey() == HttpStatus.SC_OK) {
            return true;
        }
        throw new RuntimeException(response.getValue());
    }


    public static boolean removeLog(String podName, String containerName) {
        String requestBody = "{"
                + "\"podName\": \"" + podName + "\","
                + "\"containerName\": \"" + containerName + "\","
                + "\"namespace\": \"" + defaultNamespace + "\","
                + "\"deleteAll\": " + true
                + "}";
        Pair<Integer, String> response = callPostUrl(UrlConfig.getRemoveLogsUrl(), requestBody, 5000);
        if (response.getKey() == HttpStatus.SC_OK) {
            return true;
        }
        throw new RuntimeException(response.getValue());
    }

    private static Pair<Integer, String> callGetUrl(String url) {
        try {
            java.net.URL obj = new java.net.URL(url);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) obj.openConnection();
            con.setReadTimeout(3000);
            con.setConnectTimeout(3000);
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            // Determine the correct stream to read based on response code
            BufferedReader in;
            if (responseCode >= 200 && responseCode < 300) {
                in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            } else {
                in = new BufferedReader(new InputStreamReader(con.getErrorStream()));
            }

            // Read the response message
            String inputLine;
            StringBuilder responseMessage = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                responseMessage.append(inputLine).append("\n");
            }
            in.close();

            return Pair.of(responseCode, responseMessage.toString());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException( "Error occurred: " + e.getMessage());
        }
    }


    private static Pair<Integer, String> callPostUrl(String url, String requestBody, int timeout) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // Set the request method to POST
            con.setRequestMethod("POST");
            con.setDoOutput(true); // Allow sending data in the body
            con.setRequestProperty("Content-Type", "application/json"); // Set the content type to JSON
            con.setReadTimeout(timeout);
            con.setConnectTimeout(timeout);

            // Send the request data
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Get the response code and handle the response
            int responseCode = con.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Check if the response code is 2xx (success)
            if (responseCode >= 200 && responseCode < 300) {
                // Read the response body
                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    StringBuilder response = new StringBuilder();
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    return Pair.of(responseCode, response.toString());
                }
            } else {
                // Read the error response body (4xx, 5xx)
                try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {
                    String responseLine;
                    StringBuilder response = new StringBuilder();
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    // Parse the error response JSON
                    ObjectMapper objectMapper = new ObjectMapper();
                    Map<String, Object> errorMap = objectMapper.readValue(response.toString(), Map.class);
                    String message = errorMap.getOrDefault("message", "Unknown error").toString();
                    return Pair.of(responseCode, message);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException( "Error occurred: " + e.getMessage());
        }
    }
}