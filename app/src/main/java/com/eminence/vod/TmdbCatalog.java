package com.eminence.vod;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class TmdbCatalog {
    private static final String API = "https://api.themoviedb.org/3/";
    private static final String IMAGES = "https://image.tmdb.org/t/p/";
    private static final int MAX_PAGE = 500;

    static Page browse(String type, String query, boolean newest, int page, String token)
            throws IOException, JSONException {
        boolean searching = !query.isEmpty();
        Uri.Builder url = Uri.parse(API + (searching ? "search/" : "discover/") + type)
                .buildUpon()
                .appendQueryParameter("language", "en-US")
                .appendQueryParameter("include_adult", "false")
                .appendQueryParameter("page", String.valueOf(page));
        if (searching) {
            url.appendQueryParameter("query", query);
        } else if (newest) {
            String releaseDate = type.equals("movie") ? "primary_release_date" : "first_air_date";
            SimpleDateFormat utcDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            utcDate.setTimeZone(TimeZone.getTimeZone("UTC"));
            url.appendQueryParameter("sort_by", releaseDate + ".desc");
            url.appendQueryParameter(releaseDate + ".lte", utcDate.format(new Date()));
        } else {
            url.appendQueryParameter("sort_by", "popularity.desc");
        }
        boolean apiKey = token.matches("[A-Fa-f0-9]{32}");
        if (apiKey) url.appendQueryParameter("api_key", token);
        HttpURLConnection connection = connect(url.build().toString());
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
                int totalPages = Math.min(MAX_PAGE, payload.getInt("total_pages"));
                return new Page(parse(type, payload.getJSONArray("results")), totalPages);
            }
        } finally {
            connection.disconnect();
        }
    }

    static List<Title> parse(String type, JSONArray results) {
        List<Title> titles = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null || item.optBoolean("adult")) continue;
            int id = item.optInt("id");
            String name = field(item, type.equals("movie") ? "title" : "name");
            if (id <= 0 || name.trim().isEmpty()) continue;
            String date = field(item, type.equals("movie") ? "release_date" : "first_air_date");
            titles.add(new Title(type, id, name, date.length() >= 4 ? date.substring(0, 4) : "",
                    field(item, "overview"), field(item, "poster_path")));
        }
        return titles;
    }

    private static String field(JSONObject object, String key) {
        return object.isNull(key) ? "" : object.optString(key, "");
    }

    static String imageUrl(String path) {
        if (!path.matches("/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp)")) return "";
        return IMAGES + "w342" + path;
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

    static final class Page {
        final List<Title> titles;
        final int totalPages;

        Page(List<Title> titles, int totalPages) {
            this.titles = titles;
            this.totalPages = totalPages;
        }
    }

    static final class Title {
        final String type;
        final int id;
        final String name;
        final String year;
        final String overview;
        final String poster;

        Title(String type, int id, String name, String year, String overview, String poster) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.year = year;
            this.overview = overview;
            this.poster = poster;
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
                    .put("year", year).put("overview", overview).put("poster", poster);
        }

        static Title fromJson(JSONObject object) throws JSONException {
            return new Title(object.getString("type"), object.getInt("id"), object.getString("name"),
                    object.getString("year"), object.getString("overview"), object.getString("poster"));
        }
    }
}
