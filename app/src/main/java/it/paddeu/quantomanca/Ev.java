package it.paddeu.quantomanca;

/** Un appuntamento letto dal calendario del telefono. */
public class Ev {
    public long eventId;
    public String title;
    public String location;
    /** Inizio e fine in ora locale (per gli eventi di tutto il giorno: mezzanotte locale). */
    public long begin;
    public long end;
    /** Valori originali del calendario, per aprire l'evento giusto. */
    public long rawBegin;
    public long rawEnd;
    public boolean allDay;
    /** Segnato come importante: titolo che inizia con "!" o con ⭐, oppure colore rosso Pomodoro. */
    public boolean important;
    /** Il calendario permette di modificarlo/eliminarlo. */
    public boolean canEdit;
    /** "Elisabetta, Marco" se ci sono invitati, oppure il nome del calendario condiviso; vuoto se è solo tuo. */
    public String sharedWith = "";
    /** true = invitati (condiviso con…), false = calendario di un altro (condiviso · nome). */
    public boolean sharedByAttendees;
}
