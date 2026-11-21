package org.schabi.newpipe.views;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import java.util.Map;

import org.schabi.newpipe.diagnostics.DiagnosticLog;
import org.schabi.newpipe.extractor.services.extended.supjav.SupjavServerCatalog;

/**
 * Explicit browser-compatibility fallback for Supjav provider pages requiring
 * JavaScript or browser verification. Does not replace the Pipe Media3 player.
 * Only user-initiated first-party Supjav details may open this activity.
 */
public final class SupjavWebPlayerActivity extends AppCompatActivity {
    private static final String EXTRA_PAGE = "supjav_page";
    private static final String EXTRA_SERVER = "supjav_server";
    private int selectedServer = -1;
    private final java.util.concurrent.atomic.AtomicInteger mediaRequestsSeen =
            new java.util.concurrent.atomic.AtomicInteger();
    private boolean selectedApplied = false;
    private boolean selectionStarted = false;
    private final Handler selectionHandler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout content;
    private WebView browser;
    private TextView statusText;
    private FrameLayout loadingLayer;
    private volatile String observedManifest;
    private volatile String observedReferer;
    private WebView embeddedHls;
    private FrameLayout embeddedContainer;
    private SupjavPlayerControls playerControls;
    private boolean embeddedReady;
    private boolean embeddedAttempted;
    private boolean mediaDiscoveryCompleted;
    private final Handler hlsHandler = new Handler(Looper.getMainLooper());
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    public static void open(final Context context, final String page) {
        open(context, page, SupjavServerCatalog.selectedIndex(page));
    }

    /** Open the selected website player, preserving its normal browser flow. */
    public static void open(final Context context, final String page, final int selectedIndex) {
        final String clean = SupjavServerCatalog.pageUrl(page);
        if (!clean.matches("https://(?:www\\.)?supjav\\.com/[0-9]+\\.html")) {
            Toast.makeText(context, "Invalid Supjav video page", Toast.LENGTH_SHORT).show();
            return;
        }
        final Intent intent = new Intent(context, SupjavWebPlayerActivity.class);
        intent.putExtra(EXTRA_PAGE, clean);
        intent.putExtra(EXTRA_SERVER, selectedIndex);
        // Avoid stacking identical player activities on repeated extractor callbacks.
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(final Bundle state) {
        super.onCreate(state);
        final String url = getIntent().getStringExtra(EXTRA_PAGE);
        if (url == null || !url.matches("https://(?:www\\.)?supjav\\.com/[0-9]+\\.html")) {
            finish();
            return;
        }

        selectedServer = getIntent().getIntExtra(EXTRA_SERVER, -1);
        if (selectedServer < 0 || selectedServer >= 80) selectedServer = -1;

        root = new FrameLayout(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        browser = new WebView(this);
        content.addView(browser, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        // This provider WebView is a live resolver, not the visible player UI.
        // Keep it attached/running to preserve iframe scripts, cookies and
        // browser-network requests; an opaque layer hides the website.
        loadingLayer = new FrameLayout(this);
        loadingLayer.setBackgroundColor(0xFF000000);
        root.addView(loadingLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final LinearLayout loadingControls = new LinearLayout(this);
        loadingControls.setOrientation(LinearLayout.VERTICAL);
        loadingControls.setGravity(Gravity.CENTER);
        statusText = new TextView(this);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        statusText.setText("正在解析视频播放源…");
        loadingControls.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        final TextView websiteButton = new TextView(this);
        websiteButton.setText("使用网站播放器");
        websiteButton.setTextColor(0xFFDDDDDD);
        websiteButton.setGravity(Gravity.CENTER);
        websiteButton.setPadding(dp(16), dp(16), dp(16), dp(16));
        websiteButton.setOnClickListener(v -> revealWebsitePlayer("manual"));
        loadingControls.addView(websiteButton);
        final TextView exit = new TextView(this);
        exit.setText("返回");
        exit.setTextColor(0xFFDDDDDD);
        exit.setGravity(Gravity.CENTER);
        exit.setPadding(dp(16), dp(16), dp(16), dp(16));
        exit.setOnClickListener(v -> finish());
        loadingControls.addView(exit);
        loadingLayer.addView(loadingControls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        scheduleDiscoveryFallback();
        final WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);

        browser.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(final WebView view,
                                                               final WebResourceRequest request) {
                // Observe only. Returning null keeps the provider's real browser
                // network transport, which works where native HTTP gets 403.
                final Uri address = request.getUrl();
                final String path = address.getPath() == null ? ""
                        : address.getPath().toLowerCase(java.util.Locale.ROOT);
                final boolean media = path.endsWith(".m3u8") || path.endsWith(".m3u")
                        || path.endsWith(".mp4") || path.endsWith(".m4s")
                        || path.endsWith(".ts")
                        || path.matches(".*/hls[0-9]*/(?:master|index|playlist)[^/]*\\.txt");
                if (media) {
                    final String refererForPlayback = request.getRequestHeaders().entrySet().stream()
                            .filter(entry -> "Referer".equalsIgnoreCase(entry.getKey()))
                            .map(Map.Entry::getValue).findFirst().orElse("");
                    // Capture actual browser media requests; never generate media URLs
                    // or replace their signed query parameters.
                    final boolean manifest = path.endsWith(".m3u8") || path.endsWith(".m3u")
                            || path.matches(".*/hls[0-9]*/(?:master|index|playlist)[^/]*\\.txt");
                    if (manifest && "https".equalsIgnoreCase(address.getScheme())
                            && refererForPlayback.startsWith("https://")
                            && observedManifest == null) {
                        observedManifest = address.toString();
                        observedReferer = refererForPlayback;
                        runOnUiThread(() -> {
                            if (statusText != null) {
                                statusText.setText("正在启动内置 HLS 播放器…");
                            }
                            if (!embeddedAttempted) {
                                startEmbeddedHls(true);
                            }
                        });
                    }
                }
                if (media && mediaRequestsSeen.getAndIncrement() < 16) {
                    final String referer = request.getRequestHeaders().entrySet().stream()
                            .filter(entry -> "Referer".equalsIgnoreCase(entry.getKey()))
                            .map(java.util.Map.Entry::getValue)
                            .findFirst().orElse("");
                    final String refHost;
                    try {
                        refHost = Uri.parse(referer).getHost();
                    } catch (final Exception ignored) {
                        return null;
                    }
                    final String type = path.endsWith(".m3u8") || path.endsWith(".m3u")
                            || path.endsWith(".txt") ? "manifest"
                            : path.endsWith(".mp4") ? "mp4" : "segment";
                    DiagnosticLog.event("SUPJAV_WEB", "MEDIA_REQUEST",
                            "type=" + type + " host=" + address.getHost()
                                    + " refererHost=" + refHost);
                }
                return null;
            }

            @Override
            public void onPageFinished(final WebView view, final String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                if (selectionStarted || selectedServer < 0
                        || !SupjavServerCatalog.pageUrl(
                                getIntent().getStringExtra(EXTRA_PAGE)).equals(loadedUrl)) {
                    return;
                }
                selectionStarted = true;
                // Match the upstream Supjav browser path: the first-party
                // JumpChain result belongs inside an iframe, never a top-level
                // downloader request. Preserve site cookies and frame context.
                final int index = selectedServer;
                final String script = "(function() {"
                        + "var buttons = Array.from(document.querySelectorAll("
                        + "' .btn-server[data-link],.btnst a[data-link],"
                        + ".btns a[data-link],#server-list [data-link],"
                        + ".server-list [data-link],.servers [data-link],"
                        + "[data-server][data-link]'.trim()));"
                        + "var unique = buttons.filter(function(el, i) {"
                        + "return buttons.findIndex(function(other) {"
                        + "return other.getAttribute('data-link') === "
                        + "el.getAttribute('data-link');}) === i;});"
                        + "var button = unique[" + index + "];"
                        + "if (!button) return 'missing:'+unique.length;"
                        + "var token = button.getAttribute('data-link') || '';"
                        + "var output = '',strategy='none';"
                        + "if (typeof window.JumpChain === 'function') {"
                        + "try { var value = window.JumpChain(token);"
                        + "if(typeof value === 'string') {"
                        + "var m = value.match(/https:\\/\\/[^\\s\\\"'<>]+/i);"
                        + "output = m ? m[0].replace(/&amp;/g,'&') : '';"
                        + "strategy = 'site-jump'; }}"
                        + "catch(e){strategy='site-jump-error';}}"
                        + "function wrapper(raw){try {"
                        + "var u=new URL(raw,location.href);"
                        + "return u.protocol==='https:' && "
                        + "(u.hostname==='supremejav.com' || "
                        + "u.hostname.endsWith('.supremejav.com')) &&"
                        + "u.pathname==='/supjav.php' && u.searchParams.has('c');"
                        + "}catch(e){return false;}}"
                        + "if (!wrapper(output) && /^[A-Za-z0-9_-]{3,512}$/.test(token)) {"
                        + "output='https://lk1.supremejav.com/supjav.php?c=' + "
                        + "token.split('').reverse().join('');"
                        + "strategy='legacy-token';}"
                        + "if(wrapper(output)) {"
                        + "var host=document.getElementById('supjav-native-host');"
                        + "if(!host){host=document.createElement('div');"
                        + "host.id='supjav-native-host';"
                        + "host.style.cssText='position:fixed;inset:0;z-index:2147483000;"
                        + "background:#000;overflow:hidden';"
                        + "(document.body||document.documentElement).appendChild(host);}"
                        + "var frame=document.getElementById('supjav-native-frame');"
                        + "if(!frame){frame=document.createElement('iframe');"
                        + "frame.id='supjav-native-frame';"
                        + "frame.style.cssText='width:100%;height:100%;border:0;display:block';"
                        + "frame.allow='autoplay;fullscreen;picture-in-picture';"
                        + "frame.allowFullscreen=true;host.appendChild(frame);}"
                        + "if(frame.src!==output) frame.src=output;"
                        + "return 'frame:'+strategy;}"
                        + "try {button.click();return 'site-click';}"
                        + "catch(e){return 'click-failed';}"
                        + "})()";
                // Match the standalone Supjav renderer: first-party JumpChain
                // may arrive after the page's initial onPageFinished callback.
                // Retry only the selected line, and stop after a bounded budget.
                selectionHandler.post(new Runnable() {
                    private int attempts;
                    @Override
                    public void run() {
                        if (browser != view || selectedApplied || attempts >= 10
                                || isFinishing() || isDestroyed()) {
                            return;
                        }
                        attempts++;
                        view.evaluateJavascript(script, result -> {
                            if (browser != view || selectedApplied) return;
                            final String state = result == null ? "" : result;
                            final boolean ready = state.contains("frame:")
                                    || state.contains("site-click");
                            DiagnosticLog.event("SUPJAV_WEB", "SELECTED_SERVER",
                                    "index=" + index + " attempt=" + attempts
                                            + " state=" + state);
                            if (ready) {
                                selectedApplied = true;
                            } else if (attempts < 10) {
                                selectionHandler.postDelayed(this, 900);
                            }
                        });
                    }
                });
            }

            @Override
            public void onReceivedHttpError(final WebView view,
                                            final WebResourceRequest request,
                                            final WebResourceResponse response) {
                if (response.getStatusCode() >= 400) {
                    // Do not log full media URLs: signed query strings are sensitive.
                    DiagnosticLog.event("SUPJAV_WEB", "HTTP_ERROR",
                            "host=" + request.getUrl().getHost()
                                    + " status=" + response.getStatusCode()
                                    + " mainFrame=" + request.isForMainFrame());
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(final WebView view,
                                                     final WebResourceRequest request) {
                final Uri destination = request.getUrl();
                final String scheme = destination.getScheme();
                if (!"https".equalsIgnoreCase(scheme)
                        && !"about".equalsIgnoreCase(scheme)) return true;
                if (request.isForMainFrame()) {
                    final String host = destination.getHost();
                    final String domain = host == null ? "" :
                            host.toLowerCase(java.util.Locale.ROOT);
                    // External player hosts remain allowed in selected iframes.
                    // Block unrelated main-frame redirects and advertising.
                    return !("supjav.com".equals(domain)
                            || "www.supjav.com".equals(domain));
                }
                return false;
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(final View view,
                                         final CustomViewCallback callback) {
                if (fullscreen != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreen = view;
                fullscreenCallback = callback;
                content.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
            @Override
            public void onHideCustomView() {
                hideFullScreen();
            }
        });
        DiagnosticLog.event("SUPJAV_WEB", "OPEN", "user requested browser fallback");
        browser.loadUrl(url);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        // CLEAR_TOP | SINGLE_TOP may reuse this activity for another CD.
        // Reset the selected iframe instead of retaining the old video.
        final String nextPage = intent.getStringExtra(EXTRA_PAGE);
        final int nextServer = intent.getIntExtra(EXTRA_SERVER, -1);
        final String currentPage = getIntent().getStringExtra(EXTRA_PAGE);
        final int currentServer = selectedServer;
        if (nextPage == null || !nextPage.matches(
                "https://(?:www\\.)?supjav\\.com/[0-9]+\\.html")
                || nextServer < 0 || nextServer >= 80) return;
        if (nextPage.equals(currentPage) && nextServer == currentServer) return;
        setIntent(intent);
        selectedServer = nextServer;
        selectedApplied = false;
        selectionStarted = false;
        mediaRequestsSeen.set(0);
        observedManifest = null;
        observedReferer = null;
        closeEmbeddedHls();
        embeddedAttempted = false;
        mediaDiscoveryCompleted = false;
        if (loadingLayer != null) loadingLayer.setVisibility(View.VISIBLE);
        if (statusText != null) statusText.setText("正在解析视频播放源…");
        selectionHandler.removeCallbacksAndMessages(null);
        scheduleDiscoveryFallback();
        if (browser != null) {
            browser.stopLoading();
            browser.loadUrl(nextPage);
        }
    }

    /**
     * All HLS-capable Supjav lines use the same browser renderer. Keep the
     * provider page alive for fallback when a new document loses session state.
     */
    private void startEmbeddedHls(final boolean automatic) {
        final String manifest = observedManifest;
        final String referer = observedReferer;
        if (embeddedHls != null || manifest == null || referer == null
                || browser == null) {
            if (!automatic && manifest == null) {
                Toast.makeText(this, "Waiting for a real HLS request",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (!"https".equalsIgnoreCase(Uri.parse(manifest).getScheme())
                || !"https".equalsIgnoreCase(Uri.parse(referer).getScheme())
                || Uri.parse(referer).getHost() == null) return;
        embeddedAttempted = true;
        embeddedReady = false;
        final WebView player = new WebView(this);
        embeddedHls = player;
        player.setBackgroundColor(android.graphics.Color.BLACK);
        final WebSettings settings = player.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(browser.getSettings().getUserAgentString());
        CookieManager.getInstance().setAcceptThirdPartyCookies(player, true);
        player.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedHttpError(final WebView view,
                                            final WebResourceRequest request,
                                            final WebResourceResponse response) {
                if (response.getStatusCode() >= 400) {
                    DiagnosticLog.event("SUPJAV_HLS", "HTTP_ERROR",
                            "host=" + request.getUrl().getHost()
                                    + " status=" + response.getStatusCode());
                }
            }
        });
        player.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(final ConsoleMessage message) {
                final String code = message.message();
                if (code == null || !code.startsWith("PIPE_HLS:")) return false;
                final String state = code.substring("PIPE_HLS:".length());
                if (player != embeddedHls) return true;
                if (state.startsWith("POSITION:")) {
                    final String[] fields = state.substring("POSITION:".length()).split(":");
                    if (fields.length == 3 && playerControls != null) {
                        try {
                            playerControls.updatePosition(
                                    Double.parseDouble(fields[0]),
                                    Double.parseDouble(fields[1]), "1".equals(fields[2]));
                        } catch (final NumberFormatException ignored) { }
                    }
                    return true;
                }
                if (state.startsWith("QUALITY:")) {
                    if (playerControls != null) {
                        playerControls.setQualityLabel(state.substring("QUALITY:".length()));
                    }
                    return true;
                }
                // Never log signed URLs, tokens, script arguments or progress.
                DiagnosticLog.event("SUPJAV_HLS", "STATE", "state=" + state);
                if ("READY".equals(state)) {
                    embeddedReady = true;
                    mediaDiscoveryCompleted = true;
                    if (loadingLayer != null) loadingLayer.setVisibility(View.GONE);
                } else if ("PLAYING".equals(state)) {
                    embeddedReady = true;
                    mediaDiscoveryCompleted = true;
                    if (loadingLayer != null) loadingLayer.setVisibility(View.GONE);
                    browser.onPause();
                } else if (state.startsWith("ERROR:")) {
                    hlsHandler.post(() -> {
                        if (player == embeddedHls) closeEmbeddedHls();
                    });
                }
                return true;
            }
        });
        embeddedContainer = new FrameLayout(this);
        root.addView(embeddedContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        embeddedContainer.addView(player, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        embeddedContainer.bringToFront();
        playerControls = new SupjavPlayerControls(this, new SupjavPlayerControls.Actions() {
            @Override public void close() { finish(); }
            @Override public void playPause() { sendHlsCommand("toggle()"); }
            @Override public void seek(final int seconds) {
                sendHlsCommand("seek(" + seconds + ")");
            }
            @Override public void seekToFraction(final float fraction) {
                sendHlsCommand("seekFraction(" + fraction + ")");
            }
            @Override public void setSpeed(final float speed) {
                sendHlsCommand("speed(" + speed + ")");
            }
            @Override public void cycleQuality() { sendHlsCommand("cycleQuality()"); }
            @Override public void toggleFullscreen() {
                final int orientation = getRequestedOrientation();
                if (orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    getWindow().getDecorView().setSystemUiVisibility(0);
                } else {
                    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }
            }
        });
        embeddedContainer.addView(playerControls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        DiagnosticLog.event("SUPJAV_HLS", "START",
                "mediaHost=" + Uri.parse(manifest).getHost()
                        + " refererHost=" + Uri.parse(referer).getHost()
                        + " automatic=" + automatic);
        player.loadDataWithBaseURL(referer,
                SupjavBrowserHlsDocument.render(manifest), "text/html", "UTF-8", null);
        hlsHandler.postDelayed(() -> {
            if (embeddedHls == player && !embeddedReady) {
                DiagnosticLog.event("SUPJAV_HLS", "TIMEOUT", "no playable manifest");
                closeEmbeddedHls();
            }
        }, 18000);
    }

    private void sendHlsCommand(final String command) {
        if (embeddedHls != null && embeddedReady) {
            embeddedHls.evaluateJavascript(
                    "(function(){if(window.PipeHls){window.PipeHls." + command + ";}})()",
                    null);
        }
    }

    private void closeEmbeddedHls() {
        hlsHandler.removeCallbacksAndMessages(null);
        final WebView player = embeddedHls;
        final FrameLayout overlay = embeddedContainer;
        embeddedHls = null;
        embeddedContainer = null;
        playerControls = null;
        embeddedReady = false;
        if (player != null) {
            player.onPause();
            player.stopLoading();
            player.loadUrl("about:blank");
            if (overlay != null) overlay.removeView(player);
            player.destroy();
        }
        if (root != null && overlay != null) root.removeView(overlay);
        if (browser != null) browser.onResume();
        if (!isFinishing() && !isDestroyed() && !mediaDiscoveryCompleted) {
            revealWebsitePlayer("hls-unavailable");
        }
    }

    private void scheduleDiscoveryFallback() {
        // A reused activity requires a fresh timeout for each video/CD.
        selectionHandler.postDelayed(() -> {
            if (loadingLayer != null && loadingLayer.getVisibility() == View.VISIBLE
                    && embeddedHls == null) {
                revealWebsitePlayer("discovery-timeout");
            }
        }, 18000);
    }

    private void revealWebsitePlayer(final String reason) {
        if (loadingLayer != null) loadingLayer.setVisibility(View.GONE);
        DiagnosticLog.event("SUPJAV", "WEBSITE_FALLBACK", "reason=" + reason);
    }

    private int dp(final int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private void hideFullScreen() {
        if (fullscreen == null) return;
        root.removeView(fullscreen);
        fullscreen = null;
        content.setVisibility(View.VISIBLE);
        final WebChromeClient.CustomViewCallback callback = fullscreenCallback;
        fullscreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
    }

    @Override
    public void onBackPressed() {
        if (embeddedHls != null) {
            // Back from the internal player returns to Pipe, not to the
            // provider's browser page. Website fallback is a separate choice.
            finish();
        } else if (fullscreen != null) {
            hideFullScreen();
        } else if (browser != null && browser.canGoBack()) {
            browser.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (browser != null) browser.onPause();
        if (embeddedHls != null) embeddedHls.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (browser != null && embeddedHls == null) browser.onResume();
        if (embeddedHls != null) embeddedHls.onResume();
    }

    @Override
    protected void onDestroy() {
        selectionHandler.removeCallbacksAndMessages(null);
        closeEmbeddedHls();
        loadingLayer = null;
        DiagnosticLog.event("SUPJAV_WEB", "SESSION_END",
                "mediaRequestsObserved=" + Math.min(mediaRequestsSeen.get(), 16));
        hideFullScreen();
        if (browser != null) {
            browser.stopLoading();
            content.removeView(browser);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}
