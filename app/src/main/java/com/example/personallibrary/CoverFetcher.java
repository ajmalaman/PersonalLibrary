package com.example.personallibrary;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Looks up a book cover on Open Library (free, no API key).
 * Must be called from a background thread.
 */
public final class CoverFetcher {

    private CoverFetcher() {
    }

    /** Returns a cover image URL, or null if none was found / no internet. */
    public static String fetchCoverUrl(String title, String author) {
        String url = search("title=" + enc(title) + "&author=" + enc(author));
        if (url == null) {
            url = search("title=" + enc(title)); // fallback: title only
        }
        return url;
    }

    private static String search(String params) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://openlibrary.org/search.json?" + params
                    + "&limit=5&fields=cover_i");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "PersonalLibraryApp/1.0");

            if (conn.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JSONArray docs = new JSONObject(sb.toString()).optJSONArray("docs");
            if (docs == null) return null;

            for (int i = 0; i < docs.length(); i++) {
                int coverId = docs.getJSONObject(i).optInt("cover_i", 0);
                if (coverId > 0) {
                    return "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg";
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}