package com.eminence.vod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;

enum PlaybackSource {
    SWEETFOX("Sweetfox", "https://sweetfox.app/", "sweetfox.app"),
    FLIXER("Flixer", "https://flixer.gd/", "flixer.gd"),
    DULO("Dulo", "https://dulo.mov/", "dulo.mov"),
    MOVY("Movy", "https://www.movy.sx/", "movy.sx");

    private static final String EXTRA_SOURCE = "com.eminence.vod.PLAYBACK_SOURCE";
    private static final String EXTRA_FALLBACK = "com.eminence.vod.AUTO_FALLBACK";

    final String label;
    final String home;
    private final String host;

    PlaybackSource(String label, String home, String host) {
        this.label = label;
        this.home = home;
        this.host = host;
    }

    static PlaybackSource selectedBy(Intent intent) {
        PlaybackSource[] sources = values();
        int index = intent.getIntExtra(EXTRA_SOURCE, 0);
        return index >= 0 && index < sources.length ? sources[index] : SWEETFOX;
    }

    static boolean autoFallback(Intent intent) {
        return intent.getBooleanExtra(EXTRA_FALLBACK, true);
    }

    PlaybackSource next() {
        PlaybackSource[] sources = values();
        int index = ordinal() + 1;
        return index < sources.length ? sources[index] : null;
    }

    boolean allows(Uri address) {
        String addressHost = address.getHost();
        return "https".equalsIgnoreCase(address.getScheme())
                && address.getUserInfo() == null
                && (address.getPort() == -1 || address.getPort() == 443)
                && (host.equalsIgnoreCase(addressHost) || ("www." + host).equalsIgnoreCase(addressHost));
    }

    void open(Activity activity, boolean autoFallback) {
        Intent intent = new Intent(activity, MainActivity.class)
                .putExtra(EXTRA_SOURCE, ordinal())
                .putExtra(EXTRA_FALLBACK, autoFallback);
        activity.startActivity(intent);
    }

    static void choose(Activity activity, Selection selection) {
        PlaybackSource[] sources = values();
        String[] labels = new String[sources.length];
        for (int i = 0; i < sources.length; i++) labels[i] = sources[i].label;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.playback_sources)
                .setItems(labels, (dialog, which) -> selection.open(sources[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    interface Selection {
        void open(PlaybackSource source);
    }
}
