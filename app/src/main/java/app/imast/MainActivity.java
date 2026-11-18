package app.imast;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    // Онлайн-версия: обновляется сама после каждого изменения в репозитории
    private static final String ONLINE_URL = "https://armardi95.github.io/Imastapk/";
    private static final String ONLINE_HOST = "armardi95.github.io";
    // Копия внутри APK: открывается, если нет интернета и нет сохранённой версии
    private static final String LOCAL_URL = "file:///android_asset/index.html";

    private static final int REQ_PERMS = 10;
    private static final int REQ_FILE = 11;
    private static final int REQ_SAVE = 12;

    private WebView web;
    private boolean fellBack = false;
    private String pendingPermission;
    private ValueCallback<Uri[]> fileCallback;
    private String launchSess = "";
    private String pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // localStorage: дневник и привычки хранятся на телефоне
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new NativeBridge(this), "ImastNative");
        web.setBackgroundColor(0xFF0E1526);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme()) || ONLINE_HOST.equals(u.getHost())) {
                    return false;           // свои страницы открываем внутри приложения
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u)); // внешние ссылки в браузере
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // Онлайн-страница не загрузилась (нет сети и нет сохранённой копии) — открываем встроенную
                if (request.isForMainFrame() && !fellBack) {
                    fellBack = true;
                    view.loadUrl(LOCAL_URL);
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame() && !fellBack) {
                    fellBack = true;
                    view.loadUrl(LOCAL_URL);
                }
            }
        });

        // Выбор файла (например, восстановление резервной копии)
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        readLaunch(getIntent());
        Reminders.ensureChannel(this);
        Reminders.rescheduleAll(this);

        if (savedInstanceState == null) {
            web.loadUrl(ONLINE_URL);
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    // ---------- запуск из виджета ----------

    private void readLaunch(Intent i) {
        String s = i == null ? null : i.getStringExtra("sess");
        if (s != null) {
            launchSess = s;
            i.removeExtra("sess");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        readLaunch(intent);
        if (!launchSess.isEmpty()) {
            String s = launchSess;
            launchSess = "";
            callback("launch", quote(s));
        }
    }

    String takeLaunch() {
        String s = launchSess;
        launchSess = "";
        return s;
    }

    // ---------- для NativeBridge ----------

    void callback(String type, String jsValue) {
        final String js = "window.ImastNativeCallback&&window.ImastNativeCallback(" + quote(type) + "," + jsValue + ")";
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    void askPermissions(String name, String[] perms) {
        pendingPermission = name;
        requestPermissions(perms, REQ_PERMS);
    }

    boolean biometricAvailable() {
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager bm = getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        }
        return Build.VERSION.SDK_INT >= 28
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
    }

    void authenticate(String title) {
        if (Build.VERSION.SDK_INT < 28 || !biometricAvailable()) {
            callback("auth", "'unavailable'");
            return;
        }
        BiometricPrompt p = new BiometricPrompt.Builder(this)
                .setTitle(title == null || title.isEmpty() ? "Вход в Imast" : title)
                .setNegativeButton("Отмена", getMainExecutor(), (d, w) -> callback("auth", "false"))
                .build();
        p.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { callback("auth", "true"); }
            @Override public void onAuthenticationError(int code, CharSequence msg) { callback("auth", "false"); }
        });
    }

    // ---------- сохранение файла (резервная копия) ----------

    /** Открывает системное окно «Сохранить как». Итог придёт в ImastNativeCallback('saved', true|false). */
    void saveFile(String name, String text) {
        pendingSave = text;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, name == null || name.isEmpty() ? "imast.json" : name);
        try {
            startActivityForResult(i, REQ_SAVE);
        } catch (Exception e) {
            pendingSave = null;
            callback("saved", "false");
        }
    }

    private void writeSaved(Uri uri) {
        String text = pendingSave;
        pendingSave = null;
        if (uri == null || text == null) {
            callback("saved", "false");
            return;
        }
        try (java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) throw new java.io.IOException("no stream");
            os.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            os.flush();
            callback("saved", "true");
        } catch (Exception e) {
            callback("saved", "false");
        }
    }

    // ---------- системные события ----------

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PERMS || pendingPermission == null) return;
        boolean ok = results.length > 0;
        for (int r : results) ok &= r == PackageManager.PERMISSION_GRANTED;
        callback("permission:" + pendingPermission, ok ? "true" : "false");
        pendingPermission = null;
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
        if (code == REQ_SAVE) {
            writeSaved(result == RESULT_OK && data != null ? data.getData() : null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // страница запоминает время ухода, чтобы при возврате спросить код входа
        if (web != null) callback("pause", "true");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // после возврата из системных настроек страница может перепроверить разрешения
        if (web != null) callback("resume", "true");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
