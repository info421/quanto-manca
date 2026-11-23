package it.paddeu.quantomanca;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Schermata dell'app: lista degli appuntamenti con stellina (importanti) ed eliminazione. */
public class MainActivity extends Activity {

    private static final int REQ_CAL = 7;

    private static final int BG = 0xFFF5F4EF;
    private static final int CARD = 0xFFFFFFFF;
    private static final int LINE = 0xFFE6E3DA;
    private static final int INK = 0xFF1F1E1D;
    private static final int MUTED = 0xFF75736C;
    private static final int ACCENT = 0xFFC96442;
    private static final int GOLD = 0xFFE0A800;

    private TextView permBtn;
    private LinearLayout updBox;
    private TextView updText;
    private TextView updBtn;
    private TextView status;
    private LinearLayout list;
    private boolean installing = false;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private GradientDrawable rounded(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    /** Pulsante a pillola: pieno (colore accento) o chiaro con bordo. */
    private TextView pill(String text, boolean filled) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(11), dp(16), dp(11));
        b.setTextColor(filled ? 0xFFFFFFFF : INK);
        b.setBackground(rounded(filled ? ACCENT : CARD, filled ? 0 : LINE, 22));
        b.setClickable(true);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        sv.addView(root);

        // Intestazione: titolo + pulsanti
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Quanto manca");
        title.setTextSize(26);
        title.setTextColor(INK);
        title.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView add = pill("+ Nuovo", true);
        add.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI));
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(this, "Nessuna app calendario trovata", Toast.LENGTH_LONG).show();
            }
        });
        head.addView(add);
        root.addView(head);

        // Riquadro aggiornamento
        updBox = new LinearLayout(this);
        updBox.setOrientation(LinearLayout.HORIZONTAL);
        updBox.setGravity(Gravity.CENTER_VERTICAL);
        updBox.setPadding(dp(14), dp(10), dp(10), dp(10));
        updBox.setBackground(rounded(0xFFFBEDE6, 0xFFF0CDBE, 14));
        updText = new TextView(this);
        updText.setTextColor(INK);
        updText.setTextSize(13.5f);
        updText.setTypeface(Typeface.DEFAULT_BOLD);
        updBox.addView(updText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        updBtn = pill("Aggiorna", true);
        updBtn.setOnClickListener(v -> startUpdate());
        updBox.addView(updBtn);
        updBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = dp(14);
        root.addView(updBox, ulp);

        // Permesso
        permBtn = pill("Consenti accesso al calendario", true);
        permBtn.setOnClickListener(v -> requestPermissions(new String[]{
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_CAL));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(14);
        root.addView(permBtn, plp);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(MUTED);
        status.setPadding(dp(2), dp(14), 0, 0);
        root.addView(status);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        // Fondo: widget + versione
        TextView pin = pill("Aggiungi il widget alla Home", false);
        pin.setOnClickListener(v -> pinWidget());
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(22);
        root.addView(pin, wlp);

        TextView ver = new TextView(this);
        ver.setText("☆ tocca la stellina per mettere un impegno in cima · ✕ per eliminarlo\nVersione " + BuildConfig.VERSION_NAME);
        ver.setTextSize(11.5f);
        ver.setTextColor(MUTED);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(14), 0, 0);
        root.addView(ver);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        showUpdate();
        new Thread(() -> {
            Updater.check(getApplicationContext(), true);
            runOnUiThread(() -> { showUpdate(); CountdownWidget.updateAll(this); });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refresh();
    }

    private void pinWidget() {
        AppWidgetManager m = getSystemService(AppWidgetManager.class);
        if (m != null && m.isRequestPinAppWidgetSupported()) {
            m.requestPinAppWidget(new ComponentName(this, CountdownWidget.class), null, null);
        } else {
            Toast.makeText(this, "Tieni premuto sulla Home → Widget → Quanto manca", Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        boolean ok = EventRepo.hasPermission(this);
        permBtn.setVisibility(ok && EventActions.canWrite(this) ? View.GONE : View.VISIBLE);
        list.removeAllViews();
        if (!ok) {
            status.setText("Serve il permesso di leggere il calendario.");
            return;
        }
        CountdownWidget.updateAll(this);

        List<Ev> evs = EventRepo.load(this);
        long now = System.currentTimeMillis();
        status.setText(evs.isEmpty() ? "Nessun appuntamento nei prossimi " + EventRepo.DAYS_AHEAD + " giorni." : "");
        status.setVisibility(evs.isEmpty() ? View.VISIBLE : View.GONE);

        for (Object o : WidgetService.rows(evs)) {
            if (o instanceof String) {
                TextView h = new TextView(this);
                h.setText(((String) o).toUpperCase());
                h.setTextSize(11.5f);
                h.setLetterSpacing(0.08f);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setTextColor(MUTED);
                h.setPadding(dp(4), dp(18), 0, dp(6));
                list.addView(h);
                continue;
            }
            list.addView(row((Ev) o, now));
        }
    }

    private View row(Ev e, long now) {
        int col = Fmt.color(e, now);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.setBackground(rounded(e.important ? Fmt.tint(col) : CARD, e.important ? 0 : LINE, 14));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(8);
        row.setLayoutParams(rlp);

        // Stellina
        TextView star = new TextView(this);
        star.setText(e.important ? "★" : "☆");
        star.setTextColor(e.important ? GOLD : 0xFFB5B2A8);
        star.setTextSize(22);
        star.setGravity(Gravity.CENTER);
        final String k = Stars.key(e);
        final boolean was = e.important;
        star.setOnClickListener(v -> { Stars.toggle(this, k, was); refresh(); });
        row.addView(star, new LinearLayout.LayoutParams(dp(46), dp(52)));

        // Barra urgenza
        View bar = new View(this);
        bar.setBackground(rounded(col, 0, 2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(34));
        blp.rightMargin = dp(10);
        row.addView(bar, blp);

        // Titolo + quando
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(0, dp(6), dp(6), dp(6));
        TextView t = new TextView(this);
        t.setText(e.title);
        t.setTextSize(15);
        t.setTextColor(INK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView w = new TextView(this);
        w.setText(Fmt.when(e));
        w.setTextSize(12);
        w.setTextColor(MUTED);
        mid.addView(t);
        mid.addView(w);
        mid.setOnClickListener(v -> openEvent(e));
        row.addView(mid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Quanto manca
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView big = new TextView(this);
        big.setText((Fmt.live(e, now) ? "● " : "") + Fmt.big(e, now));
        big.setTextColor(col);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        big.setTextSize(13.5f);
        TextView small = new TextView(this);
        small.setText(Fmt.small(e, now));
        small.setTextColor(MUTED);
        small.setTextSize(11.5f);
        right.addView(big);
        right.addView(small);
        right.setOnClickListener(v -> openEvent(e));
        row.addView(right);

        // Elimina
        TextView del = new TextView(this);
        del.setText("✕");
        del.setTextSize(15);
        del.setGravity(Gravity.CENTER);
        if (e.canEdit) {
            del.setTextColor(0xFFA8A59C);
            del.setOnClickListener(v -> EventActions.confirmDelete(this, e.eventId, e.rawBegin, e.title, this::refresh));
        } else {
            del.setTextColor(0x00000000);   // calendario di sola lettura: niente elimina
        }
        row.addView(del, new LinearLayout.LayoutParams(dp(40), dp(52)));

        return row;
    }

    private void openEvent(Ev e) {
        Intent view = new Intent(Intent.ACTION_VIEW,
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId));
        view.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.rawBegin);
        view.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.rawEnd);
        try { startActivity(view); } catch (ActivityNotFoundException ignored) {}
    }

    // ---- Aggiornamenti ----

    private void showUpdate() {
        if (installing) return;
        if (Updater.available(this)) {
            String n = Updater.latestName(this);
            updText.setText("Nuova versione" + (n.isEmpty() ? "" : " " + n));
            updBtn.setEnabled(true);
            updBtn.setText("Aggiorna");
            updBox.setVisibility(View.VISIBLE);
        } else {
            updBox.setVisibility(View.GONE);
        }
    }

    private void startUpdate() {
        if (installing) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Attiva «Consenti da questa fonte», poi torna indietro e tocca di nuovo Aggiorna",
                    Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException ignored) {}
            return;
        }
        installing = true;
        updBtn.setEnabled(false);
        updBtn.setText("Scarico…");
        new Thread(() -> Updater.downloadAndInstall(getApplicationContext(), new Updater.Progress() {
            @Override public void onProgress(int p) {
                runOnUiThread(() -> updBtn.setText(p + "%"));
            }
            @Override public void onError(String m) {
                runOnUiThread(() -> {
                    installing = false;
                    Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
                    showUpdate();
                });
            }
        })).start();
        updBtn.postDelayed(() -> { installing = false; }, 60_000);
    }
}
