package com.videoflow.control;

import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class ApiClient {
    static final class Pairing {
        final String baseUrl;
        final String token;
        final String originalUrl;

        Pairing(String baseUrl, String token, String originalUrl) {
            this.baseUrl = baseUrl;
            this.token = token;
            this.originalUrl = originalUrl;
        }
    }

    private ApiClient() {}

    static Pairing parsePairingUrl(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) return null;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) || uri.getHost() == null) return null;
        String token = uri.getQueryParameter("access");
        if (token == null || token.length() < 24) return null;
        String authority = uri.getEncodedAuthority();
        String path = uri.getEncodedPath();
        if (path == null || "/".equals(path)) path = "";
        else while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return new Pairing(scheme.toLowerCase() + "://" + authority + path, token, value);
    }

    static JSONObject getState(String baseUrl, String token) throws Exception {
        return request(baseUrl, token, "/api/state", "GET", null);
    }

    static JSONObject post(String baseUrl, String token, String path) throws Exception {
        return request(baseUrl, token, path, "POST", "{}");
    }

    private static JSONObject request(String baseUrl, String token, String path, String method, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(18_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("X-Video-Flow-Token", token);
        connection.setUseCaches(false);
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = readAll(input);
        connection.disconnect();
        if (status >= 400) {
            String message = response;
            try { message = new JSONObject(response).optString("error", response); } catch (Exception ignored) {}
            throw new Exception("HTTP " + status + " · " + message);
        }
        return new JSONObject(response);
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
