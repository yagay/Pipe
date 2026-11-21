package org.schabi.newpipe.extractor.services.extended.supjav;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Limited Supjav media preflight, modeled after the standalone Supjav resolver.
 * Only probes public media declared by a selected provider page.
 * No CAPTCHA bypass, guessed tokens, or aggressive retries.
 */
final class SupjavMediaProbe {
    enum Status { PLAYABLE, BLOCKED, UNKNOWN }

    private static final String AGENT =
            "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    private SupjavMediaProbe() { }

    static Status probe(final VideoStream stream) {
        final String content = stream.getContent();
        final int marker = content.indexOf("#supjav=1&ref=");
        if (marker < 0) return Status.UNKNOWN;
        final String url = content.substring(0, marker);
        final String ref = decode(content.substring(marker + "#supjav=1&ref=".length()));
        if (!url.startsWith("https://") || !ref.startsWith("https://")) {
            return Status.UNKNOWN;
        }
        final String origin;
        try {
            final URI uri = URI.create(ref);
            origin = uri.getScheme() + "://" + uri.getHost();
        } catch (final Exception e) {
            return Status.UNKNOWN;
        }

        // Downloading an MP4 to probe its header could buffer an entire movie
        // in the extractor's Downloader. Use HEAD for progressive media instead.
        if (stream.getDeliveryMethod() != DeliveryMethod.HLS) {
            try {
                final Response head = NewPipe.getDownloader().head(url, headers(ref, origin, false));
                final int status = head.responseCode();
                if (status == 404 || status == 410) return Status.BLOCKED;
                if (status >= 200 && status < 300) {
                    final String type = head.getHeader("Content-Type");
                    return type != null && (type.toLowerCase(java.util.Locale.ROOT)
                            .contains("text/html") || type.toLowerCase(java.util.Locale.ROOT)
                            .contains("application/json"))
                            ? Status.BLOCKED : Status.PLAYABLE;
                }
            } catch (final Exception ignored) {
                // Several video hosts reject HEAD although a GET works.
            }
            return Status.UNKNOWN;
        }

        // HLS manifests are short text documents. Validate the actual body,
        // not just an extension or an HTML snippet which happens to mention it.
        final ProbeResult first = checkHls(url, headers(ref, origin, true));
        if (first.status == Status.PLAYABLE) return Status.PLAYABLE;
        if (first.code != 403 && first.code != 416) return first.status;

        // A range request is sometimes denied even though a full GET works.
        final ProbeResult normal = checkHls(url, headers(ref, origin, false));
        if (normal.status == Status.PLAYABLE) return Status.PLAYABLE;
        if (normal.code != 403 && normal.code != 416) return normal.status;

        // A small subset of players require Referer but explicitly reject Origin.
        return checkHls(url, headers(ref, null, false)).status;
    }

    private static ProbeResult checkHls(final String url,
                                        final Map<String, List<String>> headers) {
        // Only read the first 8 KiB, even when the server ignores Range and
        // sends a massive body. Always close the connection after the probe.
        try (StreamingResponse response =
                     NewPipe.getDownloader().getStreaming(url, headers)) {
            final int code = response.responseCode();
            if (code == 403 || code == 404 || code == 410 || code == 429) {
                return new ProbeResult(Status.BLOCKED, code);
            }
            if (code < 200 || code >= 300) return new ProbeResult(Status.UNKNOWN, code);
            final String type = response.getHeader("Content-Type");
            if (type != null && (type.toLowerCase(java.util.Locale.ROOT)
                    .contains("text/html") || type.toLowerCase(java.util.Locale.ROOT)
                    .contains("application/json"))) {
                return new ProbeResult(Status.BLOCKED, code);
            }
            final byte[] prefix = new byte[8192];
            int count = 0;
            while (count < prefix.length) {
                final int n = response.body().read(prefix, count, prefix.length - count);
                if (n <= 0) break;
                count += n;
                if (count >= 24) break; // Enough to recognize an HLS manifest.
            }
            final String text = new String(prefix, 0, count,
                    java.nio.charset.StandardCharsets.UTF_8);
            final String head = text.replace("\uFEFF", "").trim();
            return new ProbeResult(head.startsWith("#EXTM3U")
                    ? Status.PLAYABLE : Status.BLOCKED, code);
        } catch (final Exception ignored) {
            return new ProbeResult(Status.UNKNOWN, -1);
        }
    }

    private static Map<String, List<String>> headers(final String ref,
                                                     final String origin,
                                                     final boolean range) {
        final Map<String, List<String>> result = new HashMap<>();
        result.put("Referer", Collections.singletonList(ref));
        result.put("User-Agent", Collections.singletonList(AGENT));
        result.put("Accept", Collections.singletonList("*/*"));
        if (origin != null) result.put("Origin", Collections.singletonList(origin));
        if (range) result.put("Range", Collections.singletonList("bytes=0-8191"));
        return result;
    }

    private static String decode(final String text) {
        try {
            return java.net.URLDecoder.decode(text, "UTF-8");
        } catch (final Exception e) {
            return "";
        }
    }

    private static final class ProbeResult {
        final Status status;
        final int code;
        ProbeResult(final Status status, final int code) {
            this.status = status;
            this.code = code;
        }
    }
}
