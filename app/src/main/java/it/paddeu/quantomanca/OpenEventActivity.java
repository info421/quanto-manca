package it.paddeu.quantomanca;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.widget.Toast;

/** Ponte invisibile: riceve il tocco su un appuntamento del widget e lo apre nell'app Calendario. */
public class OpenEventActivity extends Activity {

    static final String EXTRA_STAR_KEY = "it.paddeu.quantomanca.STAR_KEY";
    static final String EXTRA_STAR_WAS = "it.paddeu.quantomanca.STAR_WAS";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        if (in != null && in.hasExtra(EXTRA_STAR_KEY)) {
            // tocco sulla stellina: mette/toglie dagli importanti e ridisegna il widget
            Stars.toggle(this, in.getStringExtra(EXTRA_STAR_KEY), in.getBooleanExtra(EXTRA_STAR_WAS, false));
            CountdownWidget.updateAll(this);
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        if (in != null && in.getData() != null) {
            Intent view = new Intent(Intent.ACTION_VIEW, in.getData());
            view.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, in.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0));
            view.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, in.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0));
            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(view);
            } catch (Exception e) {
                Toast.makeText(this, "Non trovo l'app Calendario", Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
}
