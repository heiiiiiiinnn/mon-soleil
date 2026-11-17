package fr.monsoleil;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int REQ_NOTIF = 1, REQ_LOC = 2;
    private WebView web;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        UvChecker.schedule(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#06070C"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocation()) {
                    callback.invoke(origin, true, false);
                } else {
                    geoCallback = callback;
                    geoOrigin = origin;
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
                }
            }
        });
        setContentView(web);

        try {
            web.loadDataWithBaseURL("https://mon-soleil.app/", readAsset("index.html"),
                    "text/html", "utf-8", null);
        } catch (Exception e) {
            web.loadData("Erreur de chargement", "text/plain", "utf-8");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.refreshNotif && refreshNotif()", null);
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_LOC && geoCallback != null) {
            geoCallback.invoke(geoOrigin, hasLocation(), false);
            geoCallback = null;
        }
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    /** Ce que la page peut demander à l'appli. */
    class Bridge {
        @JavascriptInterface public int getType() { return UvChecker.phototype(MainActivity.this); }
        @JavascriptInterface public void setType(int t) {
            UvChecker.prefs(MainActivity.this).edit().putInt("type", t == 3 ? 3 : 2).apply();
        }
        @JavascriptInterface public float getSeuil() { return (float) UvChecker.threshold(MainActivity.this); }
        @JavascriptInterface public void setSeuil(float v) {
            UvChecker.prefs(MainActivity.this).edit().putFloat("seuil", v <= 2f ? 2f : 3f).apply();
        }
        @JavascriptInterface public String getLoc() {
            android.content.SharedPreferences p = UvChecker.prefs(MainActivity.this);
            try {
                return new JSONObject()
                        .put("name", p.getString("name", "Saint-Cloud"))
                        .put("lat", p.getFloat("lat", 48.8448f))
                        .put("lon", p.getFloat("lon", 2.2197f))
                        .put("tz", p.getString("tz", "Europe/Paris")).toString();
            } catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public void setLoc(String json) {
            try {
                JSONObject o = new JSONObject(json);
                UvChecker.prefs(MainActivity.this).edit()
                        .putString("name", o.optString("name", "Ma position"))
                        .putFloat("lat", (float) o.getDouble("lat"))
                        .putFloat("lon", (float) o.getDouble("lon"))
                        .putString("tz", o.optString("tz", "Europe/Paris"))
                        .apply();
            } catch (Exception ignored) {}
        }
        @JavascriptInterface public void startTimer(int seconds) { UvChecker.startTimer(getApplicationContext(), seconds); }
        @JavascriptInterface public void cancelTimer() { UvChecker.cancelTimer(getApplicationContext()); }
        @JavascriptInterface public boolean notifOn() {
            return ((android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled();
        }
        @JavascriptInterface public void openNotifSettings() {
            android.content.Intent i = new android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        }
        @JavascriptInterface public void testNotif() {
            final android.content.Context app = getApplicationContext();
            new Thread(() -> UvChecker.check(app, true)).start();
        }
    }
}
