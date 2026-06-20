package com.xyron.game.launcher;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xyron.game.launcher.util.GameDataVerifier;
import com.xyron.game.launcher.util.SampQueryApi;
import com.xyron.game.launcher.util.ServerConfigManager;
import com.xyron.game.main.SAMP;

public class WebLauncherActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        applyImmersive();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new LauncherBridge(this), "Android");

        webView.loadUrl("file:///android_asset/launcher/index.html");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    private void applyImmersive() {
        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        }
    }

    public void startGameFromBridge() {
        if (!GameDataVerifier.hasRequiredGameData(getApplicationContext())) {
            Intent intent = new Intent(this, UpdateActivity.class);
            intent.putExtra("mode", UpdateActivity.UpdateMode.GameDataUpdate.name());
            startActivity(intent);
            return;
        }

        ServerConfigManager.ensureSelectedServer(this);
        ServerConfigManager.ServerOption selected = ServerConfigManager.getSelectedServer(this);

        if (selected == null || !selected.isValid()) {
            runOnUiThread(() -> Toast.makeText(this,
                    "Adicione e selecione um servidor antes de jogar.",
                    Toast.LENGTH_LONG).show());
            webView.post(() -> webView.loadUrl("javascript:showPage('servers')"));
            return;
        }

        new Thread(() -> {
            boolean online = isServerReachable(selected);
            runOnUiThread(() -> {
                if (online) {
                    launchGame();
                } else {
                    Toast.makeText(this,
                            "Servidor não respondeu. Verifique sua conexão ou o endereço.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "xyron-ping").start();
    }

    private void launchGame() {
        Intent intent = new Intent(this, SAMP.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private boolean isServerReachable(ServerConfigManager.ServerOption option) {
        if (option == null || !option.isValid()) return false;
        SampQueryApi api = new SampQueryApi(option.host, option.port, 1000);
        try {
            return api.isOnline();
        } finally {
            api.close();
        }
    }

    public void reinstallDataFromBridge() {
        Intent intent = new Intent(this, UpdateActivity.class);
        intent.putExtra("mode", UpdateActivity.UpdateMode.GameDataUpdate.name());
        startActivity(intent);
    }
}
