package com.eminence.vod;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.eminence.vod.TmdbCatalog.Title;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
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
    private enum Section {
        MOVIES("movie", "Movies"), SHOWS("tv", "TV Shows"), SAVED("", "My List");

        final String type;
        final String label;

        Section(String type, String label) {
            this.type = type;
            this.label = label;
        }
    }

    private Section section = Section.MOVIES;
    private int pageNumber = 1;
    private String query = "";
    private boolean newest;
    private ScrollView scroll;
    private LinearLayout content;
    private Button catalogButton;
    private Button moviesButton;
    private Button showsButton;
    private Button savedButton;
    private Button searchButton;
    private Button sortButton;
    private TextView heading;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("eminence_catalog", MODE_PRIVATE);
        readList();
        if (state != null) {
            section = Section.valueOf(state.getString("section", Section.MOVIES.name()));
            pageNumber = state.getInt("page", 1);
            query = state.getString("query", "");
            newest = state.getBoolean("newest");
        }
        createPage();
        loadCatalog();
    }

    private void createPage() {
        getWindow().setStatusBarColor(INK);
        getWindow().setNavigationBarColor(INK);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout page = column();
        page.setBackgroundColor(INK);
        page.setPadding(dp(32), dp(12), dp(32), 0);
        setContentView(page);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.eminence_icon);
        header.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = text(getString(R.string.app_name), 20, GOLD, true);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(dp(10), 0, dp(10), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1));
        addButton(header, button(getString(R.string.watch), () -> PlaybackSource.SWEETFOX.open(this, true)));
        addButton(header, button(getString(R.string.sources_short), this::choosePlaybackSource));
        catalogButton = button("Connect TMDB", this::showCatalogSettings);
        addButton(header, catalogButton);
        addButton(header, button("About", this::showAbout));
        page.addView(header, new LinearLayout.LayoutParams(MATCH, dp(52)));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        moviesButton = button("Movies", () -> selectSection(Section.MOVIES));
        showsButton = button("TV Shows", () -> selectSection(Section.SHOWS));
        savedButton = button("My List", () -> selectSection(Section.SAVED));
        searchButton = button("Search", this::showSearch);
        sortButton = button("Sort: Popular", () -> {
            newest = !newest;
            pageNumber = 1;
            loadCatalog();
        });
        addButton(navigation, moviesButton);
        addButton(navigation, showsButton);
        addButton(navigation, savedButton);
        navigation.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        addButton(navigation, searchButton);
        addButton(navigation, sortButton);
        page.addView(navigation, new LinearLayout.LayoutParams(MATCH, dp(52)));

        heading = text("", 18, MUTED, false);
        heading.setSingleLine();
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setPadding(0, dp(5), 0, dp(10));
        page.addView(heading);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        content = column();
        content.setPadding(0, 0, 0, dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(MATCH, WRAP));
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));
        moviesButton.requestFocus();
    }

    private void selectSection(Section next) {
        section = next;
        pageNumber = 1;
        query = "";
        loadCatalog();
    }

    private void loadCatalog() {
        loadCatalog(false);
    }

    private void loadCatalog(boolean focusResults) {
        int version = ++loadVersion;
        if (refresh != null) refresh.cancel(true);
        content.removeAllViews();
        scroll.scrollTo(0, 0);
        String token = preferences.getString(TOKEN, "");
        boolean connected = !token.isEmpty();
        catalogButton.setText(connected ? "Catalog settings" : "Connect TMDB");
        moviesButton.setSelected(section == Section.MOVIES);
        showsButton.setSelected(section == Section.SHOWS);
        savedButton.setSelected(section == Section.SAVED);
        searchButton.setVisibility(connected && section != Section.SAVED ? View.VISIBLE : View.GONE);
        sortButton.setVisibility(connected && section != Section.SAVED && query.isEmpty() ? View.VISIBLE : View.GONE);
        sortButton.setText(newest ? "Sort: Newest" : "Sort: Popular");
        heading.setText(query.isEmpty() ? section.label : section.label + "  ·  " + query);

        if (section == Section.SAVED) {
            if (saved.isEmpty()) showMessage("Save a movie or show from its details to find it here.", null, null);
            else showGrid(new ArrayList<>(saved.values()), 1, focusResults);
            return;
        }
        if (!connected) {
            showMessage("Connect TMDB to browse movies and TV shows.", null, null);
            return;
        }

        showMessage("Loading " + section.label.toLowerCase(java.util.Locale.US) + "…", null, null);
        String type = section.type;
        String search = query;
        boolean sortNewest = newest;
        int requestedPage = pageNumber;
        refresh = requests.submit(() -> {
            try {
                TmdbCatalog.Page result = TmdbCatalog.browse(type, search, sortNewest, requestedPage, token);
                runOnUiThread(() -> {
                    if (isCurrent(version)) showGrid(result.titles, result.totalPages, focusResults);
                });
            } catch (TmdbCatalog.InvalidToken e) {
                runOnUiThread(() -> {
                    if (isCurrent(version)) showMessage("TMDB rejected your credentials.",
                            "Update credentials", this::showCatalogSettings);
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> {
                    if (isCurrent(version)) showMessage("The TMDB catalog could not be loaded.",
                            "Retry", () -> loadCatalog(focusResults));
                });
            }
        });
    }

    private boolean isCurrent(int version) {
        return !isDestroyed() && version == loadVersion;
    }

    private void showMessage(String message, String action, Runnable listener) {
        content.removeAllViews();
        TextView status = text(message, 16, MUTED, false);
        status.setPadding(0, dp(14), 0, dp(8));
        content.addView(status);
        if (action != null) addButton(content, button(action, listener));
    }

    private void showGrid(List<Title> titles, int totalPages, boolean focusResults) {
        content.removeAllViews();
        int availableWidth = getResources().getDisplayMetrics().widthPixels - dp(64);
        int columns = Math.max(1, Math.min(9, availableWidth / dp(116)));
        LinearLayout row = null;
        View firstCard = null;
        for (int i = 0; i < titles.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                content.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
            }
            View card = createCard(titles.get(i));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(108), WRAP);
            params.rightMargin = dp(8);
            params.bottomMargin = dp(8);
            row.addView(card, params);
            if (i == 0) firstCard = card;
        }

        if (titles.isEmpty()) {
            showMessage(query.isEmpty() ? "No titles on this page." : "No matching titles on this page.", null, null);
        }
        if (totalPages > 1 || pageNumber > 1) {
            LinearLayout pagination = new LinearLayout(this);
            pagination.setGravity(Gravity.CENTER_VERTICAL);
            Button previous = button("Previous page", () -> {
                pageNumber--;
                loadCatalog(true);
            });
            previous.setEnabled(pageNumber > 1);
            Button next = button("Next page", () -> {
                pageNumber++;
                loadCatalog(true);
            });
            next.setEnabled(pageNumber < totalPages);
            addButton(pagination, previous);
            addButton(pagination, next);
            TextView pageLabel = text("Page " + pageNumber + " of " + Math.max(totalPages, pageNumber), 15, MUTED, false);
            pageLabel.setPadding(dp(5), 0, 0, 0);
            pagination.addView(pageLabel);
            content.addView(pagination);
            if (firstCard == null) firstCard = next.isEnabled() ? next : previous;
        }
        if (focusResults && firstCard != null) firstCard.requestFocus();
    }

    private View createCard(Title title) {
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
        loadImage(poster, title.poster);
        TextView caption = text(title.name, 14, Color.WHITE, true);
        caption.setPadding(dp(5), dp(6), dp(4), dp(3));
        caption.setMaxLines(2);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(caption, new LinearLayout.LayoutParams(MATCH, dp(47)));
        return card;
    }

    private void showSearch() {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setHint("Title");
        field.setText(query);
        field.setSelection(field.length());
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(dp(24), dp(10), dp(24), 0);
        holder.addView(field, new FrameLayout.LayoutParams(MATCH, WRAP));
        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Search " + section.label)
                .setView(holder)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Search", (view, which) -> setQuery(field.getText().toString().trim()));
        if (!query.isEmpty()) dialog.setNeutralButton("Clear search", (view, which) -> setQuery(""));
        dialog.show();
    }

    private void setQuery(String value) {
        query = value;
        pageNumber = 1;
        loadCatalog(true);
    }

    private void showDetails(Title title) {
        boolean listed = saved.containsKey(title.key());
        String summary = title.label() + (title.overview.isEmpty() ? "" : "\n\n" + title.overview);
        new AlertDialog.Builder(this)
                .setTitle(title.name)
                .setMessage(summary + "\n\n" + getString(R.string.find_on_source))
                .setPositiveButton(R.string.playback_sources, (dialog, which) -> choosePlaybackSource())
                .setNeutralButton(listed ? "Remove from My List" : "Add to My List", (dialog, which) -> {
                    if (listed) saved.remove(title.key());
                    else saved.put(title.key(), title);
                    writeList();
                    if (section == Section.SAVED) loadCatalog(true);
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

    private void choosePlaybackSource() {
        PlaybackSource.choose(this, source -> source.open(this, false));
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

    private void loadImage(ImageView view, String path) {
        String url = TmdbCatalog.imageUrl(path);
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
        button.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{0xff777579, Color.WHITE}));
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
        states.addState(new int[]{android.R.attr.state_selected}, shape(0xff392b1a, radius, 0xff806232));
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

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("section", section.name());
        state.putInt("page", pageNumber);
        state.putString("query", query);
        state.putBoolean("newest", newest);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        requests.shutdownNow();
        images.shutdownNow();
        super.onDestroy();
    }
}
