package it.paddeu.quantomanca;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.Toast;

/** Eliminazione di un appuntamento, con conferma. Per gli eventi ripetuti chiede se solo questo o tutta la serie. */
public final class EventActions {

    private EventActions() {}

    public static boolean canWrite(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isRecurring(Context c, long eventId) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    new String[]{CalendarContract.Events.RRULE, CalendarContract.Events.RDATE}, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                return !cur.isNull(0) && !cur.getString(0).isEmpty()
                        || !cur.isNull(1) && !cur.getString(1).isEmpty();
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return false;
    }

    private static boolean deleteSeries(Context c, long eventId) {
        Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        return c.getContentResolver().delete(u, null, null) > 0;
    }

    private static boolean deleteOccurrence(Context c, long eventId, long rawBegin) {
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, rawBegin);
        v.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED);
        Uri u = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId);
        return c.getContentResolver().insert(u, v) != null;
    }

    /**
     * Chiede conferma e cancella. {@code after} viene eseguito sempre alla fine (cancellato o annullato).
     */
    public static void confirmDelete(Activity a, long eventId, long rawBegin, String title, Runnable after) {
        if (!canWrite(a)) {
            Toast.makeText(a, "Consenti la modifica del calendario, poi riprova", Toast.LENGTH_LONG).show();
            a.requestPermissions(new String[]{Manifest.permission.WRITE_CALENDAR}, 9);
            if (after != null) after.run();
            return;
        }
        boolean rec = isRecurring(a, eventId);
        AlertDialog.Builder b = new AlertDialog.Builder(a, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("Eliminare l'appuntamento?")
                .setMessage(title + (rec ? "\n\nÈ un evento che si ripete." : ""))
                .setOnDismissListener(d -> { if (after != null) after.run(); });

        if (rec) {
            b.setPositiveButton("Solo questo", (d, w) -> done(a, deleteOccurrence(a, eventId, rawBegin)));
            b.setNeutralButton("Tutta la serie", (d, w) -> done(a, deleteSeries(a, eventId)));
            b.setNegativeButton("Annulla", null);
        } else {
            b.setPositiveButton("Elimina", (d, w) -> done(a, deleteSeries(a, eventId)));
            b.setNegativeButton("Annulla", null);
        }
        b.show();
    }

    private static void done(Context c, boolean ok) {
        Toast.makeText(c, ok ? "Eliminato" : "Non è stato possibile eliminarlo", Toast.LENGTH_SHORT).show();
        CountdownWidget.updateAll(c);
    }
}
