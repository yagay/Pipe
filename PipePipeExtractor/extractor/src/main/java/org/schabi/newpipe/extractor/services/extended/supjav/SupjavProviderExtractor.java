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
 * Site-specific, bounded, static player declaration adapters.
 * Never evaluate remote JavaScript or scan unrelated detail-page advertisements.
 * Maintained separately from the server catalog and navigation.
 */
final class SupjavProviderExtractor {
    private SupjavProviderExtractor() { }

    static List<VideoStream> extract(final Document page,
                                     final SupjavServerCatalog.Entry server) {
        final Map<String, VideoStream> found = new LinkedHashMap<>();
        final String label = server.part + "|" + server.provider;
        // Provider-owned declarations take precedence over any generic HTML
        // source, which may be a preview or advertisement on some mirrors.
        if ("FST".equals(server.provider) || "FAST".equals(server.provider)
                || "FPLAYER".equals(server.provider)) {
            addFst(found, page, label);
        }
        if ("ST".equals(server.provider) || "STREAMTAPE".equals(server.provider)
                || "STPLAYER".equals(server.provider)) {
            addStreamtape(found, page, label);
        }
        if ("VAS".equals(server.provider)) {
            addVas(found, page, label);
        }
        addMedia(found, page, label);
        return new ArrayList<>(found.values());
    }

    private static void addFst(final Map<String, VideoStream> found,
                               final Document page, final String label) {
        final String html = page.html();
        final java.util.regex.Matcher links = java.util.regex.Pattern.compile(
                "(?s)(?:var|let|const)\\s+links\\s*=\\s*\\{(.{1,30000}?)\\}\\s*[;,]"
        ).matcher(html);
        if (!links.find()) return;
        final Map<String, String> values = new LinkedHashMap<>();
        final java.util.regex.Matcher options = java.util.regex.Pattern.compile(
                "(?i)[\\\"']?(hls[234])[\\\"']?\\s*:\\s*[\\\"']([^\\\"']{1,3000})[\\\"']"
        ).matcher(links.group(1));
        while (options.find()) {
            values.put(options.group(1).toLowerCase(java.util.Locale.ROOT),
                    options.group(2).replace("\\/", "/"));
        }
        final java.util.regex.Matcher selector = java.util.regex.Pattern.compile(
                "(?is)(?:file|[\\\"']file[\\\"'])\\s*:\\s*"
                + "(links\\.hls[234](?:\\s*\\|\\|\\s*links\\.hls[234])*)"
        ).matcher(html);
        if (!selector.find()) return;
        final java.util.regex.Matcher candidates = java.util.regex.Pattern.compile(
                "links\\.(hls[234])").matcher(selector.group(1));
        while (candidates.find()) {
            final String value = values.get(candidates.group(1).toLowerCase(java.util.Locale.ROOT));
            if (value == null) continue;
            try {
                final String url = URI.create(page.location()).resolve(value).toString();
                if (isMedia(url)) add(found, url, label, page.location());
            } catch (final Exception ignored) { }
        }
    }

    private static void addStreamtape(final Map<String, VideoStream> found,
                                      final Document page, final String label) {
        final java.util.regex.Pattern expression = java.util.regex.Pattern.compile(
                "(?is)(?:getElementById\\(\\s*['\\\"]robotlink['\\\"]\\s*\\)"
                + "|querySelector\\(\\s*['\\\"]#robotlink['\\\"]\\s*\\))"
                + "\\.innerHTML\\s*=\\s*['\\\"]([^'\\\"]*)['\\\"]\\s*\\+\\s*"
                + "(?:['\\\"]{2}\\s*\\+\\s*)?"
                + "\\(\\s*['\\\"]([^'\\\"]*)['\\\"]\\s*\\)"
                + "((?:\\s*\\.(?:substring|substr|slice)\\(\\s*\\d+\\s*\\))+)");
        final java.util.regex.Matcher matcher = expression.matcher(page.html());
        if (!matcher.find()) return;
        String suffix = matcher.group(2);
        final java.util.regex.Matcher trims = java.util.regex.Pattern.compile(
                "\\.(?:substring|substr|slice)\\(\\s*(\\d+)\\s*\\)").matcher(matcher.group(3));
        while (trims.find()) {
            final int n = Integer.parseInt(trims.group(1));
            suffix = suffix.substring(Math.min(suffix.length(), n));
        }
        final String candidate = matcher.group(1) + suffix;
        final String url = candidate.startsWith("//") ? "https:" + candidate : candidate;
        try {
            final URI uri = URI.create(url);
            if ("/get_video".equals(uri.getPath())
                    && ("streamtape.com".equalsIgnoreCase(uri.getHost())
                    || "streamtape.to".equalsIgnoreCase(uri.getHost()))) {
                add(found, url, label, page.location());
            }
        } catch (final Exception ignored) { }
    }

    private static void addVas(final Map<String, VideoStream> found,
                               final Document page, final String label) {
        if (!page.html().contains("/api/stream")) return;
        try {
            final URI player = URI.create(page.location());
            final java.util.regex.Matcher code = java.util.regex.Pattern.compile(
                    "^/e/([A-Za-z0-9]{2,120})/?$").matcher(player.getPath());
            if (!code.find() || !safePublicHttps(page.location())) return;
            final String api = player.getScheme() + "://" + player.getHost()
                    + "/api/stream" + (player.getRawQuery() == null ? ""
                    : "?" + player.getRawQuery());
            final String body = new org.json.JSONObject()
                    .put("filecode", code.group(1)).put("device", "web")
                    .put("codecs", new org.json.JSONArray()).toString();
            final Map<String, List<String>> headers = new java.util.HashMap<>();
            headers.put("Referer", Collections.singletonList(page.location()));
            headers.put("Content-Type", Collections.singletonList("application/json"));
            final Response response = NewPipe.getDownloader().post(api, headers,
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (response.responseCode() == 200) {
                add(found, new org.json.JSONObject(response.responseBody())
                        .optString("streaming_url"), label, page.location());
            }
        } catch (final Exception ignored) {
            // Provider-specific API failure does not affect another CD or server.
        }
    }

    private static void addMedia(final Map<String, VideoStream> out,
                                 final Document page, final String label) {
        // Native HTML5 sources are unambiguous; scanning arbitrary page scripts
        // for a URL would also collect advertising or expired preview assets.
        for (final String url : Parser.media(page, false)) {
            add(out, url, label, page.location());
        }
        final java.util.regex.Pattern declared = java.util.regex.Pattern.compile(
                "(?i)(?:urlPlay|streaming_url|file|source)\\s*[:=]\\s*[\\\"']"
                + "(https?://[^\\\"'<>\\s]{8,2048})[\\\"']");
        for (final Element script : page.select("script:not([src])")) {
            final String rawScript = script.data().isEmpty() ? script.html() : script.data();
            for (final String source : new String[]{rawScript, Parser.unpackPacker(rawScript)}) {
                final java.util.regex.Matcher match = declared.matcher(source);
                while (match.find()) {
                    add(out, match.group(1).replace("\\/", "/"),
                            label, page.location());
                }
            }
        }
        // EarnVids/LuluStream/FST may declare signed HLS URLs under
        // hls2/hls4/hls3 instead of a normal <video> element.
        final java.util.regex.Pattern signedHls = java.util.regex.Pattern.compile(
                "(?i)(?:[\\\"']?)(hls[234])(?:[\\\"']?)\\s*:\\s*[\\\"']([^\\\"'<>]{8,3000})[\\\"']");
        final java.util.Map<String, String> hls = new LinkedHashMap<>();
        for (final Element script : page.select("script:not([src])")) {
            final String content = script.data().isEmpty() ? script.html() : script.data();
            final java.util.regex.Matcher found = signedHls.matcher(content);
            while (found.find()) {
                final String tag = found.group(1).toLowerCase(java.util.Locale.ROOT);
                try {
                    final String raw = found.group(2).replace("\\/", "/");
                    final String url = URI.create(page.location()).resolve(raw).toString();
                    if (safePublicHttps(url) && isMedia(url)) hls.putIfAbsent(tag, url);
                } catch (final Exception ignored) { }
            }
        }
        for (final String tag : new String[]{"hls2", "hls4", "hls3"}) {
            final String url = hls.get(tag);
            if (url != null) add(out, url, label, page.location());
        }
        // VOE publishes the stream in a ROT13 + Base64 JSON declaration.
        for (final Element script : page.select("script[type=application/json]")) {
            try {
                final org.json.JSONArray values = new org.json.JSONArray(script.data());
                final String encoded = values.optString(0);
                if (encoded.length() < 10 || encoded.length() > 30000) continue;
                String clean = encoded;
                for (final String noise : new String[]{"@$", "^^", "~@", "%?", "*~", "!!", "#&", "_"}) {
                    clean = clean.replace(noise, "");
                }
                final StringBuilder rotated = new StringBuilder();
                for (final char c : clean.toCharArray()) {
                    if (c >= 'a' && c <= 'z') rotated.append((char) ('a' + (c - 'a' + 13) % 26));
                    else if (c >= 'A' && c <= 'Z') rotated.append((char) ('A' + (c - 'A' + 13) % 26));
                    else rotated.append(c);
                }
                final String shifted = new String(java.util.Base64.getDecoder()
                        .decode(rotated.toString()), java.nio.charset.StandardCharsets.US_ASCII);
                final StringBuilder normal = new StringBuilder();
                for (final char c : shifted.toCharArray()) normal.append((char) (c - 3));
                final String decoded = new String(java.util.Base64.getDecoder()
                        .decode(normal.reverse().toString()), java.nio.charset.StandardCharsets.UTF_8);
                final String video = new org.json.JSONObject(decoded).optString("source");
                add(out, video, label, page.location());
            } catch (final Exception ignored) { }
        }
    }

    static void add(final Map<String, VideoStream> out,
                            final String url, final String label,
                            final String referer) {
        if (!safePublicHttps(url) || !isMedia(url) || out.containsKey(label + "|" + url)) return;
        final boolean hls = url.toLowerCase(java.util.Locale.ROOT)
                .matches(".*\\.(?:m3u8|m3u)(?:[?#].*)?")
                || URI.create(url).getPath().toLowerCase(java.util.Locale.ROOT)
                    .matches(".*/hls[0-9]*/(?:master|index|playlist)[^/]*\\.txt");
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

    static boolean isMedia(final String url) {
        final String lower = URI.create(url).getPath().toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".m3u8") || lower.endsWith(".m3u")
                || lower.endsWith(".mp4")
                || (("/get_video".equals(lower))
                    && (URI.create(url).getHost().equalsIgnoreCase("streamtape.com")
                    || URI.create(url).getHost().equalsIgnoreCase("streamtape.to"))
                    && URI.create(url).getRawQuery() != null)
                || lower.matches(".*/hls[0-9]*/(?:master|index|playlist)[^/]*\\.txt");
    }

    static boolean safePublicHttps(final String link) {
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
