package it.paddeu.quantomanca;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Riceve l'esito dell'installazione dell'aggiornamento e mostra la conferma di Android. */
public class InstallReceiver extends BroadcastReceiver {

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(confirm); } catch (Exception ignored) {}
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(c, "Quanto manca aggiornata", Toast.LENGTH_SHORT).show();
        } else if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, "Aggiornamento non riuscito" + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
        }
    }
}
