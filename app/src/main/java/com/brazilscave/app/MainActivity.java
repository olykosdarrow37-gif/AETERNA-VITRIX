package com.brazilscave.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";
    private static final int REQ_FILE = 1001;
    private static final String BRAVE = "com.brave.browser";

    // Tiny built-in ad/tracker blocklist (blocks these hosts and their subdomains).
    private static final String[] BLOCKED = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adnxs.com", "popads.net", "popcash.net",
            "propellerads.com", "exoclick.com", "juicyads.com", "trafficjunky.com",
            "adsterra.com", "hilltopads.net", "onclickads.net", "taboola.com", "outbrain.com"
    };

    // Saves blob: downloads (e.g. library backup export) that WebView can't handle by itself.
    private static final String BRIDGE_JS =
            "(function(){if(window.__bcDl)return;window.__bcDl=true;"
          + "function send(a){fetch(a.href).then(function(r){return r.blob()}).then(function(b){"
          + "var fr=new FileReader();fr.onload=function(){var d=fr.result,i=d.indexOf(',');"
          + "BCBridge.postMessage((a.download||'download')+'\\n'+(b.type||'application/octet-stream')+'\\n'+d.substring(i+1));};"
          + "fr.readAsDataURL(b);});}"
          + "var oc=HTMLAnchorElement.prototype.click;"
          + "HTMLAnchorElement.prototype.click=function(){"
          + "if(this.download&&this.href&&this.href.indexOf('blob:')===0){send(this);return;}"
          + "return oc.apply(this,arguments);};"
          + "document.addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a[download]');"
          + "if(a&&a.href&&a.href.indexOf('blob:')===0){e.preventDefault();e.stopPropagation();send(a);}},true);"
          + "})();";

    private WebView webView;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint({"SetJavaScriptEnabled", "RequiresFeature"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0B0907);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url != null && url.startsWith("http")) openExternal(Uri.parse(url));
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (isBlocked(u.getHost())) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return assetLoader.shouldInterceptRequest(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                // Only the top-level page leaves the app; iframes (streaming rooms) load in place.
                if (r.isForMainFrame()) {
                    openExternal(u);
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // window.open(...) -> open in Brave / default browser.
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        openExternal(r.getUrl());
                        v.destroy();
                        return true;
                    }
                });
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(tmp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = cb;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
                hideSystemBars(true);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                webView.setVisibility(View.VISIBLE);
                hideSystemBars(false);
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
        });

        // Origin-restricted bridge so only the app's own page (not streaming iframes) can save files.
        Set<String> origins = Collections.singleton("https://" + HOST);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addWebMessageListener(webView, "BCBridge", origins,
                    (view, message, sourceOrigin, isMainFrame, reply) -> saveDownload(message));
            WebViewCompat.addDocumentStartJavaScript(webView, BRIDGE_JS, origins);
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            webView.loadUrl(START_URL);
        }
    }

    private boolean isBlocked(String host) {
        if (host == null) return false;
        for (String b : BLOCKED) {
            if (host.equals(b) || host.endsWith("." + b)) return true;
        }
        return false;
    }

    private void openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) return;
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            i.setPackage(BRAVE);              // prefer Brave (built-in ad blocking)
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            i.setPackage(null);               // fall back to the default browser
            try { startActivity(i); } catch (ActivityNotFoundException ignored) { }
        }
    }

    private void saveDownload(@NonNull WebMessageCompat message) {
        final String data = message.getData();
        if (data == null) return;
        new Thread(() -> {
            try {
                int a = data.indexOf('\n');
                int b = data.indexOf('\n', a + 1);
                String name = data.substring(0, a).replaceAll("[^A-Za-z0-9._ -]", "_");
                if (name.isEmpty()) name = "download";
                String mime = data.substring(a + 1, b);
                byte[] bytes = Base64.decode(data.substring(b + 1), Base64.DEFAULT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri out = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (out == null) throw new Exception("insert failed");
                    try (OutputStream os = getContentResolver().openOutputStream(out)) {
                        os.write(bytes);
                    }
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) throw new Exception("no storage");
                    try (FileOutputStream os = new FileOutputStream(new File(dir, name))) {
                        os.write(bytes);
                    }
                }
                final String fn = name;
                runOnUiThread(() -> Toast.makeText(this, "Saved to Downloads: " + fn, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Couldn't save file", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars(boolean hide) {
        getWindow().getDecorView().setSystemUiVisibility(hide
                ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) {
            webView.getWebChromeClient().onHideCustomView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            moveTaskToBack(true);   // keep audio playing in the background
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        webView.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { root.removeAllViews(); webView.destroy(); }
        super.onDestroy();
    }
}
