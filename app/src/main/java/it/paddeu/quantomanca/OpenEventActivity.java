package it.paddeu.quantomanca;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.widget.Toast;

/** Ponte invisibile: riceve il tocco su un appuntamento del widget e lo apre nell'app Calendario. */
public class OpenEventActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
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
