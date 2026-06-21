package com.xyron.game.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;

import com.xyron.game.R;
import com.xyron.game.launcher.util.DataVariantPreferences;

import java.io.File;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class UpdateActivity extends SampActivity {
    private static final String DATA_VARIANT_LITE = DataVariantPreferences.DATA_VARIANT_LITE;
    private static final String DATA_VARIANT_FULL = DataVariantPreferences.DATA_VARIANT_FULL;

    public Messenger mMessenger = new Messenger(new IncomingHandler());
    public Messenger mService;
    boolean isBind = false;
    boolean isBindingService = false;
    private UpdateMode mUpdateMode = UpdateMode.Undefined;
    public int mGpuType;
    private String mSelectedDataVariantId = DATA_VARIANT_LITE;

    private File mGameApk;
    private WebView mWebView;

    boolean mIsStartingUpdate = false;
    boolean mPendingStartUpdate = false;
    boolean mStartMessageSent = false;

    public enum UpdateMode {
        Undefined,
        GameDataUpdate
    }

    public enum GameStatus {
        Unknown,
        GameUpdateRequired,
        UpdateRequired,
        Updated
    }

    public enum UpdateStatus {
        Undefined,
        CheckUpdate,
        CheckFiles,
        DownloadGame,
        DownloadGameData,
        SourceUnavailable
    }

    public enum eGPUType {
        DXT,
        PVR,
        ETC
    }

    // ── WebView JS helper ──────────────────────────────────────────────────
    private void callJs(final String fn, final Object... args) {
        runOnUiThread(() -> {
            if (mWebView == null) return;
            StringBuilder sb = new StringBuilder(fn).append("(");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(",");
                Object a = args[i];
                if (a instanceof String) {
                    sb.append("'")
                      .append(((String) a).replace("\\", "\\\\").replace("'", "\\'").replace("\n", " "))
                      .append("'");
                } else if (a instanceof Boolean) {
                    sb.append((Boolean) a ? "true" : "false");
                } else {
                    sb.append(a);
                }
            }
            sb.append(")");
            mWebView.evaluateJavascript(sb.toString(), null);
        });
    }

    // ── JavaScript → Java bridge ───────────────────────────────────────────
    public class UpdateBridgeJs {
        @JavascriptInterface
        public void selectVariant(String variantId) {
            selectDataVariant(variantId);
        }
    }

    // ── No-op: theme is handled in HTML ───────────────────────────────────
    public void changeTheme(boolean theme) { /* styled in update.html */ }

    // ── Message handler ───────────────────────────────────────────────────
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 4) {
                UpdateStatus valueOf = UpdateStatus.valueOf(msg.getData().getString("status", ""));

                if (valueOf == UpdateStatus.DownloadGameData) {
                    callJs("setStatus", "Atualizando a data do jogo...");
                    long total   = msg.getData().getLong("total");
                    long current = msg.getData().getLong("current");
                    String filename = msg.getData().getString("filename");
                    callJs("setIndeterminate", false);
                    callJs("setFileName", filename != null ? filename : "");
                    callJs("setFileCount", current / 1048576 + "MB / " + total / 1048576 + "MB");
                    int pct = total > 0 ? (int)(current * 100 / (total + 1)) : 0;
                    callJs("setProgress", pct);
                    Log.d("UpdateActivity", current / 1048576 + "/" + total / 1048576);

                } else if (valueOf == UpdateStatus.CheckUpdate) {
                    long total   = msg.getData().getLong("total");
                    long current = msg.getData().getLong("current");
                    String filename = msg.getData().getString("filename");
                    callJs("setFileName", filename != null ? filename : "Verificando...");
                    if (total > 0) {
                        callJs("setIndeterminate", false);
                        callJs("setProgress", (int)(current * 100 / (total + 1)));
                    }

                } else if (valueOf == UpdateStatus.DownloadGame) {
                    callJs("setStatus", "Atualizando o jogo...");
                    long total   = msg.getData().getLong("total");
                    long current = msg.getData().getLong("current");
                    long curFile = msg.getData().getLong("currentfile");
                    long totFile = msg.getData().getLong("totalfiles");
                    String filename = msg.getData().getString("filename");
                    callJs("setIndeterminate", false);
                    callJs("setFileName", filename != null ? filename : "");
                    callJs("setFileCount", curFile + " / " + totFile);
                    int pct = total > 0 ? (int)(current * 100 / (total + 1)) : 0;
                    callJs("setProgress", pct);

                } else if (valueOf == UpdateStatus.SourceUnavailable) {
                    callJs("setStatus", "Fonte de download indisponível");
                    callJs("setFileName", "Não foi possível acessar os arquivos do jogo.");
                    callJs("setFileCount", "");
                    callJs("setIndeterminate", false);
                    callJs("setProgress", 0);
                    startActivity(new Intent(UpdateActivity.this, WebLauncherActivity.class));
                    finish();

                } else if (!mIsStartingUpdate) {
                    Message obtain2 = Message.obtain((Handler) null, 1);
                    obtain2.replyTo = UpdateActivity.this.mMessenger;
                    try {
                        UpdateActivity.this.mService.send(obtain2);
                    } catch (RemoteException e6) {
                        e6.printStackTrace();
                    }
                    mIsStartingUpdate = true;
                }

            } else if (msg.what == 2) {
                Log.d("x1y2z", "UpdateService.UPDATE_GAME_DATA");
                if (msg.getData().getBoolean("status", false)) {
                    String string3 = msg.getData().getString("apkPath", "");
                    if (string3.length() > 0) {
                        mGameApk = new File(string3);
                    }
                    if (mGameApk == null || !mGameApk.exists()) {
                        startActivity(new Intent(UpdateActivity.this, WebLauncherActivity.class));
                        finish();
                    } else {
                        requestInstallGame();
                        return;
                    }
                }
                Log.d("x1y2z", "Error update game data");

            } else if (msg.what == 1) {
                Log.i("UpdateActivity", "UpdateService.UPDATE_GAME");
                callJs("setStatus", "Instalando...");
                callJs("setIndeterminate", true);
                String string = msg.getData().getString("apkPath", "");
                if (msg.getData().getBoolean("status", false)) {
                    if (string.length() > 0) {
                        mGameApk = new File(string);
                    }
                    Message obtain = Message.obtain((Handler) null, 2);
                    obtain.replyTo = UpdateActivity.this.mMessenger;
                    try {
                        mService.send(obtain);
                    } catch (RemoteException e5) {
                        e5.printStackTrace();
                    }
                } else {
                    Log.d("UpdateActivity", "Error update game");
                }
            }
        }
    }

    void requestInstallGame() {
        Log.d("x1y2z", "request install game");
        Uri contentUri1 = FileProvider.getUriForFile(
            getApplicationContext(),
            getApplicationContext().getPackageName() + ".provider",
            mGameApk
        );
        Intent intent = new Intent(Intent.ACTION_VIEW, contentUri1);
        intent.setDataAndType(contentUri1, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }


    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setTitle("Cancelar atualização?")
            .setMessage("O download está em andamento. Deseja realmente sair e cancelar a atualização?")
            .setPositiveButton("Sair", (dialog, which) -> {
                if (isBind) {
                    unbindService(serviceConnection);
                    isBind = false;
                }
                finish();
            })
            .setNegativeButton("Continuar baixando", null)
            .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i("UpdateActivity", "onActivityResult -> requestCode: " + requestCode + ", resultCode: " + resultCode);
        if (requestCode == 0) {
            if (mGameApk != null && mGameApk.exists()) {
                mGameApk.delete();
            }
            startActivity(new Intent(this, WebLauncherActivity.class));
            finish();
        }
    }

    private void readUpdateMode() {
        String modeName = getIntent().getStringExtra("mode");
        if (modeName == null || modeName.length() == 0) {
            mUpdateMode = UpdateMode.Undefined;
            return;
        }
        try {
            mUpdateMode = UpdateMode.valueOf(modeName);
        } catch (IllegalArgumentException ignored) {
            mUpdateMode = UpdateMode.Undefined;
        }
    }

    private void setupDataVariantChooser() {
        if (mUpdateMode == UpdateMode.GameDataUpdate) {
            String savedVariantId = DataVariantPreferences.getSelectedVariantId(this);
            if (DATA_VARIANT_FULL.equals(savedVariantId)) {
                DataVariantPreferences.saveSelectedVariantId(this, DATA_VARIANT_LITE);
                savedVariantId = DATA_VARIANT_LITE;
            }
            if (savedVariantId.isEmpty()) {
                savedVariantId = DATA_VARIANT_LITE;
                DataVariantPreferences.saveSelectedVariantId(this, DATA_VARIANT_LITE);
            }
            startDataVariantUpdate(savedVariantId, false);
        } else {
            callJs("showProgressPanel", "Preparando atualização...");
        }
    }

    private void selectDataVariant(String variantId) {
        startDataVariantUpdate(variantId, true);
    }

    private void startDataVariantUpdate(String variantId, boolean saveSelection) {
        String normalizedVariantId = DataVariantPreferences.normalizeVariantId(variantId);
        if (!DataVariantPreferences.isSupportedVariantId(normalizedVariantId)) {
            normalizedVariantId = DATA_VARIANT_LITE;
        }
        mSelectedDataVariantId = normalizedVariantId;
        if (saveSelection) {
            DataVariantPreferences.saveSelectedVariantId(this, normalizedVariantId);
        }

        mPendingStartUpdate = true;
        mStartMessageSent = false;
        mIsStartingUpdate = false;

        final String label = DATA_VARIANT_FULL.equals(mSelectedDataVariantId) ? "Full" : "Lite";
        callJs("showProgressPanel", "Preparando a data " + label + "...");
        callJs("setFileName", "Conectando ao servidor");
        callJs("setFileCount", "");
        callJs("setIndeterminate", true);

        requestGameDataUpdateStart();
    }

    private void requestGameDataUpdateStart() {
        if (!mPendingStartUpdate || mStartMessageSent || mGpuType == 0) {
            return;
        }
        if (mService == null) {
            if (!isBindingService) {
                isBindingService = true;
                bindService(new Intent(UpdateActivity.this, UpdateService.class), serviceConnection, Context.BIND_AUTO_CREATE);
            }
            return;
        }
        sendGameDataUpdateStart();
    }

    private void sendGameDataUpdateStart() {
        if (!mPendingStartUpdate || mStartMessageSent || mService == null || mGpuType == 0) {
            return;
        }
        Message obtain = Message.obtain((Handler) null, 7);
        obtain.getData().putInt("gputype", mGpuType);
        obtain.getData().putString("data_variant", mSelectedDataVariantId);
        obtain.replyTo = mMessenger;
        try {
            mService.send(obtain);
            mStartMessageSent = true;
            mPendingStartUpdate = false;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_update);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Setup WebView
        mWebView = findViewById(R.id.update_webview);
        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                readUpdateMode();
                setupDataVariantChooser();
            }
        });
        mWebView.addJavascriptInterface(new UpdateBridgeJs(), "UpdateBridge");
        mWebView.loadUrl("file:///android_asset/launcher/update.html");

        // GPU detection (invisible, needed to detect texture format)
        GLSurfaceView.Renderer mGlRenderer = new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                eGPUType egputype;
                String glGetString  = gl10.glGetString(GL10.GL_EXTENSIONS);
                String glGetString2 = gl10.glGetString(GL10.GL_EXTENSIONS);
                if (glGetString2.contains("GL_IMG_texture_compression_pvrtc")) {
                    egputype = eGPUType.PVR;
                    mGpuType = 3;
                } else if (glGetString2.contains("GL_EXT_texture_compression_dxt1")
                        || glGetString2.contains("GL_EXT_texture_compression_s3tc")
                        || glGetString2.contains("GL_AMD_compressed_ATC_texture")) {
                    egputype = eGPUType.DXT;
                    mGpuType = 1;
                } else {
                    egputype = eGPUType.ETC;
                    mGpuType = 2;
                }
                Log.e("x1y2z", "GPU name: " + glGetString);
                Log.e("x1y2z", "GPU type: " + egputype.name());
                if (mUpdateMode == UpdateMode.GameDataUpdate) {
                    requestGameDataUpdateStart();
                }
            }
            @Override
            public void onSurfaceChanged(GL10 gl10, int i, int i1) {}
            @Override
            public void onDrawFrame(GL10 gl10) {}
        };

        ConstraintLayout gpuLayout = findViewById(R.id.gpu);
        GLSurfaceView mGlSurfaceView = new GLSurfaceView(this);
        mGlSurfaceView.setRenderer(mGlRenderer);
        gpuLayout.addView(mGlSurfaceView);
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = new Messenger(iBinder);
            isBindingService = false;
            isBind = true;
            sendGameDataUpdateStart();
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
            isBind = false;
            isBindingService = false;
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isBind) {
            unbindService(serviceConnection);
            isBind = false;
        }
    }
}
