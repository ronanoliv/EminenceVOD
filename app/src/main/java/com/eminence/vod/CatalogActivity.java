package com.eminence.vod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.eminence.vod.TmdbCatalog.Title;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class CatalogActivity extends Activity {
    private static final int INK = 0xff0c0c0e;
    private static final int PANEL = 0xff1a1a1e;
    private static final int MUTED = 0xffb9b7b4;
    private static final int GOLD = 0xffe6b95d;
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private static final String TOKEN = "tmdb_credential";
    private static final String WATCHLIST = "my_list";

    private final ExecutorService requests = Executors.newSingleThreadExecutor();
    private final ExecutorService images = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> artwork = new LruCache<String, Bitmap>(20 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };
    private final LinkedHashMap<String, Title> saved = new LinkedHashMap<>();
    private SharedPreferences preferences;
    private Future<?> refresh;
    private int loadVersion;
    private LinearLayout page;
    private LinearLayout listSection;
    private Button duloButton;
    private Button catalogButton;
    private FrameLayout hero;
    private LinearLayout catalogRows;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("eminence_catalog", MODE_PRIVATE);
        readList();
        createPage();
        loadCatalog();
    }

    private void createPage() {
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(INK);
        page = column();
        page.setPadding(dp(38), dp(18), dp(38), dp(30));
        scroll.addView(page, new ScrollView.LayoutParams(MATCH, WRAP));
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.eminence_icon);
        header.addView(logo, new LinearLayout.LayoutParams(dp(45), dp(45)));
        TextView title = text(getString(R.string.app_name), 22, GOLD, true);
        title.setPadding(dp(12), 0, dp(12), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1));
        duloButton = button("Open Dulo", this::openDulo);
        catalogButton = button("Connect TMDB", this::showCatalogSettings);
        addButton(header, duloButton);
        addButton(header, catalogButton);
        addButton(header, button("About", this::showAbout));
        page.addView(header, new LinearLayout.LayoutParams(MATCH, dp(54)));

        hero = new FrameLayout(this);
        hero.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(MATCH, dp(244));
        heroParams.topMargin = dp(12);
        page.addView(hero, heroParams);

        listSection = column();
        page.addView(listSection);
        showSaved();
        catalogRows = column();
        page.addView(catalogRows);
        duloButton.requestFocus();
    }

    private void loadCatalog() {
        int version = ++loadVersion;
        if (refresh != null) refresh.cancel(true);
        catalogRows.removeAllViews();
        String token = preferences.getString(TOKEN, "");
        boolean connected = !token.isEmpty();
        catalogButton.setText(connected ? "Catalog settings" : "Connect TMDB");
        if (!connected) {
            showHero(null, "Browse Dulo or connect TMDB to fill your home screen with movies and TV shows.",
                    "Connect TMDB", this::showCatalogSettings);
            return;
        }

        showHero(null, "Loading movies and TV shows from TMDB…", null, null);
        refresh = requests.submit(() -> {
            List<Title> movies = Collections.emptyList();
            List<Title> shows = Collections.emptyList();
            boolean partial = false;
            try {
                try {
                    movies = TmdbCatalog.trending("movie", token);
                } catch (TmdbCatalog.InvalidToken e) {
                    throw e;
                } catch (IOException | JSONException e) {
                    partial = true;
                }
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    shows = TmdbCatalog.trending("tv", token);
                } catch (TmdbCatalog.InvalidToken e) {
                    throw e;
                } catch (IOException | JSONException e) {
                    partial = true;
                }
                List<Title> loadedMovies = movies;
                List<Title> loadedShows = shows;
                boolean incomplete = partial;
                runOnUiThread(() -> {
                    if (!isCurrent(version)) return;
                    showCatalog(loadedMovies, loadedShows, incomplete);
                });
            } catch (TmdbCatalog.InvalidToken e) {
                runOnUiThread(() -> {
                    if (!isCurrent(version)) return;
                    showHero(null, "TMDB rejected the saved credentials. Check your API key or read access token.",
                            "Update credentials", this::showCatalogSettings);
                });
            }
        });
    }

    private boolean isCurrent(int version) {
        return !isDestroyed() && version == loadVersion;
    }

    private void showCatalog(List<Title> movies, List<Title> shows, boolean incomplete) {
        Title featured = !movies.isEmpty() ? movies.get(0) : !shows.isEmpty() ? shows.get(0) : null;
        if (featured == null) {
            showHero(null, incomplete ? "The TMDB catalog could not be loaded." : "TMDB returned no titles.",
                    "Retry", this::loadCatalog);
            return;
        }
        showHero(featured, featured.overview, "View details", () -> showDetails(featured));
        if (!movies.isEmpty()) addRow(catalogRows, "Trending movies", movies);
        if (!shows.isEmpty()) addRow(catalogRows, "Trending TV shows", shows);
        if (incomplete) addButton(catalogRows, button("Retry unavailable row", this::loadCatalog));
    }

    private void showHero(Title title, String description, String action, Runnable listener) {
        hero.removeAllViews();
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * .58f), MATCH, Gravity.END);
        hero.addView(backdrop, artParams);
        backdrop.setImageResource(R.drawable.eminence_logo);
        if (title != null) loadImage(backdrop, title.backdrop, true);

        View gradient = new View(this);
        gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{INK, 0xfa0c0c0e, 0x850c0c0e, 0x150c0c0e}));
        hero.addView(gradient, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout copy = column();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(20), dp(10), dp(12), dp(10));
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * .51f), MATCH, Gravity.START);
        hero.addView(copy, copyParams);
        if (title != null) copy.addView(text(title.label(), 15, GOLD, true));
        TextView heading = text(title == null ? getString(R.string.app_name) : title.name, 34, Color.WHITE, true);
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(heading);
        if (!description.isEmpty()) {
            TextView synopsis = text(description, 16, MUTED, false);
            synopsis.setMaxLines(3);
            synopsis.setEllipsize(TextUtils.TruncateAt.END);
            synopsis.setPadding(0, dp(4), 0, dp(5));
            copy.addView(synopsis);
        }
        if (action != null) addButton(copy, button(action, listener));
    }

    private void addRow(LinearLayout section, String heading, List<Title> titles) {
        TextView label = text(heading, 23, Color.WHITE, true);
        label.setPadding(0, dp(20), 0, dp(8));
        section.addView(label);
        HorizontalScrollView rail = new HorizontalScrollView(this);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setClipToPadding(false);
        LinearLayout cards = new LinearLayout(this);
        for (Title title : titles) {
            LinearLayout card = column();
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(dp(3), dp(3), dp(3), dp(3));
            card.setBackground(focusBackground(PANEL, 8));
            card.setContentDescription(title.name + ", " + title.label());
            card.setOnClickListener(view -> showDetails(title));

            ImageView poster = new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setImageResource(R.drawable.eminence_logo);
            card.addView(poster, new LinearLayout.LayoutParams(MATCH, dp(151)));
            loadImage(poster, title.poster, false);
            TextView caption = text(title.name, 14, Color.WHITE, true);
            caption.setPadding(dp(5), dp(6), dp(4), dp(3));
            caption.setMaxLines(2);
            caption.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(caption, new LinearLayout.LayoutParams(MATCH, dp(47)));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(108), WRAP);
            cardParams.rightMargin = dp(11);
            cards.addView(card, cardParams);
        }
        rail.addView(cards, new HorizontalScrollView.LayoutParams(WRAP, WRAP));
        section.addView(rail, new LinearLayout.LayoutParams(MATCH, WRAP));
    }

    private void showDetails(Title title) {
        boolean listed = saved.containsKey(title.key());
        String summary = title.label() + (title.overview.isEmpty() ? "" : "\n\n" + title.overview);
        new AlertDialog.Builder(this)
                .setTitle(title.name)
                .setMessage(summary + "\n\nOpen Dulo to search for this title.")
                .setPositiveButton("Open Dulo", (dialog, which) -> openDulo())
                .setNeutralButton(listed ? "Remove from My List" : "Add to My List", (dialog, which) -> {
                    if (listed) saved.remove(title.key());
                    else saved.put(title.key(), title);
                    writeList();
                    showSaved();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showCatalogSettings() {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setHint("API key or API Read Access Token");
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(dp(24), dp(10), dp(24), 0);
        holder.addView(field, new FrameLayout.LayoutParams(MATCH, WRAP));
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Connect TMDB")
                .setMessage("Get your API key from themoviedb.org/settings/api. "
                        + "Enter it here to load movies and TV shows.")
                .setView(holder)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", null);
        if (!preferences.getString(TOKEN, "").isEmpty()) {
            builder.setNeutralButton("Disconnect", (dialog, which) -> {
                preferences.edit().remove(TOKEN).apply();
                loadCatalog();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String token = field.getText().toString().trim();
            if (!token.matches("[A-Fa-f0-9]{32}") && !token.matches("[A-Za-z0-9_-]+[.][A-Za-z0-9_-]+[.][A-Za-z0-9_-]+")) {
                field.setError("Enter a TMDB API key or API Read Access Token.");
                return;
            }
            preferences.edit().putString(TOKEN, token).apply();
            dialog.dismiss();
            loadCatalog();
        }));
        dialog.show();
    }

    private void showAbout() {
        LinearLayout credits = column();
        credits.setPadding(dp(24), dp(16), dp(24), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.tmdb_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("TMDB");
        credits.addView(logo, new LinearLayout.LayoutParams(dp(96), dp(32)));
        TextView attribution = text(
                "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                15, MUTED, false);
        attribution.setPadding(0, dp(8), 0, dp(10));
        credits.addView(attribution);
        new AlertDialog.Builder(this).setTitle("About Eminence VOD").setView(credits)
                .setPositiveButton("Close", null).show();
    }

    private void openDulo() {
        startActivity(new Intent(this, MainActivity.class));
    }

    private void showSaved() {
        listSection.removeAllViews();
        if (!saved.isEmpty()) addRow(listSection, "My List", new ArrayList<>(saved.values()));
    }

    private void readList() {
        try {
            JSONArray entries = new JSONArray(preferences.getString(WATCHLIST, "[]"));
            for (int i = 0; i < entries.length(); i++) {
                try {
                    Title title = Title.fromJson(entries.getJSONObject(i));
                    saved.put(title.key(), title);
                } catch (JSONException ignored) {
                    // Keep the rest of the list if an entry is unreadable.
                }
            }
        } catch (JSONException ignored) {
            // Start with an empty list if the saved data is unreadable.
        }
    }

    private void writeList() {
        JSONArray entries = new JSONArray();
        try {
            for (Title title : saved.values()) entries.put(title.toJson());
            preferences.edit().putString(WATCHLIST, entries.toString()).apply();
        } catch (JSONException e) {
            new AlertDialog.Builder(this).setMessage("My List could not be saved.")
                    .setPositiveButton("Close", null).show();
        }
    }

    private void loadImage(ImageView view, String path, boolean backdrop) {
        String url = TmdbCatalog.imageUrl(path, backdrop);
        if (url.isEmpty()) return;
        Bitmap cached = artwork.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        images.submit(() -> {
            try {
                Bitmap image = TmdbCatalog.image(url);
                if (image == null) return;
                artwork.put(url, image);
                runOnUiThread(() -> {
                    if (!isDestroyed() && view.isAttachedToWindow()) view.setImageBitmap(image);
                });
            } catch (IOException ignored) {
                // The Eminence artwork remains in place if a poster is unavailable.
            }
        });
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(15), 0, dp(15), 0);
        button.setBackground(focusBackground(PANEL, 7));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void addButton(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP, dp(42));
        params.rightMargin = dp(10);
        params.topMargin = dp(3);
        row.addView(button, params);
    }

    private StateListDrawable focusBackground(int color, int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(0xff47351d, radius, GOLD));
        states.addState(new int[]{}, shape(color, radius, Color.TRANSPARENT));
        return states;
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(2), stroke);
        return shape;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        requests.shutdownNow();
        images.shutdownNow();
        super.onDestroy();
    }
}
