package org.schabi.newpipe.views;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.diagnostics.DiagnosticLog;
import org.schabi.newpipe.extractor.services.extended.supjav.SupjavServerCatalog;
import org.schabi.newpipe.player.datasource.SupjavLucNetworkContext;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Browser is used ONLY to inspect the selected LUC iframe chain.
 * There is no second page or video renderer: a native-verified source is sent
 * back to the existing Pipe detail page and Media3. A 403 is reported, never
 * silently replaced with a WebView player.
 */
public final class SupjavLucBrowserResolver implements AutoCloseable {
    public interface Listener {
        void onVerified(String mediaUrl, String playerReferer, boolean includeOrigin);
        void onUnavailable(String reason);
    }

    private static final long TIMEOUT_MS = 28000L;
    private static final int MAX_CANDIDATES = 6;

    private static final String DOCUMENT_PROBE = """
            (function(){
              if(window.__pipeLucDocumentProbe)return;
              window.__pipeLucDocumentProbe=true;
              function report(){
                var media=[],frames=[];
                function add(v){if(typeof v==='string' && v.indexOf('https://')===0
                  && media.indexOf(v)<0)media.push(v);}
                try{
                  var p=window.jwplayer&&window.jwplayer();
                  var e=p&&p.getPlaylist&&p.getPlaylist()[0];
                  if(e){add(e.file);(e.sources||[]).forEach(function(s){add(s.file);});}
                }catch(_){}
                document.querySelectorAll('video').forEach(function(v){
                  add(v.currentSrc);add(v.src);
                  v.querySelectorAll('source[src]').forEach(function(s){add(s.src);});
                  if(v.paused){try{var promise=v.play();if(promise)promise.catch(function(){});}catch(_){}}
                });
                document.querySelectorAll('iframe[src]').forEach(function(f){
                  if(f.src && f.src.indexOf('https://')===0)frames.push(f.src);
                });
                try{
                  window.PipeLucProbe.postMessage(JSON.stringify({
                    page:location.href,ref:document.referrer,
                    frames:frames.slice(0,12),media:media.slice(0,12)
                  }));
                }catch(_){}
              }
              setInterval(report,650);
              report();
            })();
            """;

    private final Activity activity;
    private final SupjavServerCatalog.Entry selected;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<String> trustedOrigins = new HashSet<>();
    private final Set<String> triedMedia = new HashSet<>();

    private FrameLayout clip;
    private WebView browser;
    private int attempts;
    private String userAgent;
    private Runnable deadline;

    public SupjavLucBrowserResolver(final Activity activity,
                                    final SupjavServerCatalog.Entry selected,
                                    final Listener listener) {
        this.activity = activity;
        this.selected = selected;
        this.listener = listener;
    }

    public void start() {
        if (closed.get() || !"LUC".equalsIgnoreCase(selected.provider)
                || !selected.page.matches("https://(?:www\\.)?supjav\\.com/[0-9]+\\.html")) {
            finishUnavailable("invalid-selected-server");
            return;
        }
        final String wrapper = selectedUrl();
        if (!SupjavLucNetworkContext.publicHttps(wrapper)) {
            finishUnavailable("invalid-selected-iframe");
            return;
        }
        synchronized (trustedOrigins) {
            trustedOrigins.add(origin(selected.page));
            trustedOrigins.add(origin(wrapper));
        }

        try {
            // A 1px clipped host keeps WebView attached to the actual window
            // and leaves the Pipe player and touch controls entirely visible.
            final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
            clip = new FrameLayout(activity);
            clip.setClipChildren(true);
            clip.setClipToPadding(true);
            clip.setClickable(false);
            clip.setFocusable(false);
            clip.setAlpha(0.01f);
            clip.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            decor.addView(clip, new FrameLayout.LayoutParams(1, 1,
                    Gravity.BOTTOM | Gravity.END));
            browser = new WebView(activity);
            final int w = (int) (activity.getResources().getDisplayMetrics().density * 360);
            final int h = (int) (activity.getResources().getDisplayMetrics().density * 210);
            clip.addView(browser, new FrameLayout.LayoutParams(w, h));
            browser.setBackgroundColor(Color.TRANSPARENT);
            browser.setClickable(false);
            browser.setFocusable(false);
            final WebSettings settings = browser.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false);
            settings.setSupportMultipleWindows(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            userAgent = settings.getUserAgentString();
            CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
                    && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addWebMessageListener(browser, "PipeLucProbe",
                        Collections.singleton("*"), (webView, message, sourceOrigin,
                                isMainFrame, replyProxy) -> {
                            if (closed.get() || message.getData() == null) return;
                            try {
                                final JSONObject report = new JSONObject(message.getData());
                                final String page = report.optString("page");
                                if (!origin(page).equals(origin(sourceOrigin.toString()))) return;
                                if (!admitDocument(page, report.optString("ref"))) return;
                                final JSONArray frames = report.optJSONArray("frames");
                                if (frames != null) {
                                    for (int i = 0; i < Math.min(frames.length(), 12); i++) {
                                        trustFrame(page, frames.optString(i));
                                    }
                                }
                                final JSONArray media = report.optJSONArray("media");
                                if (media != null) {
                                    for (int i = 0; i < Math.min(media.length(), 12); i++) {
                                        offer(media.optString(i), page);
                                    }
                                }
                            } catch (final Exception ignored) {
                                // Do not trust unstructured or unverified frame reports.
                            }
                        });
                WebViewCompat.addDocumentStartJavaScript(browser, DOCUMENT_PROBE,
                        Collections.singleton("*"));
            }

            browser.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onCreateWindow(final WebView view, final boolean dialog,
                                              final boolean gesture,
                                              final android.os.Message message) {
                    return false;
                }
            });
            browser.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(final WebView view,
                                                        final WebResourceRequest request) {
                    final String destination = request.getUrl().toString();
                    // Never allow pop-unders or first-party navigation away from
                    // the one selected wrapper and its iframe children.
                    return !SupjavLucNetworkContext.publicHttps(destination)
                            || (request.isForMainFrame()
                            && !selected.page.equals(destination));
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(final WebView view,
                                                                final WebResourceRequest request) {
                    if (closed.get()) return null;
                    final String url = request.getUrl().toString();
                    final String ref = request.getRequestHeaders().entrySet().stream()
                            .filter(e -> "Referer".equalsIgnoreCase(e.getKey()))
                            .map(java.util.Map.Entry::getValue).findFirst().orElse("");
                    if (isTrusted(ref) && isHls(url)) {
                        main.post(() -> offer(url, ref));
                    }
                    return null;
                }
            });
            deadline = () -> finishUnavailable("browser-source-unavailable");
            main.postDelayed(deadline, TIMEOUT_MS);
            final String html = "<!doctype html><html><head>"
                    + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "</head><body style='margin:0'>"
                    + "<iframe title='selected-LUC' src='" + TextUtils.htmlEncode(wrapper)
                    + "' allow='autoplay;fullscreen' style='border:0;width:360px;height:210px'>"
                    + "</iframe></body></html>";
            DiagnosticLog.event("SUPJAV_LUC", "BROWSER_RESOLVE_START",
                    "server=" + selected.index + " part=" + selected.part);
            browser.loadDataWithBaseURL(selected.page, html, "text/html", "UTF-8", null);
        } catch (final Exception e) {
            DiagnosticLog.event("SUPJAV_LUC", "BROWSER_INIT_FAILED",
                    e.getClass().getSimpleName());
            finishUnavailable("browser-init-failed");
        }
    }

    private String selectedUrl() {
        final String token = selected.link.trim();
        if (token.matches("[A-Za-z0-9_-]{3,512}")) {
            return "https://lk1.supremejav.com/supjav.php?c="
                    + new StringBuilder(token).reverse();
        }
        try {
            if (token.startsWith("//")) return "https:" + token;
            if (token.startsWith("/")) return URI.create(selected.page).resolve(token).toString();
            return token;
        } catch (final Exception ignored) {
            return "";
        }
    }

    private static String origin(final String url) {
        if (!SupjavLucNetworkContext.publicHttps(url)) return "";
        final URI uri = URI.create(url);
        return "https://" + uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static boolean playerPath(final String url) {
        try {
            final String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
            return path.matches("^/(?:e|embed|player|v|supjav\\.php)(?:/.*)?$");
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static boolean isHls(final String url) {
        if (!SupjavLucNetworkContext.publicHttps(url)) return false;
        try {
            final String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
            return (path.endsWith(".m3u8") || path.endsWith(".m3u")
                    || path.matches(".*/hls[0-9]*/(?:master|index|playlist)[^/]*\\.txt"))
                    && !path.matches(".*(?:advert|banner|preroll|vast|tracker).*");
        } catch (final Exception ignored) {
            return false;
        }
    }

    private boolean isTrusted(final String url) {
        final String value = origin(url);
        if (value.isEmpty()) return false;
        synchronized (trustedOrigins) {
            return trustedOrigins.contains(value);
        }
    }

    private boolean admitDocument(final String page, final String parent) {
        final String pageOrigin = origin(page);
        if (pageOrigin.isEmpty()) return false;
        synchronized (trustedOrigins) {
            if (trustedOrigins.contains(pageOrigin)) return true;
            if (!trustedOrigins.contains(origin(parent)) || !playerPath(page)) return false;
            trustedOrigins.add(pageOrigin);
            return true;
        }
    }

    private void trustFrame(final String parent, final String frame) {
        if (!isTrusted(parent) || !playerPath(frame)) return;
        final String next = origin(frame);
        if (next.isEmpty()) return;
        synchronized (trustedOrigins) {
            trustedOrigins.add(next);
        }
    }

    private void offer(final String media, final String referer) {
        if (closed.get() || !isTrusted(referer) || !isHls(media)) return;
        synchronized (triedMedia) {
            if (attempts >= MAX_CANDIDATES || !triedMedia.add(media)) return;
            attempts++;
        }
        DiagnosticLog.event("SUPJAV_LUC", "BROWSER_MEDIA_CANDIDATE",
                "host=" + Uri.parse(media).getHost()
                        + " refHost=" + Uri.parse(referer).getHost()
                        + " attempt=" + attempts);
        worker.execute(() -> {
            final SupjavLucNetworkContext.Probe result =
                    SupjavLucNetworkContext.verify(media, referer, userAgent);
            main.post(() -> {
                if (closed.get()) return;
                DiagnosticLog.event("SUPJAV_LUC", "BROWSER_NATIVE_PROBE",
                        "status=" + result.httpStatus + " type=" + result.reason
                                + " origin=" + result.includeOrigin);
                if (result.playable) {
                    SupjavLucNetworkContext.register(media, userAgent);
                    finishVerified(media, referer, result.includeOrigin);
                }
            });
        });
    }

    private void finishVerified(final String url, final String ref, final boolean origin) {
        if (!closed.compareAndSet(false, true)) return;
        cleanup();
        listener.onVerified(url, ref, origin);
    }

    private void finishUnavailable(final String reason) {
        if (!closed.compareAndSet(false, true)) return;
        cleanup();
        DiagnosticLog.event("SUPJAV_LUC", "BROWSER_RESOLVE_FAILED", reason);
        listener.onUnavailable(reason);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) cleanup();
    }

    private void cleanup() {
        if (deadline != null) main.removeCallbacks(deadline);
        worker.shutdownNow();
        if (browser != null) {
            browser.stopLoading();
            if (clip != null) clip.removeView(browser);
            browser.destroy();
            browser = null;
        }
        if (clip != null) {
            final ViewGroup parent = (ViewGroup) clip.getParent();
            if (parent != null) parent.removeView(clip);
            clip = null;
        }
    }
}
