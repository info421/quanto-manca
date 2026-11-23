package it.paddeu.quantomanca;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Il widget della schermata Home. */
public class CountdownWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "it.paddeu.quantomanca.TICK";
    static final String ACTION_REFRESH = "it.paddeu.quantomanca.REFRESH";
    private static final long INTERVAL = 15 * 60 * 1000L;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c, id));
        m.notifyAppWidgetViewDataChanged(ids, R.id.list);
        schedule(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i.getAction();
        if (a == null) return;
        switch (a) {
            case ACTION_TICK:
            case ACTION_REFRESH:
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_LOCALE_CHANGED:
                updateAll(c);
                schedule(c);
                break;
            default:
                break;
        }
    }

    @Override
    public void onEnabled(Context c) {
        schedule(c);
    }

    @Override
    public void onDisabled(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(tickIntent(c));
    }

    /** Ridisegna tutti i widget presenti sulla Home. */
    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, CountdownWidget.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) m.updateAppWidget(id, build(c, id));
        m.notifyAppWidgetViewDataChanged(ids, R.id.list);
    }

    private static PendingIntent tickIntent(Context c) {
        Intent t = new Intent(c, CountdownWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, 0, t,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Aggiornamento ogni 15 minuti circa, solo quando il telefono è acceso (non consuma batteria). */
    private static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL, INTERVAL, tickIntent(c));
    }

    private static RemoteViews build(Context c, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);

        // Lista appuntamenti
        Intent svc = new Intent(c, WidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        rv.setRemoteAdapter(R.id.list, svc);
        rv.setEmptyView(R.id.list, R.id.empty);

        boolean ok = EventRepo.hasPermission(c);
        rv.setTextViewText(R.id.empty, ok
                ? "Nessun appuntamento nei prossimi " + EventRepo.DAYS_AHEAD + " giorni"
                : "Tocca qui e consenti l'accesso al calendario");

        // Apri l'app (titolo e messaggio vuoto)
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent piOpen = PendingIntent.getActivity(c, 10, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.header_title, piOpen);
        rv.setOnClickPendingIntent(R.id.empty, piOpen);

        // + Nuovo appuntamento → schermata "Nuovo evento" del calendario
        Intent ins = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent piIns = PendingIntent.getActivity(c, 11, ins,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.btn_add, piIns);

        // ↻ Aggiorna
        Intent ref = new Intent(c, CountdownWidget.class).setAction(ACTION_REFRESH);
        PendingIntent piRef = PendingIntent.getBroadcast(c, 12, ref,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.btn_refresh, piRef);

        // Tocco su un appuntamento → lo apre nel calendario
        Intent view = new Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent piView = PendingIntent.getActivity(c, 13, view,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setPendingIntentTemplate(R.id.list, piView);

        rv.setTextViewText(R.id.updated,
                new SimpleDateFormat("HH:mm", Locale.ITALIAN).format(new Date()));
        return rv;
    }
}
