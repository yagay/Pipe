package org.schabi.newpipe.extractor.services.extended.supjav;

import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * LUC native-only gateway, ported from the working Supjav OkHttpMediaGateway.
 *
 * An independent client is important: NewPipe Downloader carries global app
 * cookies and auto-follows redirects without updating Referer. This client
 * intentionally does neither. Media3 uses a separate DefaultHttpDataSource,
 * just like the standalone Supjav app.
 *
 * No guessed CDN paths, browser verification bypasses, or web player.
 */
final class SupjavLucNativeGateway {
    private static final String AGENT =
            "Mozilla/5.0 (Linux; Android 15; Mobile) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/131.0.0.0 Mobile Safari/537.36";
    private static final int MAX_HOPS = 6;
    private static final int MAX_PAGES = 6;
    private static final int MAX_CANDIDATES = 12;
    private static final long DEADLINE_NS = TimeUnit.SECONDS.toNanos(35);
    private static final String MARKER = "#supjav=1&ref=";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(host -> {
                if (!SupjavProviderExtractor.safePublicHttps("https://" + host)) {
                    throw new UnknownHostException("Unsafe network hostname");
                }
                final List<InetAddress> resolved = Dns.SYSTEM.lookup(host);
                if (resolved.isEmpty()) throw new UnknownHostException("No public network address");
                for (final InetAddress ip : resolved) {
                    if (ip.isAnyLocalAddress() || ip.isLoopbackAddress()
                            || ip.isLinkLocalAddress() || ip.isSiteLocalAddress()
                            || ip.isMulticastAddress() || isNonPublicIpv4(ip)) {
                        throw new UnknownHostException("Non-public network address");
                    }
                }
                return resolved;
            })
            .build();

    private static boolean isNonPublicIpv4(final InetAddress ip) {
        final byte[] address = ip.getAddress();
        if (address.length != 4) return false;
        final int a = address[0] & 0xff;
        final int b = address[1] & 0xff;
        return a == 0 || a >= 224 || (a == 100 && b >= 64 && b <= 127)
                || (a == 169 && b == 254) || (a == 192 && b == 0)
                || (a == 198 && (b == 18 || b == 19));
    }

    private static final class Route {
        final String url;
        final String referer;
        Route(final String url, final String referer) {
            this.url = url;
            this.referer = referer;
        }
    }

    private static final class Exchange {
        final String finalUrl;
        final int status;
        final String contentType;
        final byte[] body;
        Exchange(final String finalUrl, final int status,
                 final String contentType, final byte[] body) {
            this.finalUrl = finalUrl;
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private static final class Probe {
        final boolean playable;
        final String url;
        final int status;
        final boolean includeOrigin;
        final String profile;
        Probe(final boolean playable, final String url,
              final int status, final boolean includeOrigin,
              final String profile) {
            this.playable = playable;
            this.url = url;
            this.status = status;
            this.includeOrigin = includeOrigin;
            this.profile = profile;
        }
    }

    static List<VideoStream> resolve(final SupjavServerCatalog.Entry selected)
            throws IOException, ParsingException {
        if (!"LUC".equalsIgnoreCase(selected.provider)) {
            throw new ParsingException("Only selected LUC uses the native gateway");
        }
        final String start = SupjavProviderExtractor.resolveDeclaredUrl(
                selected.link, selected.page);
        if (start == null) {
            throw new ParsingException("Unsupported LUC selected player URL");
        }
        return new SupjavLucNativeGateway().resolveInternal(selected, start);
    }

    private List<VideoStream> resolveInternal(final SupjavServerCatalog.Entry selected,
                                              final String start)
            throws IOException, ParsingException {
        final ArrayDeque<Route> queue = new ArrayDeque<>();
        final Set<String> visited = new HashSet<>();
        final Set<String> probed = new HashSet<>();
        final List<String> results = new ArrayList<>();
        final long deadline = System.nanoTime() + DEADLINE_NS;
        queue.add(new Route(start, selected.page));
        int pages = 0;
        int attempted = 0;

        while (!queue.isEmpty() && pages < MAX_PAGES
                && attempted < MAX_CANDIDATES && System.nanoTime() < deadline) {
            final Route route = queue.removeFirst();
            if (!SupjavProviderExtractor.safePublicHttps(route.url)
                    || !visited.add(route.url)) continue;
            pages++;

            final Exchange page;
            try {
                page = exchange(route.url, route.referer,
                        "GET", false, true, 1_000_000);
            } catch (final IOException error) {
                results.add("page-network");
                continue;
            }
            if (page.status != 200 || page.body.length == 0
                    || !SupjavProviderExtractor.safePublicHttps(page.finalUrl)) {
                results.add("page=" + page.status);
                continue;
            }
            final Document doc = Jsoup.parse(
                    new String(page.body, StandardCharsets.UTF_8), page.finalUrl);
            if (challenge(doc)) {
                results.add("browser-verification");
                continue;
            }

            // Same player-declared priority as the standalone Supjav:
            // hls2, hls4, hls3, then any actual HTML5 source.
            for (final VideoStream candidate
                    : SupjavProviderExtractor.extract(doc, selected)) {
                final String mediaUrl = mediaUrl(candidate);
                if (mediaUrl == null || !probed.add(mediaUrl)) continue;
                attempted++;
                final String ref = page.finalUrl;
                final boolean hls = candidate.getDeliveryMethod() == DeliveryMethod.HLS;
                final Probe probe = probe(mediaUrl, ref, hls);
                results.add(probe.profile);
                if (probe.playable && SupjavProviderExtractor.safePublicHttps(probe.url)) {
                    final String fragment = candidate.getContent()
                            .substring(candidate.getContent().indexOf(MARKER));
                    final String updated = probe.url + fragment
                            + (probe.includeOrigin ? "" : "&origin=0");
                    final VideoStream verified = new VideoStream.Builder()
                            .setId(candidate.getId())
                            .setContent(updated, true)
                            .setResolution(candidate.getResolution())
                            .setMediaFormat(candidate.getFormat())
                            .setDeliveryMethod(candidate.getDeliveryMethod())
                            .setIsVideoOnly(candidate.isVideoOnly())
                            .build();
                    return SupjavQualityResolver.annotate(Collections.singletonList(verified));
                }
                if (attempted >= MAX_CANDIDATES || System.nanoTime() >= deadline) break;
            }

            // Only follow player iframes from the selected LUC route.
            for (final Element frame : doc.select(
                    "iframe#video[src], iframe#player[src], .player iframe[src], "
                    + "iframe[src*=/e/], iframe[src*=/embed/], "
                    + "iframe[src*=/player/]")) {
                final String url = frame.absUrl("src");
                if (queue.size() >= 8) break;
                if (validPlayer(url) && !visited.contains(url)) {
                    queue.addLast(new Route(url, page.finalUrl));
                }
            }
        }

        throw new ParsingException("Supjav " + selected.label()
                + ": native LUC unavailable"
                + "; mediaCandidates=" + attempted
                + "; pages=" + pages
                + "; checks=" + String.join(",", results.subList(
                    Math.max(0, results.size() - 8), results.size())));
    }

    private static boolean validPlayer(final String url) {
        if (!SupjavProviderExtractor.safePublicHttps(url)) return false;
        try {
            final String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
            return path.matches("^/(?:e|embed|player|v|supjav\\.php)(?:/.*)?$");
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static boolean challenge(final Document doc) {
        final String sample = doc.text().toLowerCase(Locale.ROOT);
        return sample.contains("verify you are human")
                || sample.contains("checking your browser")
                || sample.contains("cloudflare challenge");
    }

    static String mediaUrl(final VideoStream stream) {
        if (stream == null) return null;
        final String content = stream.getContent();
        final int pos = content.indexOf(MARKER);
        if (pos <= 0) return null;
        final String url = content.substring(0, pos);
        return SupjavProviderExtractor.safePublicHttps(url) ? url : null;
    }

    static String checkedRedirect(final String current, final String location) {
        try {
            final String target = URI.create(current).resolve(location).toString();
            return SupjavProviderExtractor.safePublicHttps(target) ? target : null;
        } catch (final Exception ignored) {
            return null;
        }
    }

    private Exchange exchange(final String initial, final String originalReferer,
                              final String method, final boolean range,
                              final boolean includeOrigin, final int maxBytes)
            throws IOException {
        String url = initial;
        String referer = originalReferer;
        final Set<String> seen = new HashSet<>();
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            if (!SupjavProviderExtractor.safePublicHttps(url)
                    || !SupjavProviderExtractor.safePublicHttps(referer)
                    || !seen.add(url)) {
                throw new IOException("Unsafe or looped LUC route");
            }
            final Request.Builder builder = new Request.Builder()
                    .url(url).get()
                    .header("User-Agent", AGENT)
                    .header("Accept", "*/*")
                    .header("Referer", referer);
            if (includeOrigin) {
                final URI ref = URI.create(referer);
                builder.header("Origin", ref.getScheme() + "://" + ref.getHost());
            }
            if (range) builder.header("Range", "bytes=0-8191");

            final okhttp3.Call call = client.newCall(builder.build());
            call.timeout().timeout(range ? 8L : 15L, TimeUnit.SECONDS);
            try (Response response = call.execute()) {
                final int status = response.code();
                if (status == 301 || status == 302 || status == 303
                        || status == 307 || status == 308) {
                    final String location = response.header("Location");
                    if (location == null) throw new IOException("LUC redirect without Location");
                    final String next = checkedRedirect(url, location);
                    if (next == null) throw new IOException("Unsafe LUC redirect");
                    // Supjav updates Referer on *each* redirect before the next GET.
                    referer = url;
                    url = next;
                    continue;
                }
                final String type = response.header("Content-Type", "");
                if (status < 200 || status > 299 || response.body() == null) {
                    return new Exchange(url, status, type, new byte[0]);
                }
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = response.body().byteStream()) {
                    final byte[] buffer = new byte[4096];
                    while (bytes.size() < maxBytes) {
                        final int read = input.read(buffer, 0,
                                Math.min(buffer.length, maxBytes - bytes.size()));
                        if (read < 0) break;
                        if (read == 0) continue;
                        bytes.write(buffer, 0, read);
                    }
                }
                return new Exchange(url, status, type, bytes.toByteArray());
            }
        }
        throw new IOException("Too many LUC redirects");
    }

    private Probe attempt(final String url, final String ref,
                          final boolean hls, final boolean range,
                          final boolean includeOrigin, final String label) {
        try {
            final Exchange response = exchange(
                    url, ref, "PROBE", range, includeOrigin, 8192);
            final int status = response.status;
            if (status != 200 && status != 206) {
                return new Probe(false, null, status, includeOrigin,
                        label + "=" + status);
            }
            final String type = response.contentType.toLowerCase(Locale.ROOT);
            if (type.contains("text/html") || type.contains("application/json")) {
                return new Probe(false, null, status, includeOrigin,
                        label + "=non-media");
            }
            final String prefix = new String(response.body,
                    StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
            final boolean hlsOk = hls && prefix.startsWith("#EXTM3U");
            final boolean mp4Ok = !hls && response.body.length >= 8
                    && response.body[4] == 'f' && response.body[5] == 't'
                    && response.body[6] == 'y' && response.body[7] == 'p';
            final boolean playable = hlsOk || mp4Ok;
            return new Probe(playable, playable ? response.finalUrl : null,
                    status, includeOrigin,
                    label + "=" + (playable ? status : "invalid-media"));
        } catch (final IOException error) {
            return new Probe(false, null, -1, includeOrigin,
                    label + "=network-error");
        }
    }

    private Probe probe(final String url, final String referer, final boolean hls) {
        final Probe range = attempt(url, referer, hls, true, true, "range-origin");
        if (range.playable || !hls || !retryable(range.status)) return range;
        final Probe ordinary = attempt(url, referer, true, false, true, "get-origin");
        if (ordinary.playable || !retryable(ordinary.status)) return ordinary;
        return attempt(url, referer, true, false, false, "get-referer");
    }

    static boolean retryable(final int code) {
        return code == 403 || code == 416;
    }
}
