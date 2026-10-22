package com.xeleo.tv;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Quick settings > Acerca de: the version, who made XELEO TV and its MIT license, so the notice travels inside
 * every APK (also in copies others build from the repo), and where the data comes from.
 */
public class AboutActivity extends Activity {

    private static final int CARD = 0xFF131D23;
    private static final int TEXT = 0xFFE3E3E3;
    private static final int TEXT_DIM = 0xFFC4C7C5;

    static final String COPYRIGHT = "© 2026 Sergio Escobedo";
    static final String REPO = "github.com/serginiesc/xeleo-tv";
    private static final String LICENSE = "MIT License\n\nCopyright (c) 2026 Sergio Escobedo\n\n"
            + "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and "
            + "associated documentation files (the \"Software\"), to deal in the Software without restriction, including "
            + "without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell "
            + "copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the "
            + "following conditions:\n\nThe above copyright notice and this permission notice shall be included in all "
            + "copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF "
            + "ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A "
            + "PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY "
            + "CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT "
            + "OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.";

    private float density;
    private Typeface regular, medium;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        getWindow().setBackgroundDrawable(new ColorDrawable(0x99000000));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(22), dp(26), dp(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);

        TextView name = text(24, TEXT, medium);
        name.setText("XELEO TV");
        card.addView(name);
        TextView version = text(13, TEXT_DIM, regular);
        version.setText("Versión " + version());
        version.setPadding(0, dp(4), 0, dp(14));
        card.addView(version);

        TextView who = text(14, TEXT, regular);
        who.setText(COPYRIGHT + "\n" + REPO);
        who.setLineSpacing(dp(3), 1f);
        card.addView(who);
        TextView free = text(12.5f, TEXT_DIM, regular);
        free.setText("Software libre con licencia MIT: puedes usarlo, cambiarlo y compartirlo, siempre que conserves "
                + "este aviso y el nombre de su autor. Si alguien te pasó una versión cambiada, también debe decirlo.");
        free.setLineSpacing(dp(2), 1f);
        free.setPadding(0, dp(12), 0, dp(12));
        card.addView(free);

        TextView data = text(12, TEXT_DIM, regular);
        data.setText("Datos: ESPN (deportes), Pluto TV y ViX (En vivo), Open-Meteo (clima). XELEO solo muestra su "
                + "información y abre sus apps; no transmite video.\nFuente Outfit (SIL OFL 1.1) · Íconos Material "
                + "(Apache 2.0).");
        data.setLineSpacing(dp(2), 1f);
        data.setPadding(0, 0, 0, dp(14));
        card.addView(data);

        TextView license = text(10.5f, 0xB3C4C7C5, regular);
        license.setText(LICENSE);
        license.setLineSpacing(dp(2), 1f);
        license.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable box = new GradientDrawable();
        box.setColor(0xFF1C262C);
        box.setCornerRadius(dp(12));
        license.setBackground(box);
        card.addView(license);

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(true);   // ▲▼ scroll the license
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(card);
        FrameLayout root = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(560), -2, Gravity.CENTER);
        lp.setMargins(0, dp(20), 0, dp(20));
        root.addView(scroll, lp);
        setContentView(root);
        scroll.requestFocus();
    }

    /** "1.15" from the APK. */
    String version() {
        try {
            PackageInfo p = getPackageManager().getPackageInfo(getPackageName(), 0);
            return p.versionName;
        } catch (Exception e) {
            return "?";
        }
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
