package app.imast;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

    private WebView web;
    private boolean fellBack = false;

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

        web.setBackgroundColor(0xFF0E1526);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme()) || ONLINE_HOST.equals(u.getHost())) {
                    return false;           // свои страницы открываем внутри приложения
                }
                startActivity(new Intent(Intent.ACTION_VIEW, u)); // внешние ссылки в браузере
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
                // Страница ещё не опубликована (ошибка 404 и т. п.) — тоже открываем встроенную
                if (request.isForMainFrame() && !fellBack) {
                    fellBack = true;
                    view.loadUrl(LOCAL_URL);
                }
            }
        });

        if (savedInstanceState == null) {
            web.loadUrl(ONLINE_URL);
        } else {
            web.restoreState(savedInstanceState);
        }
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
}
