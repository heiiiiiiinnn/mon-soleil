package fr.monsoleil;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;

/** Récupère la prévision UV du jour à Saint-Cloud et envoie la notif si on peut bronzer. */
public final class UvChecker {

    static String forecastUrl(Context c) {
        SharedPreferences p = prefs(c);
        return String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&hourly=uv_index&timezone=auto&forecast_days=1",
                p.getFloat("lat", 48.8448f), p.getFloat("lon", 2.2197f));
    }
    static String placeName(Context c) { return prefs(c).getString("name", "Saint-Cloud"); }
    static final String CHANNEL = "bronzage";
    static final int NOTIF_HOUR = 8, NOTIF_MINUTE = 30;

    private UvChecker() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("mon_soleil", Context.MODE_PRIVATE);
    }

    static int phototype(Context c) { return prefs(c).getInt("type", 2); }
    static double threshold(Context c) { return prefs(c).getFloat("seuil", 3f); }

    /** Programme la vérification tous les matins vers 8h30. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1,
                new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_IMMUTABLE);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, NOTIF_HOUR);
        cal.set(Calendar.MINUTE, NOTIF_MINUTE);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pi);
    }

    static int sessionMinutes(double uv, int type) {
        double med = type == 3 ? 350 : 250;
        double toBurn = med / (uv * 0.025 * 60);
        long m = Math.round(toBurn * 0.4 / 5) * 5;
        return (int) Math.max(5, Math.min(30, m));
    }

    /**
     * Vérifie la prévision. Si force est vrai, envoie toujours une notif (pour tester).
     * Retourne le texte de la notif, ou null si rien n'est envoyé. À appeler hors du thread principal.
     */
    static String check(Context c, boolean force) {
        double seuil = threshold(c);
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(forecastUrl(c)).openConnection();
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject hourly = new JSONObject(sb.toString()).getJSONObject("hourly");
            JSONArray times = hourly.getJSONArray("time");
            JSONArray uvs = hourly.getJSONArray("uv_index");

            double max = 0; int first = -1, last = -1;
            for (int i = 0; i < uvs.length(); i++) {
                if (uvs.isNull(i)) continue;
                double uv = uvs.getDouble(i);
                int hour = Integer.parseInt(times.getString(i).substring(11, 13));
                if (uv > max) max = uv;
                if (uv >= seuil) { if (first < 0) first = hour; last = hour; }
            }

            String msg;
            if (first >= 0) {
                int minutes = sessionMinutes(max, phototype(c));
                if (seuil < 3 && max < 3) minutes = Math.max(minutes, 30);
                msg = String.format(Locale.FRANCE,
                        "%s : UV jusqu'à %.0f entre %dh et %dh. Crème SPF30, %d min max.",
                        placeName(c), max, first, last + 1, minutes);
                if (max >= 8) msg += " Soleil très fort, pas plus longtemps !";
                notify(c, "☀️ Tu peux aller bronzer !", msg);
                return msg;
            } else if (force) {
                msg = String.format(Locale.FRANCE,
                        "Pas aujourd'hui à %s : l'UV ne dépasse pas %.1f (il faut %.0f). Je te préviens dès que c'est bon.",
                        placeName(c), max, seuil);
                notify(c, "Test Mon Soleil", msg);
                return msg;
            }
            return null;
        } catch (Exception e) {
            if (force) notify(c, "Test Mon Soleil", "Pas de connexion à la météo pour le moment.");
            return null;
        }
    }

    static PendingIntent timerIntent(Context c) {
        return PendingIntent.getBroadcast(c, 2, new Intent(c, TimerReceiver.class), PendingIntent.FLAG_IMMUTABLE);
    }

    static void startTimer(Context c, int seconds) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + seconds * 1000L, timerIntent(c));
    }

    static void cancelTimer(Context c) {
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(timerIntent(c));
    }

    static void notify(Context c, String title, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Alerte bronzage",
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFFFF9F1C)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try { nm.notify(42, n); } catch (SecurityException ignored) {}
    }
}
