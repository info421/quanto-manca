package it.paddeu.quantomanca;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Schermata dell'app: permesso al calendario, aggiunta del widget, anteprima. */
public class MainActivity extends Activity {

    private static final int REQ_CAL = 7;

    private Button permBtn;
    private LinearLayout updBox;
    private TextView updText;
    private Button updBtn;
    private boolean installing = false;
    private TextView status;
    private LinearLayout preview;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("Quanto manca");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("I tuoi prossimi appuntamenti in ordine di quanto manca, in un widget sulla Home. "
                + "Legge i calendari sincronizzati sul telefono (Google Calendar compreso).");
        info.setTextSize(15);
        info.setPadding(0, dp(8), 0, dp(18));
        root.addView(info);

        // Riquadro aggiornamento (nascosto finché non c'è una versione nuova)
        updBox = new LinearLayout(this);
        updBox.setOrientation(LinearLayout.VERTICAL);
        updBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        updBox.setBackgroundColor(Fmt.BLUE);
        updText = new TextView(this);
        updText.setTextColor(0xFFFFFFFF);
        updText.setTextSize(15);
        updText.setTypeface(Typeface.DEFAULT_BOLD);
        updBox.addView(updText);
        updBtn = new Button(this);
        updBtn.setText("Aggiorna adesso");
        updBtn.setOnClickListener(v -> startUpdate());
        updBox.addView(updBtn);
        updBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ulp.bottomMargin = dp(16);
        root.addView(updBox, ulp);

        permBtn = new Button(this);
        permBtn.setText("Consenti accesso al calendario");
        permBtn.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQ_CAL));
        root.addView(permBtn);

        Button pin = new Button(this);
        pin.setText("Aggiungi il widget alla Home");
        pin.setOnClickListener(v -> pinWidget());
        root.addView(pin);

        Button add = new Button(this);
        add.setText("+ Nuovo appuntamento");
        add.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI));
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(this, "Nessuna app calendario trovata", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(add);

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(18), 0, dp(6));
        root.addView(status);

        preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        root.addView(preview);

        TextView ver = new TextView(this);
        ver.setText("Versione " + BuildConfig.VERSION_NAME);
        ver.setTextSize(11);
        ver.setPadding(0, dp(24), 0, 0);
        ver.setAlpha(0.6f);
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

    private void showUpdate() {
        if (installing) return;
        if (Updater.available(this)) {
            String n = Updater.latestName(this);
            updText.setText("Nuova versione disponibile" + (n.isEmpty() ? "" : " (" + n + ")"));
            updBtn.setEnabled(true);
            updBtn.setText("Aggiorna adesso");
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
                runOnUiThread(() -> updBtn.setText("Scarico… " + p + "%"));
            }
            @Override public void onError(String m) {
                runOnUiThread(() -> {
                    installing = false;
                    Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
                    showUpdate();
                });
            }
        })).start();
        // al termine del download Android mostra la conferma di installazione
        updBtn.postDelayed(() -> { installing = false; }, 60_000);
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
        permBtn.setVisibility(ok ? View.GONE : View.VISIBLE);
        preview.removeAllViews();
        if (!ok) {
            status.setText("Serve il permesso di leggere il calendario.");
            return;
        }
        CountdownWidget.updateAll(this);

        List<Ev> list = EventRepo.load(this);
        long now = System.currentTimeMillis();
        status.setText(list.isEmpty() ? "Nessun appuntamento nei prossimi " + EventRepo.DAYS_AHEAD + " giorni."
                : "Prossimi appuntamenti");
        int n = Math.min(10, list.size());
        for (int i = 0; i < n; i++) {
            Ev e = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(8), 0, dp(8));

            View bar = new View(this);
            bar.setBackgroundColor(Fmt.color(e, now));
            row.addView(bar, new LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT));

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setPadding(dp(10), 0, dp(8), 0);
            TextView t = new TextView(this);
            t.setText(e.title);
            t.setTextSize(15);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            TextView w = new TextView(this);
            w.setText(Fmt.when(e));
            w.setTextSize(12);
            mid.addView(t);
            mid.addView(w);
            row.addView(mid, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView big = new TextView(this);
            big.setText(Fmt.big(e, now));
            big.setTextColor(Fmt.color(e, now));
            big.setTypeface(Typeface.DEFAULT_BOLD);
            big.setTextSize(14);
            row.addView(big);

            preview.addView(row);
        }
    }
}
