package com.codexlight.android;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout topBar;
    private TextView tvServerTitle;
    private View statusDot;
    private LinearLayout errorOverlay;
    private TextView tvErrorMessage;
    private Button btnErrorRetry;
    private Button btnErrorChangeServer;

    private ServerConfig currentConfig;
    private boolean isDemoMode = false;
    private volatile boolean currentPageTrusted = false;
    private long lastBackPressTime = 0;
    private ValueCallback<Uri[]> fileUploadCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        resolveIntent();
        setupWebView();
        loadTargetUrl();
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        topBar = findViewById(R.id.topBar);
        tvServerTitle = findViewById(R.id.tvServerTitle);
        statusDot = findViewById(R.id.statusDot);
        errorOverlay = findViewById(R.id.errorOverlay);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
        btnErrorRetry = findViewById(R.id.btnErrorRetry);
        btnErrorChangeServer = findViewById(R.id.btnErrorChangeServer);

        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            errorOverlay.setVisibility(View.GONE);
            loadTargetUrl();
        });

        findViewById(R.id.btnSettings).setOnClickListener(v -> openServerManager());

        btnErrorRetry.setOnClickListener(v -> {
            errorOverlay.setVisibility(View.GONE);
            loadTargetUrl();
        });

        btnErrorChangeServer.setOnClickListener(v -> openServerManager());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resolveIntent();
        loadTargetUrl();
    }

    private void resolveIntent() {
        Intent intent = getIntent();
        isDemoMode = intent.getBooleanExtra("demo", false);

        if (!isDemoMode) {
            String host = intent.getStringExtra("host");
            int port = intent.getIntExtra("port", 0);
            String token = intent.getStringExtra("token");

            if (host != null && !host.isEmpty()) {
                currentConfig = new ServerConfig(host, port, token);
                new ServerConfigManager(this).saveCurrent(currentConfig);
            } else {
                ServerConfigManager mgr = new ServerConfigManager(this);
                currentConfig = mgr.getCurrent();
            }

            if (currentConfig == null) {
                openServerManager();
                finish();
            }
        }
    }

    public boolean isDemoMode() {
        return isDemoMode;
    }

    public boolean isTrustedOrigin(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (isDemoMode) {
            String path = uri.getPath();
            return "file".equalsIgnoreCase(scheme) && path != null && path.startsWith("/android_asset/");
        }
        if (currentConfig == null || !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        int port = uri.getPort();
        int effectivePort = port > 0 ? port : 80;
        return host != null
            && host.equalsIgnoreCase(currentConfig.getHost())
            && effectivePort == currentConfig.getPort();
    }

    public void updatePageOrigin(String url) {
        if (url == null || url.isEmpty()) {
            currentPageTrusted = false;
            return;
        }
        try {
            currentPageTrusted = isTrustedOrigin(Uri.parse(url));
        } catch (Exception e) {
            currentPageTrusted = false;
        }
    }

    public String getCurrentToken() {
        if (isDemoMode || !currentPageTrusted || currentConfig == null) {
            return "";
        }
        return currentConfig.getToken();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(isDemoMode);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        String defaultUa = settings.getUserAgentString();
        settings.setUserAgentString(defaultUa + " CodexLightMobile/1.0.0");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(false);
        }

        webView.addJavascriptInterface(new CodexLightBridge(this), "CodexLightNative");
        webView.setWebChromeClient(new AppWebChromeClient(this, progressBar));
        webView.setWebViewClient(new AppWebViewClient(this, progressBar));
    }

    private void loadTargetUrl() {
        if (isDemoMode) {
            currentPageTrusted = false;
            tvServerTitle.setText("Codex Light (本地演示模式)");
            statusDot.setBackgroundTintList(getColorStateList(R.color.accent_primary));
            webView.loadUrl("file:///android_asset/web/index.html");
        } else if (currentConfig != null) {
            currentPageTrusted = true;
            tvServerTitle.setText(currentConfig.toDisplayAddress());
            statusDot.setBackgroundTintList(getColorStateList(R.color.status_connected));
            String url = currentConfig.toFullUrl();
            webView.loadUrl(url);
        }
    }

    public void hideConnectionError() {
        if (errorOverlay != null) {
            errorOverlay.setVisibility(View.GONE);
        }
    }

    public void showConnectionError(String detail) {
        if (errorOverlay == null) return;
        errorOverlay.setVisibility(View.VISIBLE);
        if (currentConfig != null) {
            tvErrorMessage.setText("无法连接到 " + currentConfig.toDisplayAddress() + "\n\n请确认：\n1. 电脑端 Codex Light 已启动并在「设置 → 局域网访问」中保持开启\n2. 手机与电脑在同一 Wi-Fi 网络下\n3. 电脑防火墙未拦截端口 " + currentConfig.getPort());
        } else {
            tvErrorMessage.setText(detail);
        }
    }

    public void setFileUploadCallback(ValueCallback<Uri[]> callback) {
        if (this.fileUploadCallback != null) {
            this.fileUploadCallback.onReceiveValue(null);
        }
        this.fileUploadCallback = callback;
    }

    private void openServerManager() {
        Intent intent = new Intent(this, ServerConnectActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (fileUploadCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileUploadCallback.onReceiveValue(results);
            fileUploadCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (errorOverlay.getVisibility() == View.VISIBLE) {
            openServerManager();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressTime < 2000) {
            super.onBackPressed();
        } else {
            lastBackPressTime = now;
            Toast.makeText(this, R.string.press_again_exit, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
