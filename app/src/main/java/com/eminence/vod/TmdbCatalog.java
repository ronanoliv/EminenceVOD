package com.eminence.vod;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class TmdbCatalog {
    private static final String API = "https://api.themoviedb.org/3/";
    private static final String IMAGES = "https://image.tmdb.org/t/p/";
    private static final int ROW_LIMIT = 16;

    static List<Title> trending(String type, String token) throws IOException, JSONException {
        boolean apiKey = token.matches("[A-Fa-f0-9]{32}");
        String url = API + "trending/" + type + "/week?language=en-US";
        if (apiKey) url += "&api_key=" + token;
        HttpURLConnection connection = connect(url);
        if (!apiKey) connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new InvalidToken();
            if (status != 200) throw new IOException("TMDB returned HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                JSONObject payload = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                return parse(type, payload.getJSONArray("results"));
            }
        } finally {
            connection.disconnect();
        }
    }

    static List<Title> parse(String type, JSONArray results) {
        List<Title> titles = new ArrayList<>();
        for (int i = 0; i < results.length() && titles.size() < ROW_LIMIT; i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null || item.optBoolean("adult")) continue;
            int id = item.optInt("id");
            String name = field(item, type.equals("movie") ? "title" : "name");
            if (id <= 0 || name.trim().isEmpty()) continue;
            String date = field(item, type.equals("movie") ? "release_date" : "first_air_date");
            titles.add(new Title(type, id, name, date.length() >= 4 ? date.substring(0, 4) : "",
                    field(item, "overview"), field(item, "poster_path"), field(item, "backdrop_path")));
        }
        return titles;
    }

    private static String field(JSONObject object, String key) {
        return object.isNull(key) ? "" : object.optString(key, "");
    }

    static String imageUrl(String path, boolean backdrop) {
        if (!path.matches("/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp)")) return "";
        return IMAGES + (backdrop ? "w780" : "w342") + path;
    }

    static Bitmap image(String url) throws IOException {
        HttpURLConnection connection = connect(url);
        try (InputStream input = connection.getInputStream()) {
            return BitmapFactory.decodeStream(input);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection connect(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    static final class InvalidToken extends IOException {}

    static final class Title {
        final String type;
        final int id;
        final String name;
        final String year;
        final String overview;
        final String poster;
        final String backdrop;

        Title(String type, int id, String name, String year, String overview, String poster, String backdrop) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.year = year;
            this.overview = overview;
            this.poster = poster;
            this.backdrop = backdrop;
        }

        String key() {
            return type + ':' + id;
        }

        String label() {
            String kind = type.equals("movie") ? "Movie" : "TV show";
            return year.isEmpty() ? kind : kind + "  ·  " + year;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("type", type).put("id", id).put("name", name)
                    .put("year", year).put("overview", overview).put("poster", poster).put("backdrop", backdrop);
        }

        static Title fromJson(JSONObject object) throws JSONException {
            return new Title(object.getString("type"), object.getInt("id"), object.getString("name"),
                    object.getString("year"), object.getString("overview"), object.getString("poster"),
                    object.getString("backdrop"));
        }
    }
}
