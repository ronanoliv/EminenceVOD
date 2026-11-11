package com.eminence.vod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final long SOURCE_TIMEOUT_MS = 15000;
    private static final String AUTOPLAY_STATE =
            "if (location.origin === 'https://dulo.mov' && window.__eminenceEpisodeAutoplay) "
                    + "window.__eminenceEpisodeAutoplay.active = ";
    private static final int GOLD = 0xffe6b95d;
    private static final int FILL = FrameLayout.LayoutParams.MATCH_PARENT;

    private FrameLayout root;
    private LinearLayout toolbar;
    private WebView web;
    private String episodeAutoplayScript;
    private boolean resumed;
    private PlaybackSource source;
    private boolean autoFallback;
    private boolean initialLoadPending;
    private final Runnable sourceTimeout = this::handleInitialLoadFailure;
    private TextView pointer;
    private ImageView splash;
    private final Runnable dismissSplash = this::removeSplash;
    private Button homeButton;
    private View video;
    private WebChromeClient.CustomViewCallback videoCallback;
    private float pointerX;
    private float pointerY;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        source = PlaybackSource.selectedBy(getIntent());
        autoFallback = PlaybackSource.autoFallback(getIntent());
        hideSystemBars();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        createWebView();
        createToolbar();
        createPointer();
        showSplash();
        root.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (oldR == oldL) {
                pointerX = root.getWidth() / 2f;
                pointerY = root.getHeight() / 2f;
            }
            positionPointer();
        });
        if (state == null || web.restoreState(state) == null) {
            loadSourceHome();
        }
        Toast.makeText(this, R.string.remote_hint, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void createWebView() {
        if (source == PlaybackSource.DULO) episodeAutoplayScript = loadEpisodeAutoplayScript();
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        // Enable separate-window requests so the default WebChromeClient rejects them.
        web.getSettings().setSupportMultipleWindows(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri address = request.getUrl();
                return request.isForMainFrame() ? !source.allows(address) : blockNonHttps(address);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return blockNonHttps(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                // Also guard top-level navigations on older WebViews and form submissions.
                if (url != null && !source.allows(Uri.parse(url))) view.stopLoading();
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                Uri address = url == null ? Uri.EMPTY : Uri.parse(url);
                String path = address.getPath();
                boolean episode = source == PlaybackSource.DULO && source.allows(address)
                        && path != null && path.matches("/watch/tv/[0-9]+/?");
                view.getSettings().setMediaPlaybackRequiresUserGesture(!episode);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (episodeAutoplayScript != null && url != null && "dulo.mov".equals(Uri.parse(url).getHost())) {
                    view.evaluateJavascript(episodeAutoplayScript + "\n" + AUTOPLAY_STATE + resumed + ";", null);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (url != null && source.allows(Uri.parse(url))) finishInitialLoad();
                removeSplash();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) handlePageError(request.getUrl().toString());
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 400) {
                    handlePageError(request.getUrl().toString());
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (video != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                setToolbarVisible(false);
                video = view;
                videoCallback = callback;
                web.setVisibility(View.INVISIBLE);
                root.addView(video, new FrameLayout.LayoutParams(FILL, FILL));
                pointer.bringToFront();
                video.requestFocus();
                hideSystemBars();
            }

            @Override
            public void onHideCustomView() {
                exitVideo();
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(FILL, FILL));
        web.requestFocus();
    }

    private String loadEpisodeAutoplayScript() {
        try (InputStream input = getAssets().open("dulo-episode-autoplay.js")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            android.util.Log.w("EminenceVOD", "Unable to load episode autoplay", error);
            return null;
        }
    }

    private void loadSourceHome() {
        initialLoadPending = true;
        web.loadUrl(source.home);
        startSourceTimeout();
    }

    private void startSourceTimeout() {
        web.removeCallbacks(sourceTimeout);
        if (resumed && initialLoadPending) web.postDelayed(sourceTimeout, SOURCE_TIMEOUT_MS);
    }

    private void finishInitialLoad() {
        initialLoadPending = false;
        web.removeCallbacks(sourceTimeout);
    }

    private void handlePageError(String url) {
        if (url == null || !source.allows(Uri.parse(url))) return;
        if (initialLoadPending) {
            if (resumed) handleInitialLoadFailure();
            return;
        }
        removeSplash();
        Toast.makeText(this, getString(R.string.load_error, source.label), Toast.LENGTH_LONG).show();
    }

    private void handleInitialLoadFailure() {
        if (!initialLoadPending || !resumed || isFinishing()) return;
        finishInitialLoad();
        web.stopLoading();
        removeSplash();
        PlaybackSource next = autoFallback ? source.next() : null;
        if (next != null) {
            Toast.makeText(this, getString(R.string.try_next_source, source.label, next.label),
                    Toast.LENGTH_SHORT).show();
            next.open(this, true);
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.load_error, source.label))
                .setPositiveButton(R.string.playback_sources, (dialog, which) -> chooseSource())
                .setNeutralButton(R.string.reload, (dialog, which) -> loadSourceHome())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseSource() {
        PlaybackSource.choose(this, selected -> {
            if (selected == source) {
                loadSourceHome();
            } else {
                selected.open(this, false);
                finish();
            }
        });
    }

    private boolean blockNonHttps(Uri url) {
        return !"https".equalsIgnoreCase(url.getScheme());
    }

    private void createToolbar() {
        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(0xff111111);
        toolbar.setPadding(dp(12), 0, dp(12), 0);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.eminence_icon);
        toolbar.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = new TextView(this);
        title.setText(source.label);
        title.setTextColor(GOLD);
        title.setTextSize(18);
        title.setPadding(dp(10), 0, dp(12), 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        homeButton = addButton(R.string.home, this::loadSourceHome);
        addButton(R.string.back, () -> {
            if (web.canGoBack()) web.goBack();
        });
        addButton(R.string.reload, web::reload);
        addButton(R.string.sources_short, this::chooseSource);
        addButton(R.string.browser, this::openExternal);
        toolbar.setVisibility(View.GONE);
        root.addView(toolbar, new FrameLayout.LayoutParams(FILL, dp(60), Gravity.TOP));
    }

    private Button addButton(int label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_focused}, new int[]{}},
                new int[]{0xff775014, 0xff292929}));
        button.setOnClickListener(view -> {
            setToolbarVisible(false);
            action.run();
        });
        toolbar.addView(button, new LinearLayout.LayoutParams(-2, dp(52)));
        return button;
    }

    private void createPointer() {
        pointer = new TextView(this);
        pointer.setText("⊕");
        pointer.setTextColor(GOLD);
        pointer.setTextSize(24);
        pointer.setShadowLayer(dp(2), 0, 0, Color.BLACK);
        pointer.setGravity(Gravity.CENTER);
        pointer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(pointer, new FrameLayout.LayoutParams(dp(28), dp(32)));
    }

    private void showSplash() {
        splash = new ImageView(this);
        splash.setImageResource(R.drawable.eminence_logo);
        splash.setBackgroundColor(Color.BLACK);
        splash.setScaleType(ImageView.ScaleType.FIT_CENTER);
        splash.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.addView(splash, new FrameLayout.LayoutParams(FILL, FILL));
        splash.postDelayed(dismissSplash, 1300);
    }

    private void removeSplash() {
        if (splash == null) return;
        splash.removeCallbacks(dismissSplash);
        root.removeView(splash);
        splash = null;
    }

    private boolean toolbarVisible() {
        return toolbar.getVisibility() == View.VISIBLE;
    }

    private void setToolbarVisible(boolean visible) {
        removeSplash();
        toolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
        pointer.setVisibility(visible ? View.INVISIBLE : View.VISIBLE);
        if (visible) {
            homeButton.requestFocus();
        } else {
            web.requestFocus();
        }
    }

    private void openExternal() {
        String url = web.getUrl();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url == null ? source.home : url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void exitVideo() {
        if (video == null) return;
        root.removeView(video);
        video = null;
        WebChromeClient.CustomViewCallback callback = videoCallback;
        videoCallback = null;
        web.setVisibility(View.VISIBLE);
        web.requestFocus();
        callback.onCustomViewHidden();
        hideSystemBars();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean pressed = event.getAction() == KeyEvent.ACTION_DOWN;
        if (key == KeyEvent.KEYCODE_MENU && video == null) {
            if (pressed && event.getRepeatCount() == 0) setToolbarVisible(!toolbarVisible());
            return true;
        }
        if (toolbarVisible()) {
            if (key == KeyEvent.KEYCODE_DPAD_DOWN && pressed) {
                setToolbarVisible(false);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        int step = dp(32);
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (pressed) movePointer(-step, 0, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (pressed) movePointer(step, 0, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (pressed) movePointer(0, -step, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (pressed) movePointer(0, step, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (!pressed && !event.isCanceled()) clickPointer();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (video == null && pressed) web.scrollBy(0,
                        (key == KeyEvent.KEYCODE_CHANNEL_DOWN ? 1 : -1) * web.getHeight() / 2);
                return video == null || super.dispatchKeyEvent(event);
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void movePointer(int dx, int dy, int repeatCount) {
        int edge = dp(12);
        if (video == null && dy < 0 && pointerY <= edge && repeatCount == 0) {
            setToolbarVisible(true);
            return;
        }
        if (video == null && (pointerY <= edge && dy < 0
                || pointerY >= root.getHeight() - edge && dy > 0)) {
            web.scrollBy(0, dy * 2);
        }
        pointerX += dx;
        pointerY += dy;
        positionPointer();
    }

    private void positionPointer() {
        int edge = dp(12);
        pointerX = Math.max(edge, Math.min(root.getWidth() - edge, pointerX));
        pointerY = Math.max(edge, Math.min(root.getHeight() - edge, pointerY));
        pointer.setX(pointerX - dp(14));
        pointer.setY(pointerY - dp(16));
    }

    private void clickPointer() {
        removeSplash();
        View target = video == null ? web : video;
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, pointerX, pointerY, 0);
        MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, pointerX, pointerY, 0);
        target.dispatchTouchEvent(down);
        target.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (video != null) {
            exitVideo();
        } else if (toolbarVisible()) {
            setToolbarVisible(false);
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        web.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        resumed = false;
        web.removeCallbacks(sourceTimeout);
        web.evaluateJavascript(AUTOPLAY_STATE + "false;", null);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        startSourceTimeout();
        web.onResume();
        web.evaluateJavascript(AUTOPLAY_STATE + "true;", null);
    }

    @Override
    protected void onDestroy() {
        finishInitialLoad();
        exitVideo();
        removeSplash();
        web.stopLoading();
        root.removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
