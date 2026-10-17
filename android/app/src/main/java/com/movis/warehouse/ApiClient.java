package com.movis.warehouse;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** One trusted service; redirects must never forward a user's session elsewhere. */
final class ApiClient {
    static final String SERVER = "https://movisapp.onrender.com";
    private static final int MAX_RESPONSE = 64 * 1024 * 1024;

    static final class ApiError extends IOException {
        final int code;
        ApiError(int code, String message) { super(message); this.code = code; }
    }

    static JSONObject request(String path, JSONObject data, String token) throws Exception {
        return request(path,data,token,"");
    }

    static JSONObject request(String path, JSONObject data, String token, String knownImages) throws Exception {
        if (!path.startsWith("/") || path.startsWith("//")) throw new IOException("Invalid request path");
        HttpURLConnection c = (HttpURLConnection) new URL(SERVER + path).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(path.equals("/logout") ? 5000 : 20000);
        // Allow the free server to finish its first YOLO request after waking.
        c.setReadTimeout(path.equals("/logout") ? 5000 : 180000);
        c.setRequestProperty("Accept", "application/json");
        if(path.startsWith("/v2/")){
            if(data!=null)c.setRequestProperty("X-Movis-State", "action");
            if(!knownImages.isEmpty())c.setRequestProperty("X-Movis-Known-Images",knownImages);
        }
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (data != null) {
                byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            }
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) throw new IOException("The service address changed. Contact your administrator.");
            InputStream source = code < 400 ? c.getInputStream() : c.getErrorStream();
            if (source == null) throw new IOException("Service unavailable. Try again shortly.");
            JSONObject response;
            try (InputStream in = source; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (out.size() + count > MAX_RESPONSE) throw new IOException("This task is too large to display. Contact your administrator.");
                    out.write(buffer, 0, count);
                }
                try { response = new JSONObject(out.toString("UTF-8")); }
                catch (JSONException e) { throw new IOException("The service is starting or unavailable. Try again shortly."); }
            }
            if (code >= 400) throw new ApiError(code, response.optString("error", "Request could not be completed."));
            return response;
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timed out. Refresh this task before retrying a submission.");
        } catch (UnknownHostException | ConnectException e) {
            throw new IOException("Unable to connect. Check your internet connection and try again.");
        } finally { c.disconnect(); }
    }
}
