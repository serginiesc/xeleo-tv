package com.xeleo.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The En vivo tab, inside Home under its top bar: Pluto TV's and ViX's channels by category, and on top a big
 * preview of what the focused one shows now (its picture, title, description and time left). OK opens the channel
 * in its app. Home shows it when the En vivo tab is selected (open) and hides it otherwise (close).
 */
final class LiveView extends FrameLayout {

    private static final int BG = 0xFF0B0F17;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xB3FFFFFF;
    private static final int RED = 0xFFE53935;
    private static final int VIX = 0xFFF06A26;

    private float density;
    private Typeface regular, medium;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat hm = new SimpleDateFormat("h:mm", Locale.getDefault());

    private ImageView heroImage;
    private TextView badge, channelName, title, meta, description, next, status;
    private View progressFill;
    private LinearLayout rowsBox;
    private Live.Channel shownChannel;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            if (shownChannel != null) showHero(shownChannel, false);
            handler.postDelayed(this, 60_000);
        }
    };

    LiveView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        regular = getResources().getFont(R.font.outfit_regular);
        medium = getResources().getFont(R.font.outfit_medium);

        FrameLayout root = this;
        root.setBackgroundColor(BG);
        heroImage = new ImageView(getContext());
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));
        // Dark from the left for the text, and from the bottom for the rows.
        View left = new View(getContext());
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BG, 0xF20B0F17, 0x990B0F17, 0x000B0F17}));
        root.addView(left, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(getContext());
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000B0F17, 0x330B0F17, 0xE60B0F17, BG, BG}));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -1));

        root.addView(hero(), new FrameLayout.LayoutParams(dp(540), -2, Gravity.TOP | Gravity.START));

        ScrollView scroll = new ScrollView(getContext());
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);   // a row scrolled up fades instead of being cut
        scroll.setFadingEdgeLength(dp(28));
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        rowsBox = new LinearLayout(getContext());
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setClipChildren(false);
        rowsBox.setPadding(0, dp(56), 0, dp(24));   // the chips and cards sit low, under the preview
        scroll.addView(rowsBox, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, dp(262), Gravity.BOTTOM));

        status = text(16, TEXT_DIM, regular);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        root.addView(status, new FrameLayout.LayoutParams(-1, dp(262), Gravity.BOTTOM));

        placeholders();
    }

    /** The tab was selected: the guide (from the cache, so at once) and the minute tick. */
    void open() {
        Live.load(getContext(), this::onGuide);
        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, 60_000);
    }

    /** Another tab was selected. */
    void close() {
        handler.removeCallbacks(minuteTick);
        closeSheet();
    }

    /** ▼ from the top bar: into the cards (or the chips while there are none). */
    void focusContent() {
        if (cardsLine != null && cardsLine.getChildCount() > 0 && cardsLine.getChildAt(0).isFocusable()) {
            cardsLine.getChildAt(0).requestFocus();
        } else if (chips.get(selected) != null) {
            chips.get(selected).requestFocus();
        }
    }

    /** Dim empty cards, gently pulsing, for the moment the guide takes when Home couldn't fetch it ahead. */
    private void placeholders() {
        for (int r = 0; r < 2; r++) {
            View label = new View(getContext());
            label.setBackground(round(0x1FFFFFFF, dp(4)));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(90), dp(14));
            llp.setMargins(dp(40), dp(12), 0, dp(10));
            rowsBox.addView(label, llp);
            LinearLayout line = new LinearLayout(getContext());
            line.setPadding(dp(40), dp(4), 0, dp(6));
            for (int i = 0; i < 5; i++) {
                View card = new View(getContext());
                card.setBackground(round(0x14FFFFFF, dp(12)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(184), dp(96));
                lp.rightMargin = dp(12);
                line.addView(card, lp);
            }
            rowsBox.addView(line);
        }
        android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(rowsBox, "alpha", 1f, 0.45f);
        pulse.setDuration(700);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.start();
        rowsBox.setTag(pulse);
    }


    // ---------------------------------------------------------------- top

    private View topBar() {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(40), dp(22), dp(40), 0);
        TextView name = text(19, TEXT, medium);
        name.setText("En vivo");
        name.setShadowLayer(dp(6), 0, 0, 0x80000000);
        bar.addView(name);
        TextView source = text(13, TEXT_DIM, regular);
        source.setText(Live.plutoInstalled(getContext()) ? "Pluto TV y ViX  ·  OK abre el canal en su app"
                : "Pluto TV y ViX  ·  Instala la app Pluto TV para ver sus canales");
        source.setPadding(dp(14), dp(3), 0, 0);
        bar.addView(source, new LinearLayout.LayoutParams(0, -2, 1f));
        TextClock clock = new TextClock(getContext());
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(26);
        clock.setTypeface(regular);
        clock.setTextColor(TEXT);
        clock.setShadowLayer(dp(8), 0, 0, 0x80000000);
        bar.addView(clock);
        return bar;
    }

    private View hero() {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(40), dp(76), 0, 0);

        LinearLayout line = new LinearLayout(getContext());
        line.setGravity(Gravity.CENTER_VERTICAL);
        badge = text(11, TEXT, medium);
        badge.setLetterSpacing(0.08f);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        line.addView(badge);
        channelName = text(14, TEXT_DIM, medium);
        channelName.setPadding(dp(10), 0, 0, 0);
        channelName.setSingleLine(true);
        line.addView(channelName);
        col.addView(line);

        title = text(32, TEXT, medium);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(dp(8), 0, 0, 0x80000000);
        title.setPadding(0, dp(10), 0, 0);
        col.addView(title);
        meta = text(14, TEXT_DIM, regular);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(8), 0, 0);
        col.addView(meta);
        description = text(13, TEXT_DIM, regular);
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setLineSpacing(dp(2), 1f);
        description.setPadding(0, dp(8), dp(20), 0);
        col.addView(description);

        FrameLayout track = new FrameLayout(getContext());
        track.setBackground(round(0x40FFFFFF, dp(2)));
        progressFill = new View(getContext());
        progressFill.setBackground(round(TEXT, dp(2)));
        track.addView(progressFill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(220), dp(3));
        tlp.topMargin = dp(12);
        col.addView(track, tlp);
        next = text(12, TEXT_DIM, regular);
        next.setSingleLine(true);
        next.setEllipsize(TextUtils.TruncateAt.END);
        next.setPadding(0, dp(8), 0, 0);
        col.addView(next);
        return col;
    }

    /** Fills the preview with what the channel shows now. */
    private void showHero(Live.Channel ch, boolean newPicture) {
        shownChannel = ch;
        long now = System.currentTimeMillis();
        Live.Program p = ch.at(now);
        channelName.setText(ch.name);
        boolean live = p != null && p.live, vix = !ch.vix.isEmpty();
        badge.setText(vix ? "ViX" : live ? "EN VIVO" : "AHORA");
        badge.setBackground(round(vix ? VIX : live ? RED : 0x33FFFFFF, dp(10)));
        if (p == null) {
            title.setText(ch.name);
            meta.setText(vix ? "Canal gratis de ViX  ·  OK lo abre en la app de ViX" : "");
            description.setText(vix ? "ViX no publica su programación; al abrirlo ves qué están pasando." : "");
            next.setText("");
            setProgress(0);
        } else {
            title.setText(p.title);
            int left = (int) Math.max(1, (p.stop - now) / 60_000);
            StringBuilder m = new StringBuilder();
            if (!p.episode.isEmpty()) m.append(p.episode).append("  ·  ");
            m.append(hm.format(new Date(p.start))).append("–").append(hm.format(new Date(p.stop)))
                    .append("  ·  ").append(left < 60 ? "quedan " + left + " min" : "queda " + left / 60 + " h " + left % 60 + " min");
            meta.setText(m);
            description.setText(p.description);
            Live.Program n = ch.after(p);
            next.setText(n == null ? "" : "Sigue a las " + hm.format(new Date(n.start)) + ":  " + n.title);
            setProgress((float) (now - p.start) / (p.stop - p.start));
        }
        if (!newPicture) return;
        String img = p != null && !p.image.isEmpty() ? p.image : ch.image;
        if (img.equals(heroImage.getTag())) return;
        heroImage.animate().cancel();
        heroImage.setAlpha(0.35f);
        Logos.photo(heroImage, img, ch.image, () -> heroImage.animate().alpha(1f).setDuration(260).start());
    }

    private void setProgress(float f) {
        View track = (View) progressFill.getParent();
        track.setVisibility(shownChannel != null && shownChannel.at(System.currentTimeMillis()) == null
                ? View.INVISIBLE : View.VISIBLE);   // no programme (ViX): no bar
        track.post(() -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
            lp.width = Math.round(track.getWidth() * Math.max(0, Math.min(1, f)));
            progressFill.setLayoutParams(lp);
        });
    }

    // ---------------------------------------------------------------- rows

    /** The chip for Mis canales and the one for hidden channels; the others are Pluto's category names. */
    private static final String FAVORITES = "\u0001fav", HIDDEN = "\u0001hidden";

    private List<Live.Row> rows;
    private Map<String, Live.Channel> index;
    private String selected = FAVORITES;
    private LinearLayout chipsLine, cardsLine;
    private HorizontalScrollView cardsScroll;
    private TextView hint;
    private final Map<String, TextView> chips = new LinkedHashMap<>();
    /** True from a long press on a card until OK is released, so that release doesn't open the channel. */
    private boolean swallowOk;
    private FrameLayout sheet;

    private void onGuide(List<Live.Row> rows, String error) {
        if (!isAttachedToWindow() && getParent() == null) return;
        boolean hadFocus = findFocus() != null;
        if (rowsBox.getTag() instanceof android.animation.Animator) ((android.animation.Animator) rowsBox.getTag()).cancel();
        rowsBox.setTag(null);
        rowsBox.removeAllViews();
        if (error != null || rows.isEmpty()) {
            rowsBox.setAlpha(1f);
            status.setText(error != null ? error : "Pluto TV no tiene canales para esta región.");
            status.setVisibility(View.VISIBLE);
            return;
        }
        this.rows = rows;
        index = Live.index(rows);
        rowsBox.setAlpha(0f);
        rowsBox.animate().alpha(1f).setDuration(220).start();

        HorizontalScrollView chipsScroll = new HorizontalScrollView(getContext());
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setClipChildren(false);
        chipsScroll.setClipToPadding(false);
        chipsLine = new LinearLayout(getContext());
        chipsLine.setClipChildren(false);
        chipsLine.setClipToPadding(false);
        chipsLine.setPadding(dp(40), dp(4), dp(40), dp(4));
        chipsScroll.addView(chipsLine);
        rowsBox.addView(chipsScroll);

        cardsScroll = new HorizontalScrollView(getContext());
        cardsScroll.setHorizontalScrollBarEnabled(false);
        cardsScroll.setClipChildren(false);
        cardsScroll.setClipToPadding(false);
        cardsLine = new LinearLayout(getContext());
        cardsLine.setClipChildren(false);
        cardsLine.setClipToPadding(false);   // the focused card grows into the padding; don't cut its frame
        cardsLine.setPadding(dp(40), dp(10), dp(40), dp(8));
        cardsScroll.addView(cardsLine);
        rowsBox.addView(cardsScroll);

        hint = text(11, TEXT_DIM, regular);
        hint.setPadding(dp(40), dp(4), dp(40), 0);
        rowsBox.addView(hint);

        if (channels(FAVORITES).isEmpty()) selected = rows.get(0).title;
        buildChips();
        showCards(null);
        View first = cardsLine.getChildCount() > 0 ? cardsLine.getChildAt(0) : chips.get(selected);
        if (first != null) {
            if (hadFocus) first.post(first::requestFocus);   // in the bar, the focus stays there
            if (first.getTag() instanceof Live.Channel) showHero((Live.Channel) first.getTag(), true);
        }
    }

    /** The channels under a chip: Mis canales in their order, a category without the hidden ones, or the hidden. */
    private List<Live.Channel> channels(String key) {
        List<Live.Channel> out = new ArrayList<>();
        Set<String> hidden = Live.hidden(getContext());
        if (key.equals(FAVORITES)) {
            for (String id : Live.favorites(getContext())) {
                Live.Channel ch = index.get(id);
                if (ch != null && !hidden.contains(id)) out.add(ch);
            }
        } else if (key.equals(HIDDEN)) {
            for (String id : hidden) if (index.containsKey(id)) out.add(index.get(id));
        } else {
            for (Live.Row r : rows) {
                if (!r.title.equals(key)) continue;
                for (Live.Channel ch : r.channels) if (!hidden.contains(ch.id)) out.add(ch);
            }
        }
        return out;
    }

    /** "Mis canales · 8", every category with its count, and "Ocultos · 2" while some are hidden. */
    private void buildChips() {
        chipsLine.removeAllViews();
        chips.clear();
        List<String> keys = new ArrayList<>();
        keys.add(FAVORITES);
        for (Live.Row r : rows) if (!r.title.isEmpty()) keys.add(r.title);
        if (!channels(HIDDEN).isEmpty()) keys.add(HIDDEN);
        if (!keys.contains(selected)) selected = FAVORITES;
        for (String key : keys) {
            String label = key.equals(FAVORITES) ? "★  Mis canales" : key.equals(HIDDEN) ? "Ocultos" : key;
            TextView chip = text(13, TEXT, medium);
            chip.setText(label + "  ·  " + channels(key).size());
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setFocusable(true);
            chip.setId(View.generateViewId());
            styleChip(chip, false, key.equals(selected));
            chip.setOnFocusChangeListener((v, f) -> {
                styleChip(chip, f, key.equals(selected));
                if (f && !key.equals(selected)) {
                    // Focusing a chip shows its channels, without the focus leaving it.
                    String old = selected;
                    selected = key;
                    if (chips.get(old) != null) styleChip(chips.get(old), false, false);
                    showCards(null);
                    List<Live.Channel> list = channels(key);   // and the preview, with its first channel
                    if (!list.isEmpty()) {
                        handler.removeCallbacks(pendingHero);
                        pendingChannel = list.get(0);
                        handler.postDelayed(pendingHero, 250);
                    }
                }
            });
            chip.setOnClickListener(v -> {
                if (cardsLine.getChildCount() > 0) cardsLine.getChildAt(0).requestFocus();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(32));
            lp.rightMargin = dp(8);
            chipsLine.addView(chip, lp);
            chips.put(key, chip);
        }
        Rows.fence(chipsLine);
    }

    private void styleChip(TextView chip, boolean focused, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(focused ? 0xF2FFFFFF : selected ? 0x40FFFFFF : 0x1AFFFFFF);
        if (!focused && !selected) bg.setStroke(1, 0x33FFFFFF);
        chip.setBackground(bg);
        chip.setTextColor(focused ? 0xFF15171C : TEXT);
    }

    /** The selected chip's channels as one row of cards; keeps the focus on keepId's card if it is still there. */
    private void showCards(String keepId) {
        cardsLine.removeAllViews();
        List<Live.Channel> list = channels(selected);
        TextView chip = chips.get(selected);
        View keep = null;
        for (Live.Channel ch : list) {
            View card = card(ch);
            if (chip != null) card.setNextFocusUpId(chip.getId());   // up goes back to this chip, not a neighbour
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(184), dp(96));
            lp.rightMargin = dp(12);
            cardsLine.addView(card, lp);
            if (ch.id.equals(keepId)) keep = card;
        }
        cardsScroll.scrollTo(0, 0);
        if (list.isEmpty()) {
            TextView empty = text(13, TEXT_DIM, regular);
            empty.setText(selected.equals(FAVORITES) ? "Mantén OK en un canal de cualquier categoría para agregarlo aquí."
                    : "No hay canales aquí.");
            empty.setGravity(Gravity.CENTER_VERTICAL);
            cardsLine.addView(empty, new LinearLayout.LayoutParams(-2, dp(96)));
        }
        hint.setText(selected.equals(HIDDEN) ? "Mantén OK en un canal para volver a mostrarlo."
                : "OK abre el canal  ·  Mantén OK para Mis canales, ocultarlo o un recordatorio");
        Rows.fence(cardsLine);
        if (keep != null) keep.requestFocus();
    }

    private boolean onCardKey(View card, int keyCode, KeyEvent e) {
        boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
        if (!ok) return false;
        if (swallowOk) {
            if (e.getAction() == KeyEvent.ACTION_UP) swallowOk = false;
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 1) {
            swallowOk = true;
            openSheet((Live.Channel) card.getTag());
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- hold OK: what to do with a channel

    private void openSheet(Live.Channel ch) {
        closeSheet();
        FrameLayout root = this;
        sheet = new FrameLayout(getContext());
        sheet.setBackgroundColor(0x99000000);
        sheet.setClickable(true);
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(14));
        box.setBackground(round(0xFA161C26, dp(18)));
        TextView title = text(16, TEXT, medium);
        title.setText(ch.name);
        title.setPadding(dp(4), 0, 0, dp(10));
        box.addView(title);

        boolean fav = Live.favorites(getContext()).contains(ch.id);
        boolean hidden = Live.hidden(getContext()).contains(ch.id);
        View first = option(box, fav ? "Quitar de Mis canales" : "★  Agregar a Mis canales", () -> {
            Live.setFavorite(getContext(), ch.id, !fav);
            done(ch, fav ? "Se quitó de Mis canales: " + ch.name : "Se agregó a Mis canales: " + ch.name);
        });
        option(box, hidden ? "Volver a mostrar" : "Ocultar canal", () -> {
            Live.setHidden(getContext(), ch.id, !hidden);
            done(ch, hidden ? "Se volvió a mostrar: " + ch.name : "Se ocultó: " + ch.name + " (queda en Ocultos)");
        });
        Live.Program now = ch.at(System.currentTimeMillis());
        Live.Program next = now != null ? ch.after(now) : null;
        if (next != null) {
            String key = Reminders.key(ch.id, next.start);
            boolean set = Reminders.has(getContext(), key);
            String when = hm.format(new Date(next.start));
            option(box, set ? "Quitar recordatorio de «" + next.title + "»"
                    : "Recordar «" + next.title + "» · " + when, () -> {
                if (set) Reminders.remove(getContext(), key);
                else Reminders.add(getContext(), key, next.title, ch.name, ch.logo, next.start);
                done(ch, set ? "Recordatorio quitado." : "Te aviso 5 min antes de las " + when + ", aunque estés en otra app.");
            });
        }
        sheet.addView(box, new FrameLayout.LayoutParams(dp(380), -2, Gravity.CENTER));
        root.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
        first.requestFocus();
    }

    private View option(LinearLayout box, String label, Runnable action) {
        TextView t = text(14, TEXT, medium);
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14), 0, dp(14), 0);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(round(0x14FFFFFF, dp(12)));
        t.setOnFocusChangeListener((v, f) -> {
            t.setBackground(round(f ? 0xF2FFFFFF : 0x14FFFFFF, dp(12)));
            t.setTextColor(f ? 0xFF15171C : TEXT);
        });
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
        lp.topMargin = dp(6);
        box.addView(t, lp);
        return t;
    }

    /** After an option: the counts and the row change, the focus stays on the channel (or its neighbour). */
    private void done(Live.Channel ch, String message) {
        closeSheet();
        int at = -1;
        for (int i = 0; i < cardsLine.getChildCount(); i++) if (cardsLine.getChildAt(i).getTag() == ch) at = i;
        buildChips();
        showCards(ch.id);
        View focus = findFocus();
        if ((focus == null || focus.getTag() != ch) && cardsLine.getChildCount() > 0) {
            View near = cardsLine.getChildAt(Math.max(0, Math.min(at, cardsLine.getChildCount() - 1)));
            if (near.isFocusable()) near.requestFocus();
            else if (chips.get(selected) != null) chips.get(selected).requestFocus();
        }
        hint.setText(message);
    }

    private void closeSheet() {
        if (sheet != null && sheet.getParent() != null) ((ViewGroup) sheet.getParent()).removeView(sheet);
        sheet = null;
    }

    /** Back while the channel menu is open closes it; true when it did. */
    boolean back() {
        if (sheet == null) return false;
        closeSheet();
        showCards(null);
        return true;
    }

    private View card(Live.Channel ch) {
        FrameLayout card = new FrameLayout(getContext());
        card.setTag(ch);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(round(0x26FFFFFF, dp(12)));
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(10), dp(12), dp(8));

        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(getContext());
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        top.addView(logo, new LinearLayout.LayoutParams(0, dp(22), 1f));
        Logos.into(logo, ch.logo, 0);
        long now = System.currentTimeMillis();
        Live.Program p = ch.at(now);
        boolean star = Live.favorites(getContext()).contains(ch.id) && !selected.equals(FAVORITES);   // in Mis canales
        boolean vix = !ch.vix.isEmpty();
        if (p != null || star || vix) {
            TextView on = text(9, vix ? 0xFFFF9A62 : p != null && p.live ? 0xFFFF8A80 : TEXT_DIM, medium);
            on.setLetterSpacing(0.08f);
            String state = vix ? "ViX" : p == null ? "" : p.live ? "EN VIVO" : "AHORA";
            on.setText(star ? (state.isEmpty() ? "★" : "★  " + state) : state);
            top.addView(on);
        }
        col.addView(top);

        TextView name = text(13, TEXT, medium);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(p != null ? p.title : ch.name);
        name.setPadding(0, dp(8), 0, 0);
        col.addView(name);
        FrameLayout track = new FrameLayout(getContext());
        track.setBackground(round(0x33FFFFFF, dp(1)));
        View fill = new View(getContext());
        fill.setBackground(round(0xE6FFFFFF, dp(1)));
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dp(2));
        tlp.topMargin = dp(7);
        col.addView(track, tlp);
        if (p != null) {
            float f = (float) (now - p.start) / (p.stop - p.start);
            track.post(() -> {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                flp.width = Math.round(track.getWidth() * Math.max(0, Math.min(1, f)));
                fill.setLayoutParams(flp);
            });
        }
        TextView when = text(10, TEXT_DIM, regular);
        when.setSingleLine(true);
        when.setEllipsize(TextUtils.TruncateAt.END);
        when.setPadding(0, dp(6), 0, 0);
        if (p != null) {
            Live.Program n = ch.after(p);
            when.setText(hm.format(new Date(p.start)) + "–" + hm.format(new Date(p.stop))
                    + (n != null ? "  ·  Sigue: " + n.title : ""));
        } else {
            when.setText(vix ? "Abre en la app de ViX" : ch.name);
        }
        col.addView(when);
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));

        View ring = new View(getContext());
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        card.setOnFocusChangeListener((v, f) -> {
            GradientDrawable b = new GradientDrawable();
            b.setCornerRadius(dp(12));
            b.setStroke(dp(3), Color.WHITE);
            ring.setBackground(f ? b : null);
            v.setBackground(round(f ? 0x40FFFFFF : 0x26FFFFFF, dp(12)));
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).translationZ(f ? dp(8) : 0).setDuration(140).start();
            if (f) {
                handler.removeCallbacks(pendingHero);
                pendingChannel = ch;
                handler.postDelayed(pendingHero, 160);   // not on every card while scrolling fast
            }
        });
        card.setOnClickListener(v -> Live.open(getContext(), ch));
        card.setOnKeyListener(this::onCardKey);
        return card;
    }

    private Live.Channel pendingChannel;
    private final Runnable pendingHero = () -> {
        if (pendingChannel != null) showHero(pendingChannel, true);
    };

    // ---------------------------------------------------------------- helpers

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private TextView text(float sp, int color, Typeface tf) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
