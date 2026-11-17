package fr.monsoleil;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        UvChecker.schedule(this);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#FFF8EF"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);

        try {
            web.loadDataWithBaseURL("https://mon-soleil.app/", readAsset("index.html"),
                    "text/html", "utf-8", null);
        } catch (Exception e) {
            web.loadData("Erreur de chargement", "text/plain", "utf-8");
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
        @JavascriptInterface public void testNotif() {
            final android.content.Context app = getApplicationContext();
            new Thread(() -> UvChecker.check(app, true)).start();
        }
    }
}
