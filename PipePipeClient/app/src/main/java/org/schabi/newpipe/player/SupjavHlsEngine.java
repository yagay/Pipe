package org.schabi.newpipe.player;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.schabi.newpipe.diagnostics.DiagnosticLog;
import org.schabi.newpipe.extractor.services.extended.supjav.SupjavServerCatalog;
import org.schabi.newpipe.views.SupjavBrowserHlsDocument;

import java.util.Locale;
import java.util.Map;

/**
 * Browser-backed video engine hosted INSIDE the original Pipe player surface.
 * No own Activity, playback buttons, seekbar, playlist or media session.
 *
 * The resolver WebView executes Supjav's first-party server selection; the
 * visible WebView runs the same reusable HLS.js document as standalone Supjav.
 * Neither Media3's HTTP client nor a synthetic proxy touches signed HLS URLs.
 */
public final class SupjavHlsEngine {
    public interface Listener {
        void onStatus(String status);
        void onPosition(long positionMs, long durationMs, boolean paused);
        void onQuality(String label);
        void onQualityLevels(java.util.List<Integer> heights);
        void onReady();
        void onBuffering();
        void onPlaying();
        void onEnded();
        void onFailure(String reason);
    }

    private final FrameLayout surface;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView resolver;
    private WebView player;
    private View.OnTouchListener gestureListener;
    private int selectedIndex = -1;
    private boolean selectedApplied;
    private boolean attempted;
    private boolean ready;
    private boolean failed;
    private long lastPositionMs = -1;
    private long lastProgressLogUptime;
    private String page;
    private String manifest;
    private String referer;
    private int generation;

    public SupjavHlsEngine(final FrameLayout target, final Listener callback) {
        surface = target;
        listener = callback;
    }

    public boolean isReady() { return ready; }

    /** Route browser video gestures into the existing Pipe gesture controller. */
    public void setGestureListener(final View.OnTouchListener listener) {
        gestureListener = listener;
        if (player != null) player.setOnTouchListener(listener);
    }

    public void open(final String url, final int index) {
        close();
        final String clean = SupjavServerCatalog.pageUrl(url);
        if (clean == null || !clean.matches("https://(?:www\\.)?supjav\\.com/[0-9]+\\.html")
                || index < 0 || index >= 80) {
            listener.onFailure("Invalid Supjav server");
            return;
        }
        page = clean;
        selectedIndex = index;
        DiagnosticLog.event("SUPJAV_ENGINE", "RESOLVER_OPEN",
                "serverIndex=" + index);
        final int active = generation;
        surface.setVisibility(View.VISIBLE);
        surface.setBackgroundColor(0xFF000000);
        resolver = new WebView(surface.getContext());
        // The provider's iframe must have a real viewport so its media
        // request lifecycle is not suppressed by tiny/offscreen rendering.
        // It stays visually transparent and below the video and Pipe controls.
        resolver.setAlpha(0.01f);
        resolver.setClickable(false);
        resolver.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        surface.addView(resolver, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        final WebSettings settings = resolver.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(resolver, true);
        resolver.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(final WebView view,
                                                               final WebResourceRequest request) {
                final Uri address = request.getUrl();
                final String path = address.getPath() == null ? ""
                        : address.getPath().toLowerCase(Locale.ROOT);
                final boolean hls = path.endsWith(".m3u8") || path.endsWith(".m3u")
                        || path.matches(".*/hls[0-9]*/(?:master|index|playlist)[^/]*\\.txt");
                if (!hls || !("https".equalsIgnoreCase(address.getScheme()))) return null;
                final String ref = request.getRequestHeaders().entrySet().stream()
                        .filter(entry -> "Referer".equalsIgnoreCase(entry.getKey()))
                        .map(Map.Entry::getValue).findFirst().orElse("");
                if (!ref.startsWith("https://")) return null;
                main.post(() -> {
                    if (generation != active || attempted || resolver != view) return;
                    manifest = address.toString();
                    referer = ref;
                    startHls(active);
                });
                return null; // DO NOT proxy/intercept provider media
            }

            @Override
            public void onPageFinished(final WebView view, final String url) {
                if (generation != active || selectedApplied || !page.equals(url)) return;
                selectFrame(view, active);
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
                    final String domain = host == null ? ""
                            : host.toLowerCase(Locale.ROOT);
                    return !("supjav.com".equals(domain)
                            || "www.supjav.com".equals(domain));
                }
                return false;
            }

            @Override
            public void onReceivedHttpError(final WebView view,
                                            final WebResourceRequest request,
                                            final WebResourceResponse response) {
                if (response.getStatusCode() >= 400) {
                    DiagnosticLog.event("SUPJAV_ENGINE", "HTTP_ERROR",
                            "host=" + request.getUrl().getHost()
                                    + " status=" + response.getStatusCode());
                }
            }
        });
        listener.onStatus("正在获取播放源…");
        resolver.loadUrl(page);
        main.postDelayed(() -> {
            if (generation == active && !ready && !attempted) {
                listener.onFailure("No browser HLS media discovered");
            }
        }, 18000);
    }

    private void selectFrame(final WebView view, final int active) {
        final int index = selectedIndex;
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
                        + "var button = unique[" + selectedIndex + "];"
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

        final Runnable selector = new Runnable() {
            int attempts;
            @Override public void run() {
                if (generation != active || selectedApplied || attempts++ >= 10) return;
                view.evaluateJavascript(script, result -> {
                    if (generation != active || selectedApplied) return;
                    final String outcome = result == null ? "" : result;
                    if (outcome.contains("frame:") || outcome.contains("site-click")) {
                        selectedApplied = true;
                    } else {
                        main.postDelayed(this, 900);
                    }
                });
            }
        };
        main.post(selector);
    }

    private void startHls(final int active) {
        if (attempted || manifest == null || referer == null || generation != active) return;
        attempted = true;
        listener.onStatus("正在启动 HLS…");
        player = new WebView(surface.getContext());
        player.setBackgroundColor(0xFF000000);
        player.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        final WebSettings settings = player.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (resolver != null) settings.setUserAgentString(
                resolver.getSettings().getUserAgentString());
        CookieManager.getInstance().setAcceptThirdPartyCookies(player, true);
        final WebView playingView = player;
        if (gestureListener != null) player.setOnTouchListener(gestureListener);
        player.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(final ConsoleMessage message) {
                final String msg = message.message();
                if (generation != active || playingView != player
                        || msg == null || !msg.startsWith("PIPE_HLS:")) return false;
                final String state = msg.substring("PIPE_HLS:".length());
                if (state.startsWith("POSITION:")) {
                    final String[] fields = state.substring(9).split(":");
                    if (fields.length == 3) {
                        try {
                            final long pos = (long) (Double.parseDouble(fields[0]) * 1000);
                            final long duration = (long) (Double.parseDouble(fields[1]) * 1000);
                            final boolean paused = "1".equals(fields[2]);
                            listener.onPosition(pos, duration, paused);
                            final long now = android.os.SystemClock.uptimeMillis();
                            if (lastPositionMs < 0 || now - lastProgressLogUptime > 5000) {
                                DiagnosticLog.event("SUPJAV_ENGINE", "VIDEO_PROGRESS",
                                        "posMs=" + pos + " durationMs=" + duration
                                        + " paused=" + paused
                                        + " advanced=" + (lastPositionMs >= 0
                                            && pos > lastPositionMs));
                                lastProgressLogUptime = now;
                                lastPositionMs = pos;
                            }
                        } catch (final NumberFormatException ignored) {
                            DiagnosticLog.event("SUPJAV_ENGINE", "POSITION_PARSE_ERROR",
                                    "invalid progress fields");
                        }
                    }
                } else if (state.startsWith("LEVELS:")) {
                    final java.util.List<Integer> heights = new java.util.ArrayList<>();
                    for (final String height : state.substring(7).split(",")) {
                        if (height.matches("[0-9]{1,5}")) {
                            heights.add(Integer.parseInt(height));
                        }
                    }
                    listener.onQualityLevels(heights);
                } else if (state.startsWith("QUALITY:")) {
                    listener.onQuality(state.substring(8));
                } else if ("READY".equals(state)) {
                    ready = true;
                    DiagnosticLog.event("SUPJAV_ENGINE", "VIDEO_READY",
                            "decoded video data available");
                    listener.onReady();
                } else if ("PLAYING".equals(state)) {
                    ready = true;
                    DiagnosticLog.event("SUPJAV_ENGINE", "VIDEO_PLAYING",
                            "video element reports actual playback");
                    listener.onPlaying();
                    if (resolver != null) resolver.onPause();
                } else if ("ENDED".equals(state)) {
                    listener.onEnded();
                } else if (state.startsWith("ERROR:")) {
                    fail(state.substring(6));
                } else if ("SCRIPT_FAILED".equals(state)) {
                    fail("hls-library-load-failed");
                } else if ("WAITING".equals(state) || "STALLED".equals(state)) {
                    DiagnosticLog.event("SUPJAV_ENGINE", "VIDEO_BUFFERING",
                            "state=" + state);
                    listener.onBuffering();
                } else if ("AUTOPLAY_RESTRICTED".equals(state)) {
                    DiagnosticLog.event("SUPJAV_ENGINE", "AUTOPLAY_RESTRICTED",
                            "manual play is available in the Pipe controls");
                    listener.onStatus("点击播放按钮继续");
                } else if ("BOOTSTRAP".equals(state) || "SCRIPT_LOADED".equals(state)
                        || "MANIFEST_PARSED".equals(state)
                        || state.startsWith("METADATA:")
                        || state.startsWith("FIRST_FRAME:")) {
                    DiagnosticLog.event("SUPJAV_ENGINE", "WEB_STATE",
                            "state=" + state);
                }
                return true;
            }
        });
        player.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(final WebView view, final String url) {
                if (generation == active && view == player) {
                    DiagnosticLog.event("SUPJAV_ENGINE", "HLS_PAGE_LOADED",
                            "width=" + view.getWidth() + " height=" + view.getHeight()
                                    + " visible=" + view.isShown());
                }
            }
            @Override public void onReceivedError(final WebView view,
                    final WebResourceRequest request,
                    final android.webkit.WebResourceError error) {
                if (generation == active && view == player && request.isForMainFrame()) {
                    DiagnosticLog.event("SUPJAV_ENGINE", "HLS_PAGE_ERROR",
                            "code=" + error.getErrorCode());
                    fail("hls-document-load-error");
                }
            }
            @Override public void onReceivedHttpError(final WebView view,
                        final WebResourceRequest request, final WebResourceResponse response) {
                if (response.getStatusCode() >= 400) {
                    DiagnosticLog.event("SUPJAV_ENGINE", "HLS_HTTP_ERROR",
                            "host=" + request.getUrl().getHost()
                            + " status=" + response.getStatusCode());
                }
            }
        });
        surface.addView(player, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        surface.requestLayout();
        player.post(() -> {
            if (generation == active && player == playingView) {
                DiagnosticLog.event("SUPJAV_ENGINE", "VIEW_ATTACHED",
                        "surfaceWidth=" + surface.getWidth()
                        + " surfaceHeight=" + surface.getHeight()
                        + " videoWidth=" + player.getWidth()
                        + " videoHeight=" + player.getHeight()
                        + " visible=" + player.isShown());
            }
        });
        DiagnosticLog.event("SUPJAV_ENGINE", "START",
                "mediaHost=" + Uri.parse(manifest).getHost()
                + " refHost=" + Uri.parse(referer).getHost());
        player.loadDataWithBaseURL(referer,
                SupjavBrowserHlsDocument.render(manifest), "text/html", "UTF-8", null);
        main.postDelayed(() -> {
            if (generation == active && !ready) fail("no-decoded-video-after-18s");
        }, 18000);
    }

    private void fail(final String reason) {
        if (failed) return;
        failed = true;
        DiagnosticLog.event("SUPJAV_ENGINE", "HLS_FAILED",
                "reason=" + reason);
        listener.onFailure(reason);
    }

    public void command(final String safeCommand) {
        final boolean manualPlay = "play()".equals(safeCommand)
                || "toggle()".equals(safeCommand);
        if (player != null && (ready || manualPlay) && safeCommand.matches(
                "(?:toggle\\(\\)|play\\(\\)|pause\\(\\)|cycleQuality\\(\\)|quality\\(-?[0-9]+\\)|seek\\(-?[0-9]+\\)|seekFraction\\([0-9.]+\\)|speed\\([0-9.]+\\))")) {
            player.evaluateJavascript("(function(){if(window.PipeHls)window.PipeHls."
                    + safeCommand + ";})()", null);
        }
    }

    public void close() {
        generation++;
        main.removeCallbacksAndMessages(null);
        ready = false;
        failed = false;
        lastPositionMs = -1;
        lastProgressLogUptime = 0;
        attempted = false;
        selectedApplied = false;
        manifest = null;
        referer = null;
        page = null;
        if (player != null) {
            surface.removeView(player);
            player.stopLoading();
            player.destroy();
            player = null;
        }
        if (resolver != null) {
            surface.removeView(resolver);
            resolver.stopLoading();
            resolver.destroy();
            resolver = null;
        }
        surface.setVisibility(View.GONE);
    }
}
