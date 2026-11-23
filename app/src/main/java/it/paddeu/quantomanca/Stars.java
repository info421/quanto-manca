package it.paddeu.quantomanca;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Stelline salvate sul telefono (non modificano il calendario).
 * "on" = messo tra gli importanti con la stellina; "off" = tolto anche se nel calendario ha «!» o il colore rosso.
 */
public final class Stars {

    private static final String PREFS = "stars";

    private Stars() {}

    /** Chiave della singola occorrenza (così una lezione settimanale si può stellare un giorno solo). */
    public static String key(Ev e) {
        return e.eventId + "_" + e.rawBegin;
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Set<String> get(Context c, String name) {
        return new HashSet<>(p(c).getStringSet(name, new HashSet<>()));
    }

    /** Applica le stelline a un evento già letto (che può avere important=true dai segni del calendario). */
    public static void apply(Context c, Ev e, Set<String> on, Set<String> off) {
        String k = key(e);
        if (on.contains(k)) e.important = true;
        else if (off.contains(k)) e.important = false;
    }

    public static Set<String> on(Context c) { return get(c, "on"); }
    public static Set<String> off(Context c) { return get(c, "off"); }

    /** Inverte lo stato: se è importante lo toglie, altrimenti lo aggiunge. */
    public static void toggle(Context c, String key, boolean currentlyImportant) {
        Set<String> on = get(c, "on");
        Set<String> off = get(c, "off");
        if (currentlyImportant) {
            on.remove(key);
            off.add(key);
        } else {
            off.remove(key);
            on.add(key);
        }
        p(c).edit().putStringSet("on", on).putStringSet("off", off).apply();
    }
}
