package it.paddeu.quantomanca;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Ogni mattina alle 8:30: notifica con gli appuntamenti importanti (stellina, «!» o rosso). */
public class DailyReceiver extends BroadcastReceiver {

    static final String ACTION_DAILY = "it.paddeu.quantomanca.DAILY";
    private static final String CHANNEL = "importanti";
    private static final int HOUR = 8, MINUTE = 30;

    @Override
    public void onReceive(Context c, Intent i) {
        final Context app = c.getApplicationContext();
        if (ACTION_DAILY.equals(i.getAction())) {
            final PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    notifyNow(app);
                    schedule(app);
                } finally {
                    pr.finish();
                }
            }).start();
        } else {
            schedule(app);   // riavvio, cambio ora/fuso
        }
    }

    /** Programma la prossima notifica delle 8:30 (oggi se non è ancora passata, altrimenti domani). */
    public static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, HOUR);
        t.set(Calendar.MINUTE, MINUTE);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis() + 30_000) t.add(Calendar.DAY_OF_YEAR, 1);
        Intent in = new Intent(c, DailyReceiver.class).setAction(ACTION_DAILY);
        PendingIntent pi = PendingIntent.getBroadcast(c, 100, in,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        // funziona anche col telefono in risparmio energetico; può arrivare con qualche minuto di ritardo
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
    }

    public static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    /** Costruisce e mostra la notifica. Restituisce quanti importanti ha trovato. */
    public static int notifyNow(Context c) {
        List<Ev> imp = new ArrayList<>();
        for (Ev e : EventRepo.load(c)) if (e.important) imp.add(e);
        if (imp.isEmpty() || !canNotify(c)) return imp.size();

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Impegni importanti",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Riepilogo delle 8:30 con gli appuntamenti con la stellina");
        nm.createNotificationChannel(ch);

        long now = System.currentTimeMillis();
        Notification.InboxStyle style = new Notification.InboxStyle();
        int n = 0;
        for (Ev e : imp) {
            if (n++ >= 7) break;
            style.addLine("★ " + e.title + " — " + Fmt.big(e, now).toLowerCase() + " (" + Fmt.whenShort(e) + ")");
        }
        if (imp.size() > 7) style.setSummaryText("e altri " + (imp.size() - 7));

        Ev first = imp.get(0);
        String title = imp.size() == 1 ? "★ 1 impegno importante" : "★ " + imp.size() + " impegni importanti";
        String text = first.title + " — " + Fmt.big(first, now).toLowerCase();
        style.setBigContentTitle(title);

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 101, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notif = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFFC96442)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(style)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.notify(1, notif);
        return imp.size();
    }
}
