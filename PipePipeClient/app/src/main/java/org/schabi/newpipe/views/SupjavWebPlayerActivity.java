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
    private boolean selectedApplied = false;
    private boolean selectionStarted = false;
    private final Handler selectionHandler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LinearLayout content;
    private WebView browser;
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

        final TextView back = new TextView(this);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setPadding(dp(18), 0, dp(12), 0);
        back.setText("‹  Supjav · Web player");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        back.setOnClickListener(v -> finish());
        content.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        browser = new WebView(this);
        content.addView(browser, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
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
            public void onPageFinished(final WebView view, final String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                if (selectionStarted || selectedServer < 0 || !url.equals(loadedUrl)) {
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
        if (fullscreen != null) {
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (browser != null) browser.onResume();
    }

    @Override
    protected void onDestroy() {
        selectionHandler.removeCallbacksAndMessages(null);
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
