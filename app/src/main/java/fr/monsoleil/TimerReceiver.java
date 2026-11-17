package fr.monsoleil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        UvChecker.notify(context.getApplicationContext(), "⏱️ Séance terminée",
                "Mets-toi à l'ombre. Ce soir, vérifie que ta peau n'est pas rouge.");
    }
}
