package org.schabi.newpipe.views;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
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
        settings.setMediaPlaybackRequiresUserGesture(true);
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
                if (selectedApplied || selectedServer < 0 || !url.equals(loadedUrl)) {
                    return;
                }
                selectedApplied = true;
                // The user explicitly selected this server in Pipe. Trigger the
                // site's own player button rather than constructing guessed CDNs,
                // bypassing challenges or injecting arbitrary media URLs.
                final int index = selectedServer;
                final String selector = ".btn-server[data-link],.btnst a[data-link],"
                        + ".btns a[data-link],#server-list [data-link],"
                        + ".server-list [data-link],.servers [data-link],"
                        + "[data-server][data-link]";
                final String script = "(function(){const items=Array.from("
                        + "document.querySelectorAll('" + selector + "'));"
                        + "const unique=items.filter((x,i)=>items.findIndex(y=>"
                        + "y.getAttribute('data-link')===x.getAttribute('data-link'))===i);"
                        + "if(unique[" + index + "]){unique[" + index + "].click();"
                        + "return 'selected';}return 'missing';})()";
                view.evaluateJavascript(script, result ->
                        DiagnosticLog.event("SUPJAV_WEB", "SELECTED_SERVER",
                                "index=" + index + " result=" + result));
            }

            @Override
            public boolean shouldOverrideUrlLoading(final WebView view,
                                                     final WebResourceRequest request) {
                final Uri destination = request.getUrl();
                final String scheme = destination.getScheme();
                return !"https".equalsIgnoreCase(scheme)
                        && !"about".equalsIgnoreCase(scheme);
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
