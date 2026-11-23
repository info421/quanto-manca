package it.paddeu.quantomanca;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/** Legge i prossimi appuntamenti da tutti i calendari visibili del telefono. */
public final class EventRepo {

    public static final int DAYS_AHEAD = 90;
    public static final int MAX_ITEMS = 60;

    private EventRepo() {}

    public static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<Ev> load(Context c) {
        List<Ev> out = new ArrayList<>();
        if (!hasPermission(c)) return out;

        long now = System.currentTimeMillis();
        long from = now - 2L * 24 * 3600 * 1000;           // margine per eventi di più giorni già iniziati
        long to = now + DAYS_AHEAD * 24L * 3600 * 1000;

        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, from);
        ContentUris.appendId(b, to);

        String[] proj = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.STATUS,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                CalendarContract.Instances.OWNER_ACCOUNT,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Instances.EVENT_COLOR_KEY
        };
        String sel = CalendarContract.Instances.VISIBLE + "=1";

        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(b.build(), proj, sel, null,
                    CalendarContract.Instances.BEGIN + " ASC");
            if (cur == null) return out;
            while (cur.moveToNext()) {
                int status = cur.isNull(6) ? -1 : cur.getInt(6);
                int self = cur.isNull(7) ? -1 : cur.getInt(7);
                if (status == CalendarContract.Events.STATUS_CANCELED) continue;
                if (self == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue;

                Ev e = new Ev();
                e.eventId = cur.getLong(0);
                e.title = cur.isNull(1) ? "" : cur.getString(1).trim();
                if (e.title.startsWith("!")) {
                    e.important = true;
                    e.title = e.title.replaceFirst("^!+\\s*", "");
                }
                if (e.title.contains("⭐")) {
                    e.important = true;
                    e.title = e.title.replace("⭐", "").trim();
                }
                String colorKey = cur.isNull(11) ? "" : cur.getString(11);
                if ("11".equals(colorKey)) e.important = true;   // Google Calendar: Pomodoro (rosso)
                if (e.title.isEmpty()) e.title = "(senza titolo)";
                e.rawBegin = cur.getLong(2);
                e.rawEnd = cur.getLong(3);
                e.allDay = cur.getInt(4) == 1;
                e.location = cur.isNull(5) ? "" : cur.getString(5).trim();

                if (e.allDay) {
                    e.begin = utcMidnightToLocal(e.rawBegin);
                    e.end = utcMidnightToLocal(e.rawEnd);
                } else {
                    e.begin = e.rawBegin;
                    e.end = e.rawEnd;
                }
                if (e.end <= e.begin) e.end = e.begin + 60_000;
                if (e.end <= now) continue;   // già finito

                String owner = cur.isNull(8) ? "" : cur.getString(8);
                String calName = cur.isNull(9) ? "" : cur.getString(9);
                int access = cur.isNull(10) ? 0 : cur.getInt(10);
                e.sharedWith = attendees(c, e.eventId, owner);
                if (!e.sharedWith.isEmpty()) {
                    e.sharedByAttendees = true;
                } else if (isOthersCalendar(owner, access)) {
                    e.sharedWith = calName.isEmpty() ? owner : calName;
                    e.sharedByAttendees = false;
                }
                out.add(e);
            }
        } catch (RuntimeException ignored) {
            // permesso revocato o calendario non leggibile
        } finally {
            if (cur != null) cur.close();
        }

        Collections.sort(out, (x, y) -> Long.compare(x.begin, y.begin));
        if (out.size() > MAX_ITEMS) return new ArrayList<>(out.subList(0, MAX_ITEMS));
        return out;
    }

    /** Calendario di un'altra persona (condiviso con te), escluse festività e calendari automatici. */
    private static boolean isOthersCalendar(String owner, int access) {
        if (owner == null || owner.isEmpty()) return false;
        if (owner.contains("#") || owner.endsWith("calendar.google.com")) return false;  // festività, compleanni…
        return access < CalendarContract.Calendars.CAL_ACCESS_OWNER;
    }

    /** Nomi degli invitati (te escluso): "Elisabetta, Marco +2". Vuoto se non ci sono altri. */
    private static String attendees(Context c, long eventId, String owner) {
        String[] proj = {
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL
        };
        List<String> names = new ArrayList<>();
        Cursor a = null;
        try {
            a = CalendarContract.Attendees.query(c.getContentResolver(), eventId, proj);
            if (a == null) return "";
            while (a.moveToNext()) {
                String name = a.isNull(0) ? "" : a.getString(0).trim();
                String mail = a.isNull(1) ? "" : a.getString(1).trim();
                if (!mail.isEmpty() && mail.equalsIgnoreCase(owner)) continue;
                if (mail.endsWith("resource.calendar.google.com")) continue;   // sale, risorse
                String shown;
                if (!name.isEmpty()) shown = name.split("\\s+")[0];            // solo il nome
                else if (mail.contains("@")) shown = mail.substring(0, mail.indexOf('@'));
                else continue;
                if (!names.contains(shown)) names.add(shown);
            }
        } catch (Exception ignored) {
        } finally {
            if (a != null) a.close();
        }
        if (names.isEmpty()) return "";
        if (names.size() <= 2) return String.join(", ", names);
        return names.get(0) + ", " + names.get(1) + " +" + (names.size() - 2);
    }

    /** Gli eventi di tutto il giorno sono salvati a mezzanotte UTC: li riporto alla mezzanotte locale. */
    private static long utcMidnightToLocal(long utcMillis) {
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        Calendar local = Calendar.getInstance();
        local.clear();
        local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return local.getTimeInMillis();
    }
}
