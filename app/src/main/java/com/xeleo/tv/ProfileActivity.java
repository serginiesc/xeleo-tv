package com.xeleo.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Quick settings > Personalizar: change the TV's name and the name in the greeting. */
public class ProfileActivity extends Activity {

    private static final int CARD = 0xFF131D23;
    private static final int FIELD = 0xFF1C262C;
    private static final int TEXT = 0xFFE3E3E3;
    private static final int TEXT_DIM = 0xFFC4C7C5;
    private static final int FOCUS = 0xFFE3E3E3;
    private static final int DARK = 0xFF1F1F1F;

    private float density;
    private Typeface regular, medium;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        getWindow().setBackgroundDrawable(new ColorDrawable(0x99000000));

        FrameLayout root = new FrameLayout(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(22), dp(24), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        root.addView(card, new FrameLayout.LayoutParams(dp(380), -2, Gravity.CENTER));

        TextView title = text(20, Color.WHITE, medium);
        title.setText("Personalizar");
        card.addView(title);
        TextView hint = text(12, TEXT_DIM, regular);
        hint.setText("Cómo se llama tu tele y a quién saluda el inicio.");
        hint.setPadding(0, dp(6), 0, dp(14));
        card.addView(hint);

        EditText tvName = field(card, "Nombre de la tele", Profile.tvName(this));
        EditText userName = field(card, "Nombre en el saludo (vacío: sin nombre)", Profile.userName(this));
        EditText city = field(card, "Ciudad del clima", Profile.weatherCity(this));
        city.setImeOptions(EditorInfo.IME_ACTION_DONE);
        // Halloween, Día de Muertos… on Home on their days (SeasonView); OK switches it, saved at once.
        TextView season = button(seasonLabel(), null);
        season.setGravity(Gravity.CENTER_VERTICAL);
        season.setPadding(dp(14), 0, dp(14), 0);
        season.setOnClickListener(v -> {
            SeasonView.setEnabled(this, !SeasonView.enabled(this));
            season.setText(seasonLabel());
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(40));
        slp.topMargin = dp(14);
        card.addView(season, slp);
        status = text(12, TEXT_DIM, regular);
        status.setPadding(dp(2), dp(8), 0, 0);
        card.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(18), 0, 0);
        buttons.addView(button("Cancelar", v -> finish()));
        TextView save = button("Guardar", v -> {
            Profile.save(this, tvName.getText().toString(), userName.getText().toString());
            String wanted = city.getText().toString().trim();
            if (wanted.isEmpty() || wanted.equalsIgnoreCase(Profile.weatherCity(this))) {
                setResult(RESULT_OK);
                finish();
            } else {
                findCity(wanted);
            }
        });
        buttons.addView(save);
        card.addView(buttons);

        setContentView(root);
        save.post(save::requestFocus);
    }

    private TextView status;
    private boolean searching;

    private String seasonLabel() {
        return "Temas de temporada (Navidad, 15 de septiembre…):  " + (SeasonView.enabled(this) ? "Encendidos" : "Apagados");
    }

    /** Looks the city up (Open-Meteo geocoding, no key), saves the first match and closes; says so if none. */
    private void findCity(String name) {
        if (searching) return;
        searching = true;
        status.setTextColor(TEXT_DIM);
        status.setText("Buscando " + name + "…");
        new Thread(() -> {
            boolean ok = false;
            try {
                java.net.URL url = new java.net.URL("https://geocoding-api.open-meteo.com/v1/search?count=1&language=es&format=json&name="
                        + java.net.URLEncoder.encode(name, "UTF-8"));
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                try (java.io.InputStream in = conn.getInputStream()) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    org.json.JSONArray results = new org.json.JSONObject(out.toString("UTF-8")).optJSONArray("results");
                    if (results != null && results.length() > 0) {
                        org.json.JSONObject r = results.getJSONObject(0);
                        String cityName = r.getString("name");
                        String region = r.optString("admin1", "");
                        double la = r.getDouble("latitude"), lo = r.getDouble("longitude");
                        String place = region.isEmpty() ? cityName : cityName + ", " + region;
                        ok = true;
                        runOnUiThread(() -> {
                            Profile.saveLocation(this, cityName, la, lo);
                            status.setTextColor(0xFF8AB4F8);
                            status.setText("Listo: " + place);
                            status.postDelayed(() -> { setResult(RESULT_OK); finish(); }, 1200);
                        });
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) {
                // Shown below as not found.
            }
            if (!ok) {
                runOnUiThread(() -> {
                    searching = false;
                    status.setTextColor(0xFFF28B82);
                    status.setText("No encontré " + name + ". Revisa el nombre o la conexión.");
                });
            }
        }, "geocode").start();
    }

    private EditText field(LinearLayout parent, String label, String value) {
        TextView l = text(12, TEXT_DIM, regular);
        l.setText(label);
        l.setPadding(dp(2), dp(6), 0, dp(6));
        parent.addView(l);
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        e.setTextColor(Color.WHITE);
        e.setTextSize(16);
        e.setTypeface(regular);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        styleField(e, false);
        e.setOnFocusChangeListener((v, f) -> styleField((EditText) v, f));
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private void styleField(EditText e, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(FIELD);
        bg.setStroke(dp(focused ? 2 : 1), focused ? 0xFF8AB4F8 : 0x33FFFFFF);
        e.setBackground(bg);
    }

    private TextView button(String label, View.OnClickListener click) {
        TextView b = text(14, TEXT, medium);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        styleButton(b, false);
        b.setOnFocusChangeListener((v, f) -> styleButton((TextView) v, f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.leftMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void styleButton(TextView b, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(19));
        bg.setColor(focused ? FOCUS : FIELD);
        b.setBackground(bg);
        b.setTextColor(focused ? DARK : TEXT);
    }

    private TextView text(float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
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
