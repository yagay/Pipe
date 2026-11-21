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
        addMedia(found, detail, "Main|Direct");
        final java.util.List<Element> entries = detail.select(
                ".btn-server[data-link], .btnst a[data-link], .btns a[data-link], "
                + "#server-list [data-link], .server-list [data-link], "
                + ".servers [data-link], [data-server][data-link], "
                + "iframe#video[src], #player-wrap iframe[src], iframe#player[src]");
        final java.util.List<String> labels = new ArrayList<>();
        for (final Element entry : entries) {
            labels.add(serverName(entry));
        }
        // Many multi-CD pages list two identical consecutive sets of providers.
        // Only assign inferred CD numbers when every set matches exactly.
        int groupSize = 0;
        for (int parts = 2; parts <= 10; parts++) {
            if (entries.size() < 6 || entries.size() % parts != 0) continue;
            final int size = entries.size() / parts;
            if (size < 3 || size > 8) continue;
            final java.util.Set<String> expected =
                    new java.util.LinkedHashSet<>(labels.subList(0, size));
            if (expected.size() != size) continue;
            boolean match = true;
            for (int part = 1; part < parts; part++) {
                if (!expected.equals(new java.util.LinkedHashSet<>(
                        labels.subList(part * size, (part + 1) * size)))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                groupSize = size;
                break;
            }
        }
        int inspected = 0;
        for (final Element item : entries) {
            if (inspected >= 80) break;
            final int position = inspected++;
            final String name = labels.get(position);
            final String explicitPart = readPart(item);
            final String part = explicitPart != null ? explicitPart
                    : groupSize > 0 ? "CD" + (position / groupSize + 1) : "Main";
            final String sourceLabel = part + "|" + name;
            final String raw = item.hasAttr("data-link")
                    ? item.attr("data-link") : item.absUrl("src");
            final String link = resolveDeclaredUrl(raw, detailUrl);
            if (link == null) continue;
            if (isMedia(link)) {
                add(found, link, sourceLabel, detailUrl);
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
                addMedia(found, player, sourceLabel);
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
                                nestedUrl == null ? frameUrl : nestedUrl), sourceLabel);
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

    private static String serverName(final Element item) {
        final String value = item.text().trim().isEmpty()
                ? item.attr("data-server").trim() : item.text().trim();
        final String result = value.replaceAll(
                "(?i)\\b(?:CD|DISC|PART)[ _-]*[0-9]+\\\\b", "").trim();
        return result.isEmpty() ? "Player" : result.split("\\s+")[0]
                .toUpperCase(java.util.Locale.ROOT);
    }

    private static String readPart(final Element item) {
        final String raw = item.attr("data-part") + " " + item.attr("data-cd")
                + " " + item.text() + " "
                + (item.parent() == null ? "" : item.parent().attr("data-part"));
        final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?i)(?:^|[^a-z0-9])(?:CD|DISC|PART)[ _-]*0?([1-9][0-9]?)"
        ).matcher(raw);
        return matcher.find() ? "CD" + Integer.parseInt(matcher.group(1)) : null;
    }

    private static void addMedia(final Map<String, VideoStream> out,
                                 final Document page, final String label) {
        for (final String url : Parser.media(page, true)) {
            add(out, url, label, page.location());
        }
    }

    private static void add(final Map<String, VideoStream> out,
                            final String url, final String label,
                            final String referer) {
        if (!safePublicHttps(url) || !isMedia(url) || out.containsKey(label + "|" + url)) return;
        final boolean hls = url.toLowerCase(java.util.Locale.ROOT)
                .matches(".*\\.(?:m3u8|m3u)(?:[?#].*)?");
        final String quality = Parser.resolution(url);
        out.put(label + "|" + url, new VideoStream.Builder()
                .setId("SUPJAV|" + label + "|" + quality)
                .setContent(url + "#supjav=1&ref=" + encode(referer), true)
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

    private static String encode(final String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (final Exception ignored) {
            return "";
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
