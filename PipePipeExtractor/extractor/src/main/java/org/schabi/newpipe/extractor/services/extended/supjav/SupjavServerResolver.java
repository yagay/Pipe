package org.schabi.newpipe.extractor.services.extended.supjav;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supjav line adapter. Uses only the selected site's declared player links,
 * not arbitrary ad iframes or untrusted URLs from surrounding markup.
 * JavaScript-only / browser-dependent hosts require a separate fallback.
 */
final class SupjavServerResolver {
    private static final int MAX_LINES = 12;
    private SupjavServerResolver() { }

    static List<VideoStream> resolve(final Document detail, final String detailUrl) {
        final Map<String, VideoStream> found = new LinkedHashMap<>();
        addMedia(found, detail, "Direct");
        int inspected = 0;
        for (final Element item : detail.select(
                ".btn-server[data-link], .btnst a[data-link], .btns a[data-link], "
                + "#server-list [data-link], .server-list [data-link], "
                + ".servers [data-link], [data-server][data-link], "
                + "iframe#video[src], #player-wrap iframe[src], iframe#player[src]")) {
            if (++inspected > MAX_LINES) break;
            final String raw = item.hasAttr("data-link")
                    ? item.attr("data-link") : item.absUrl("src");
            final String name = item.text().trim().isEmpty()
                    ? "Player" : item.text().trim();
            final String link = resolveDeclaredUrl(raw, detailUrl);
            if (link == null) continue;
            if (isMedia(link)) {
                add(found, link, name);
                continue;
            }
            try {
                final Map<String, List<String>> headers = new java.util.HashMap<>();
                headers.put("Referer", Collections.singletonList(detailUrl));
                headers.put("User-Agent", Collections.singletonList(
                        "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"));
                final Response response = NewPipe.getDownloader().get(link, headers);
                if (response.responseCode() != 200 || response.responseBody() == null
                        || response.responseBody().length() > 1000000) continue;
                final String finalUrl = response.latestUrl();
                if (finalUrl != null && !safePublicHttps(finalUrl)) continue;
                final Document player = org.jsoup.Jsoup.parse(response.responseBody(),
                        finalUrl == null ? link : finalUrl);
                addMedia(found, player, name);
                // A Supjav wrapper commonly embeds a second provider page.
                // Follow only a declared player frame, not unrelated ad frames.
                int nested = 0;
                for (final Element frame : player.select(
                        "iframe#video[src], iframe#player[src], "
                        + ".player iframe[src], iframe[src*=/embed/], iframe[src*=/e/]")) {
                    if (++nested > 3) break;
                    final String frameUrl = frame.absUrl("src");
                    if (!safePublicHttps(frameUrl)) continue;
                    try {
                        headers.put("Referer", Collections.singletonList(
                                finalUrl == null ? link : finalUrl));
                        final Response nestedResponse = NewPipe.getDownloader()
                                .get(frameUrl, headers);
                        if (nestedResponse.responseCode() != 200
                                || nestedResponse.responseBody() == null
                                || nestedResponse.responseBody().length() > 1000000) continue;
                        final String nestedUrl = nestedResponse.latestUrl();
                        if (nestedUrl != null && !safePublicHttps(nestedUrl)) continue;
                        addMedia(found, org.jsoup.Jsoup.parse(
                                nestedResponse.responseBody(),
                                nestedUrl == null ? frameUrl : nestedUrl), name);
                    } catch (final Exception ignored) {
                        // Keep trying other explicitly declared player frames.
                    }
                }
            } catch (final Exception ignored) {
                // A broken provider must not hide the other player choices.
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void addMedia(final Map<String, VideoStream> out,
                                 final Document page, final String label) {
        for (final String url : Parser.media(page, true)) {
            add(out, url, label);
        }
    }

    private static void add(final Map<String, VideoStream> out,
                            final String url, final String label) {
        if (!safePublicHttps(url) || !isMedia(url) || out.containsKey(url)) return;
        final boolean hls = url.toLowerCase(java.util.Locale.ROOT)
                .matches(".*\\.(?:m3u8|m3u)(?:[?#].*)?");
        final String quality = Parser.resolution(url);
        out.put(url, new VideoStream.Builder()
                .setId(label + " " + quality)
                .setContent(url, true)
                .setResolution(quality)
                .setMediaFormat(MediaFormat.MPEG_4)
                .setDeliveryMethod(hls ? DeliveryMethod.HLS : DeliveryMethod.PROGRESSIVE_HTTP)
                .setIsVideoOnly(false).build());
    }

    static String resolveDeclaredUrl(final String raw, final String detailUrl) {
        if (raw == null || raw.isEmpty() || raw.length() > 2048) return null;
        try {
            final String link;
            if (raw.startsWith("https://")) {
                link = raw;
            } else if (raw.startsWith("//")) {
                link = "https:" + raw;
            } else if (raw.startsWith("/")) {
                link = URI.create(detailUrl).resolve(raw).toString();
            } else if (raw.matches("[A-Za-z0-9_-]{3,512}")) {
                link = "https://lk1.supremejav.com/supjav.php?c="
                        + new StringBuilder(raw).reverse();
            } else {
                return null;
            }
            return safePublicHttps(link) ? link : null;
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static boolean isMedia(final String url) {
        final String lower = URI.create(url).getPath().toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".m3u8") || lower.endsWith(".m3u")
                || lower.endsWith(".mp4");
    }

    private static boolean safePublicHttps(final String link) {
        try {
            final URI url = URI.create(link);
            final String host = url.getHost();
            return "https".equalsIgnoreCase(url.getScheme()) && host != null
                    && url.getUserInfo() == null && url.getPort() == -1
                    && host.matches("(?i)[a-z0-9-]+(?:\\.[a-z0-9-]+)+")
                    && !host.matches(".*\\.(?:local|localhost|internal|lan|test)")
                    && !host.matches(".*\\.[0-9]+")
                    && !link.toLowerCase(java.util.Locale.ROOT)
                        .matches(".*(?:advert|banner|popunder|preroll|tracker).*");
        } catch (final Exception ignored) {
            return false;
        }
    }
}
