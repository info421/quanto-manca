package it.paddeu.quantomanca;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;

/** Testi e colori: quanto manca, quando, urgenza. */
public final class Fmt {

    public static final int RED = 0xFFE5484D;
    public static final int ORANGE = 0xFFF08C2E;
    public static final int YELLOW = 0xFFD9A400;
    public static final int GREEN = 0xFF30A46C;
    public static final int BLUE = 0xFF3E63DD;

    private static final Locale IT = Locale.ITALIAN;

    private Fmt() {}

    private static LocalDate day(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static long dayDiff(Ev e, long now) {
        return ChronoUnit.DAYS.between(day(now), day(e.begin));
    }

    public static boolean live(Ev e, long now) {
        return e.begin <= now && now < e.end;
    }

    private static String hm(long millis) {
        return new SimpleDateFormat("HH:mm", IT).format(new Date(millis));
    }

    /** Testo grande a destra: "tra 3 ore", "Domani", "tra 12 giorni"… */
    public static String big(Ev e, long now) {
        if (live(e, now)) return "In corso";
        long dd = dayDiff(e, now);
        long min = Math.max(0, Math.round((e.begin - now) / 60000.0));
        if (dd == 0) {
            if (e.allDay) return "Oggi";
            if (min < 60) return "tra " + min + " min";
            long h = min / 60, m = min % 60;
            return m == 0 ? "tra " + h + " h" : "tra " + h + " h " + String.format(IT, "%02d", m);
        }
        if (dd == 1) return "Domani";
        if (dd < 14) return "tra " + dd + " giorni";
        if (dd < 60) return "tra " + (dd / 7) + " sett.";
        return "tra " + Math.round(dd / 30.0) + " mesi";
    }

    /** Testo piccolo sotto: l'ora o il giorno della settimana. */
    public static String small(Ev e, long now) {
        if (live(e, now)) return e.allDay ? "tutto il giorno" : "fino alle " + hm(e.end);
        long dd = dayDiff(e, now);
        if (dd <= 1) return e.allDay ? "tutto il giorno" : "alle " + hm(e.begin);
        if (dd < 14) return new SimpleDateFormat("EEEE", IT).format(new Date(e.begin));
        return dd + " giorni";
    }

    /** Riga sotto il titolo: "lun 24 nov · 11:00 · luogo". */
    public static String when(Ev e) {
        String d = new SimpleDateFormat("EEE d MMM", IT).format(new Date(e.begin));
        String s = e.allDay ? d + " · tutto il giorno" : d + " · " + hm(e.begin);
        if (e.sharedWith != null && !e.sharedWith.isEmpty()) {
            s += e.sharedByAttendees ? " · condiviso con " + e.sharedWith : " · condiviso · " + e.sharedWith;
        }
        if (e.location != null && !e.location.isEmpty()) s += " · " + e.location;
        return s;
    }

    public static int color(Ev e, long now) {
        double h = (e.begin - now) / 3_600_000.0;
        if (h < 24) return RED;
        if (h < 72) return ORANGE;
        if (h < 24 * 7) return YELLOW;
        if (h < 24 * 30) return GREEN;
        return BLUE;
    }
}
