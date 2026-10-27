package com.xeleo.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * A small player for whatever is playing on the TV (Spotify, YouTube...): cover, title, artist, the app's name
 * and previous / play-pause / next; OK on the cover opens that app. Reads Android's active media sessions, which needs notification access for
 * {@link MediaListener}; without it, or with nothing playing, the view stays hidden.
 */
final class NowPlayingView extends LinearLayout {

    private final float density;
    private final boolean panelStyle;
    private final ImageView art;
    private final TextView title, subtitle;
    private final ImageView playPause;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaController controller;
    /** Kept out of sight even when something plays (Home, while the "Hoy" row needs the room). */
    private boolean hidden;

    void setHidden(boolean h) {
        if (h == hidden) return;
        hidden = h;
        render();
    }

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = list -> refresh();

    /** panelStyle: the quick settings card; otherwise the glass pill on Home. */
    NowPlayingView(Context context, boolean panelStyle, Typeface regular, Typeface medium) {
        super(context);
        this.panelStyle = panelStyle;
        density = context.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(panelStyle ? 16 : 18));
        bg.setColor(panelStyle ? 0xFF1C262C : 0x33FFFFFF);
        if (!panelStyle) bg.setStroke(1, 0x40FFFFFF);
        setBackground(bg);
        setVisibility(GONE);

        int artSize = panelStyle ? 52 : 46;
        art = new ImageView(context);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
            }
        });
        art.setBackgroundColor(0x33FFFFFF);
        // The cover is a button too: OK opens the app that is playing, at its player.
        art.setFocusable(true);
        art.setClickable(true);
        art.setContentDescription("Abrir la app");
        art.setOnClickListener(v -> openApp());
        art.setOnFocusChangeListener((v, f) -> {
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(8));
            ring.setStroke(f ? dp(3) : 0, 0xFFFFFFFF);
            v.setForeground(f ? ring : null);
            v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(120).start();
        });
        addView(art, new LayoutParams(dp(artSize), dp(artSize)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        texts.setPadding(dp(10), 0, dp(6), 0);
        title = text(panelStyle ? 12 : 14, Color.WHITE, medium);
        subtitle = text(panelStyle ? 9.5f : 11, 0xCCFFFFFF, regular);
        subtitle.setPadding(0, dp(3), 0, 0);
        texts.addView(title);
        texts.addView(subtitle);
        addView(texts, panelStyle ? new LayoutParams(0, -2, 1f) : new LayoutParams(dp(220), -2));

        addView(button(R.drawable.ic_media_prev, () -> transport(0)));
        playPause = button(R.drawable.ic_media_play, () -> transport(1));
        addView(playPause);
        addView(button(R.drawable.ic_media_next, () -> transport(2)));
    }

    /** For the screensaver: just the song, no buttons (nothing there can be pressed). */
    void hideControls() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View v = getChildAt(i);
            if (v instanceof ImageView && v != art) {
                v.setVisibility(GONE);
                v.setFocusable(false);
            }
        }
        art.setFocusable(false);
        art.setClickable(false);
    }

    /**
     * Opens the app that is playing: at its player when it says where that is (the session's activity, as its
     * notification does), else at its start. From quick settings the panel closes behind it.
     */
    private void openApp() {
        if (controller == null) return;
        Context c = getContext();
        boolean opened = false;
        android.app.PendingIntent player = controller.getSessionActivity();
        if (player != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    // Android 14 lets a sender start the activity only when it says so (Home may start activities).
                    android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(
                                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    player.send(c, 0, null, null, null, null, o.toBundle());
                } else {
                    player.send();
                }
                opened = true;
            } catch (android.app.PendingIntent.CanceledException | RuntimeException ignored) {
            }
        }
        if (!opened) {
            PackageManager pm = c.getPackageManager();
            android.content.Intent launch = pm.getLeanbackLaunchIntentForPackage(controller.getPackageName());
            if (launch == null) launch = pm.getLaunchIntentForPackage(controller.getPackageName());
            if (launch == null) return;
            try {
                c.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                return;
            }
        }
        if (panelStyle && c instanceof android.app.Activity) ((android.app.Activity) c).finish();
    }

    private TextView text(float sp, int color, Typeface tf) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private ImageView button(int icon, Runnable action) {
        ImageView b = new ImageView(getContext());
        b.setImageResource(icon);
        int pad = dp(panelStyle ? 7 : 9);
        b.setPadding(pad, pad, pad, pad);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(v -> action.run());
        style(b, false);
        b.setOnFocusChangeListener((v, f) -> style((ImageView) v, f));
        LayoutParams lp = new LayoutParams(dp(panelStyle ? 30 : 36), dp(panelStyle ? 30 : 36));
        lp.leftMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    private void style(ImageView b, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(focused ? 0xFFE3E3E3 : 0x26FFFFFF);
        b.setBackground(bg);
        b.setColorFilter(focused ? 0xFF1F1F1F : Color.WHITE);
    }

    private void transport(int which) {
        if (controller == null) return;
        MediaController.TransportControls t = controller.getTransportControls();
        PlaybackState st = controller.getPlaybackState();
        if (which == 0) t.skipToPrevious();
        else if (which == 2) t.skipToNext();
        else if (st != null && st.getState() == PlaybackState.STATE_PLAYING) t.pause();
        else t.play();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MediaSessionManager msm = getContext().getSystemService(MediaSessionManager.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener(), handler);
        } catch (SecurityException ignored) {
            // No notification access yet: the player simply stays hidden.
        }
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        MediaSessionManager msm = getContext().getSystemService(MediaSessionManager.class);
        msm.removeOnActiveSessionsChangedListener(sessionsChanged);
        setController(null);
    }

    private ComponentName listener() {
        return new ComponentName(getContext(), MediaListener.class);
    }

    /** Picks the session that is playing, else the first paused one. */
    void refresh() {
        MediaController pick = null;
        try {
            List<MediaController> list = getContext().getSystemService(MediaSessionManager.class)
                    .getActiveSessions(listener());
            for (MediaController c : list) {
                PlaybackState st = c.getPlaybackState();
                if (st == null || c.getMetadata() == null) continue;
                int state = st.getState();
                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) { pick = c; break; }
                // Paused or just stopped (Spotify reports NONE between tracks): still worth a play button.
                if (pick == null && state != PlaybackState.STATE_ERROR) pick = c;
            }
        } catch (SecurityException ignored) {
        }
        setController(pick);
    }

    private void setController(MediaController c) {
        if (controller != null) controller.unregisterCallback(callback);
        controller = c;
        if (controller != null) controller.registerCallback(callback, handler);
        render();
    }

    private void render() {
        MediaMetadata md = controller != null ? controller.getMetadata() : null;
        if (md == null || hidden) {
            setVisibility(GONE);
            return;
        }
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null) t = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        title.setText(t == null ? appName() : t);
        subtitle.setText(a == null || a.isEmpty() ? appName() : a + "  ·  " + appName());
        Bitmap bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bmp == null) bmp = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (bmp != null) art.setImageBitmap(bmp);
        else {
            try {
                art.setImageDrawable(getContext().getPackageManager().getApplicationIcon(controller.getPackageName()));
            } catch (PackageManager.NameNotFoundException e) {
                art.setImageDrawable(null);
            }
        }
        PlaybackState st = controller.getPlaybackState();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        playPause.setImageResource(playing ? R.drawable.ic_media_pause : R.drawable.ic_media_play);
        setVisibility(VISIBLE);
    }

    private String appName() {
        try {
            PackageManager pm = getContext().getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(controller.getPackageName(), 0)).toString();
        } catch (PackageManager.NameNotFoundException | NullPointerException e) {
            return "";
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
