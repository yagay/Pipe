package org.schabi.newpipe.player.datasource;

import android.net.Uri;
import android.webkit.CookieManager;

import androidx.annotation.Nullable;

import org.schabi.newpipe.DownloaderImpl;

import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Short-lived network context for one browser-discovered LUC source.
 * WebView remains a source resolver, never a second playback surface.
 * Cookies are read only for the exact outgoing HTTPS host via CookieManager;
 * neither cookies nor signed URLs are logged or persisted.
 */
public final class SupjavLucNetworkContext {
    private static final long TTL_MS = 120_000L;
    private static final Map<String, Profile> VERIFIED = new ConcurrentHashMap<>();

    public static final class Profile {
        public final String userAgent;
        public final long expiresAt;
        private Profile(final String ua) {
            userAgent = ua;
            expiresAt = System.currentTimeMillis() + TTL_MS;
        }
    }

    public static final class Probe {
        public final boolean playable;
        public final int httpStatus;
        public final boolean includeOrigin;
        public final String reason;

        private Probe(final boolean ok, final int status, final boolean origin,
                      final String reason) {
            playable = ok;
            httpStatus = status;
            includeOrigin = origin;
            this.reason = reason;
        }
    }

    private SupjavLucNetworkContext() { }

    @Nullable
    public static Profile forMedia(@Nullable final String mediaUrl) {
        if (mediaUrl == null) return null;
        final Profile value = VERIFIED.get(mediaUrl);
        if (value == null) return null;
        if (System.currentTimeMillis() > value.expiresAt) {
            VERIFIED.remove(mediaUrl, value);
            return null;
        }
        return value;
    }

    /** Register only after one native GET returned real HLS/MP4 bytes. */
    public static void register(final String mediaUrl, final String userAgent) {
        if (!publicHttps(mediaUrl) || userAgent == null || userAgent.isEmpty()) return;
        if (VERIFIED.size() > 20) VERIFIED.clear();
        VERIFIED.put(mediaUrl, new Profile(userAgent));
    }

    /** CookieManager itself applies each host's cookie domain/path policy. */
    @Nullable
    public static String cookiesForRequest(final String requestUrl) {
        if (!publicHttps(requestUrl)) return null;
        try {
            final String cookie = CookieManager.getInstance().getCookie(requestUrl);
            return cookie == null || cookie.isEmpty() ? null : cookie;
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    public static Probe verify(final String mediaUrl, final String referer,
                               final String userAgent) {
        if (!publicHttps(mediaUrl) || !publicHttps(referer)
                || userAgent == null || userAgent.isEmpty()) {
            return new Probe(false, -1, true, "unsafe-url");
        }
        final Probe first = request(mediaUrl, referer, userAgent, true, true);
        if (first.playable || (first.httpStatus != 403 && first.httpStatus != 416)) {
            return first;
        }
        final Probe withoutRange = request(mediaUrl, referer, userAgent, false, true);
        if (withoutRange.playable
                || (withoutRange.httpStatus != 403 && withoutRange.httpStatus != 416)) {
            return withoutRange;
        }
        return request(mediaUrl, referer, userAgent, false, false);
    }

    private static Probe request(final String url, final String ref, final String userAgent,
                                 final boolean range, final boolean includeOrigin) {
        final String origin = Uri.parse(ref).getScheme() + "://"
                + Uri.parse(ref).getHost();
        try {
            final OkHttpClient client = DownloaderImpl.getInstance().getClient()
                    .newBuilder().callTimeout(12, TimeUnit.SECONDS)
                    // Never forward a cookie from a player host to a redirect host.
                    .addInterceptor(chain -> {
                        final Request original = chain.request();
                        final Request.Builder builder = original.newBuilder();
                        final String cookie = cookiesForRequest(original.url().toString());
                        if (cookie != null) builder.header("Cookie", cookie);
                        return chain.proceed(builder.build());
                    }).build();
            final Request.Builder builder = new Request.Builder().url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "*/*")
                    .header("Referer", ref);
            if (includeOrigin) builder.header("Origin", origin);
            if (range) builder.header("Range", "bytes=0-8191");
            try (Response response = client.newCall(builder.build()).execute()) {
                final int status = response.code();
                if (status != 200 && status != 206) {
                    return new Probe(false, status, includeOrigin, "http");
                }
                final okhttp3.ResponseBody body = response.body();
                if (body == null) return new Probe(false, status, includeOrigin, "empty");
                final String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
                if (type.contains("text/html") || type.contains("application/json")) {
                    return new Probe(false, status, includeOrigin, "non-media");
                }
                final byte[] head = new byte[64];
                int count = 0;
                try (InputStream input = body.byteStream()) {
                    while (count < head.length) {
                        final int n = input.read(head, count, head.length - count);
                        if (n <= 0) break;
                        count += n;
                        if (count >= 24) break;
                    }
                }
                final String prefix = new String(head, 0, count,
                        java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim();
                final boolean hls = prefix.startsWith("#EXTM3U");
                final boolean mp4 = count >= 8
                        && head[4] == 'f' && head[5] == 't'
                        && head[6] == 'y' && head[7] == 'p';
                final boolean valid = hls || mp4;
                return new Probe(valid, status, includeOrigin,
                        hls ? "hls" : mp4 ? "mp4" : "invalid-media");
            }
        } catch (final Exception e) {
            return new Probe(false, -1, includeOrigin,
                    e.getClass().getSimpleName());
        }
    }

    public static boolean publicHttps(final String url) {
        if (url == null || url.length() > 4096) return false;
        try {
            final URI uri = URI.create(url);
            final String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            final String lower = host.toLowerCase(Locale.ROOT);
            return lower.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+")
                    && !lower.endsWith(".local")
                    && !lower.endsWith(".localhost")
                    && !lower.endsWith(".internal")
                    && !lower.endsWith(".lan")
                    && !lower.endsWith(".test")
                    && !lower.matches(".*\\.[0-9]+");
        } catch (final Exception ignored) {
            return false;
        }
    }
}
