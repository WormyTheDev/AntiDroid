package com.antidroid.antigravity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String HOME_URL = "https://antigravity.google.com/";

    private static final Set<String> IN_APP_HOSTS = new HashSet<>(Arrays.asList(
            "antigravity.google.com",
            "accounts.google.com",
            "accounts.youtube.com",
            "myaccount.google.com",
            "gds.google.com",
            "consent.google.com",
            "ogs.google.com",
            "www.gstatic.com",
            "ssl.gstatic.com"
    ));

    private WebView webView;
    private FrameLayout popupContainer;
    private WebView popupWebView;
    private ProgressBar progressBar;
    private LinearLayout splashOverlay;
    private LinearLayout errorOverlay;
    private TextView errorMessage;

    private boolean firstPageLoaded = false;
    private boolean hasMainFrameError = false;
    private ValueCallback<Uri[]> filePathCallback;
    private PermissionRequest pendingPermissionRequest;

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (filePathCallback == null) {
                    return;
                }
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String dataString = data.getDataString();
                    ClipData clipData = data.getClipData();
                    if (clipData != null && clipData.getItemCount() > 0) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            });

    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (pendingPermissionRequest != null) {
                    if (granted) {
                        pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                    } else {
                        pendingPermissionRequest.deny();
                    }
                    pendingPermissionRequest = null;
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        configureSystemBars();

        FrameLayout rootContainer = findViewById(R.id.root_container);
        webView = findViewById(R.id.webview);
        popupContainer = findViewById(R.id.popup_container);
        progressBar = findViewById(R.id.progress_bar);
        splashOverlay = findViewById(R.id.splash_overlay);
        errorOverlay = findViewById(R.id.error_overlay);
        errorMessage = findViewById(R.id.error_message);
        Button retryButton = findViewById(R.id.retry_button);

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer, (view, windowInsets) -> {
            Insets statusInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(statusInsets.left, statusInsets.top, statusInsets.right, imeInsets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        retryButton.setOnClickListener(v -> {
            hasMainFrameError = false;
            errorOverlay.setVisibility(View.GONE);
            webView.reload();
        });

        configureWebView(webView);
        configureBackNavigation();

        String startUrl = extractStartUrl(getIntent());
        webView.loadUrl(startUrl);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = extractStartUrl(intent);
        if (url != null && !url.equals(HOME_URL)) {
            webView.loadUrl(url);
        }
    }

    private String extractStartUrl(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null && "https".equalsIgnoreCase(data.getScheme())
                    && "antigravity.google.com".equalsIgnoreCase(data.getHost())) {
                return data.toString();
            }
        }
        return HOME_URL;
    }

    private void configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        applyImmersiveNavBar();
    }

    private void applyImmersiveNavBar() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.navigationBars());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveNavBar();
        }
    }

    private String buildCleanChromeUserAgent(String defaultUa) {
        if (defaultUa == null || defaultUa.isEmpty()) {
            return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
        }
        // Strip WebView markers so Google Sign-In treats this as standard Mobile Chrome
        return defaultUa
                .replace("; wv)", ")")
                .replaceAll("Version/\\d+\\.\\d+\\s+", "");
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void applyCommonWebSettings(WebView target) {
        target.setBackgroundColor(Color.parseColor("#070707"));
        WebSettings settings = target.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(buildCleanChromeUserAgent(settings.getUserAgentString()));

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(target, true);
    }

    private void configureWebView(WebView target) {
        applyCommonWebSettings(target);

        target.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) {
                    return false;
                }
                if (shouldHandleInApp(uri)) {
                    return false;
                }
                openExternalUri(uri);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                hasMainFrameError = false;
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
                if (!firstPageLoaded) {
                    firstPageLoaded = true;
                    splashOverlay.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(() -> splashOverlay.setVisibility(View.GONE))
                            .start();
                }
                if (!hasMainFrameError) {
                    errorOverlay.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    hasMainFrameError = true;
                    if (!firstPageLoaded) {
                        firstPageLoaded = true;
                        splashOverlay.setVisibility(View.GONE);
                    }
                    CharSequence desc = error != null ? error.getDescription() : null;
                    if (desc != null && desc.length() > 0) {
                        errorMessage.setText(getString(R.string.offline_message) + "\n(" + desc + ")");
                    } else {
                        errorMessage.setText(R.string.offline_message);
                    }
                    errorOverlay.setVisibility(View.VISIBLE);
                }
            }
        });

        target.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView newWebView = new WebView(MainActivity.this);
                applyCommonWebSettings(newWebView);

                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        Uri uri = request.getUrl();
                        if (uri != null && !shouldHandleInApp(uri)) {
                            openExternalUri(uri);
                            closePopupWebView();
                            return true;
                        }
                        return false;
                    }
                });

                newWebView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onCloseWindow(WebView window) {
                        closePopupWebView();
                    }
                });

                closePopupWebView();
                popupWebView = newWebView;
                popupContainer.addView(newWebView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                popupContainer.setVisibility(View.VISIBLE);

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                String[] acceptTypes = params != null ? params.getAcceptTypes() : null;
                if (acceptTypes != null && acceptTypes.length == 1 && !acceptTypes[0].isEmpty()) {
                    intent.setType(acceptTypes[0]);
                } else {
                    intent.setType("*/*");
                }
                if (params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }

                try {
                    fileChooserLauncher.launch(Intent.createChooser(intent, "Select file"));
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open file chooser", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    String[] resources = request.getResources();
                    boolean needsAudio = false;
                    for (String res : resources) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                            needsAudio = true;
                        }
                    }
                    if (needsAudio) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED) {
                            request.grant(resources);
                        } else {
                            pendingPermissionRequest = request;
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                        }
                    } else {
                        request.deny();
                    }
                });
            }
        });

        target.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimeType);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) {
                    req.addRequestHeader("cookie", cookies);
                }
                req.addRequestHeader("User-Agent", userAgent);
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                req.setTitle(fileName);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(req);
                    Toast.makeText(MainActivity.this, "Downloading " + fileName, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                openExternalUri(Uri.parse(url));
            }
        });
    }

    private boolean shouldHandleInApp(@NonNull Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String lowerHost = host.toLowerCase();
        return IN_APP_HOSTS.contains(lowerHost) || lowerHost.endsWith(".antigravity.google.com");
    }

    private void openExternalUri(@NonNull Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open link", Toast.LENGTH_SHORT).show();
        }
    }

    private void closePopupWebView() {
        if (popupWebView != null) {
            popupContainer.removeView(popupWebView);
            popupWebView.destroy();
            popupWebView = null;
        }
        popupContainer.setVisibility(View.GONE);
    }

    private void configureBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (popupWebView != null) {
                    if (popupWebView.canGoBack()) {
                        popupWebView.goBack();
                    } else {
                        closePopupWebView();
                    }
                    return;
                }
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        closePopupWebView();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
