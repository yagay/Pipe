package org.schabi.newpipe.extractor.services.extended.supjav;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.StreamingResponse;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional, bounded manifest metadata lookup for selected Supjav sources.
 * Never changes media URLs, signatures, the chosen CD or provider.
 * Media3 remains responsible for actual adaptive-track selection.
 */
final class SupjavQualityResolver {
    private static final int MAX_BYTES = 48 * 1024;
    private static final Pattern SIZE = Pattern.compile(
            "(?i)(?:^|[,\\s])RESOLUTION\\s*=\\s*(\\d{2,5})x(\\d{2,5})");
    private static final String MARKER = "#supjav=1&ref=";

    private SupjavQualityResolver() { }

    static List<VideoStream> annotate(final List<VideoStream> input) {
        if (input == null || input.isEmpty()) return input;
        final List<VideoStream> result = new ArrayList<>(input.size());
        // Keep source discovery bounded. Slow manifest servers must not hold
        // the video detail/initial playback hostage indefinitely.
        int queries = 0;
        for (final VideoStream source : input) {
            if (source.getDeliveryMethod() != DeliveryMethod.HLS
                    || queries >= 3 || !unknown(source.getResolution())) {
                result.add(source);
                continue;
            }
            queries++;
            final String manifest = manifestPrefix(source);
            final int highest = highestVariantHeight(manifest);
            if (highest <= 0) {
                result.add(source);
                continue;
            }
            // A master playlist is adaptive. Its max rendition is NOT the
            // currently decoded resolution; make this explicit in the label.
            final String resolution = "Best (up to " + highest + "p)";
            result.add(new VideoStream.Builder()
                    .setId(source.getId())
                    .setContent(source.getContent(), true)
                    .setResolution(resolution)
                    .setMediaFormat(source.getFormat())
                    .setDeliveryMethod(source.getDeliveryMethod())
                    .setIsVideoOnly(source.isVideoOnly())
                    .build());
        }
        return result;
    }

    static int highestVariantHeight(final String manifest) {
        if (manifest == null || !manifest.startsWith("#EXTM3U")) return -1;
        int highest = -1;
        final String[] lines = manifest.split("\\r?\\n");
        for (final String line : lines) {
            // Do not guess heights from URL names or BANDWIDTH.
            if (!line.startsWith("#EXT-X-STREAM-INF:")
                    && !line.startsWith("#EXT-X-I-FRAME-STREAM-INF:")) continue;
            final Matcher match = SIZE.matcher(line);
            if (!match.find()) continue;
            try {
                final int height = Integer.parseInt(match.group(2));
                if (height >= 144 && height <= 8640) highest = Math.max(highest, height);
            } catch (final NumberFormatException ignored) { }
        }
        return highest;
    }

    private static boolean unknown(final String resolution) {
        return resolution == null || resolution.isEmpty()
                || "Auto".equalsIgnoreCase(resolution)
                || "Unknown".equalsIgnoreCase(resolution);
    }

    private static String manifestPrefix(final VideoStream stream) {
        final String content = stream.getContent();
        final int index = content.indexOf(MARKER);
        if (index < 1) return "";
        final String url = content.substring(0, index);
        if (!SupjavProviderExtractor.safePublicHttps(url)) return "";
        String ref = content.substring(index + MARKER.length());
        final int extra = ref.indexOf('&');
        if (extra >= 0) ref = ref.substring(0, extra);
        try {
            ref = URLDecoder.decode(ref, "UTF-8");
            if (!SupjavProviderExtractor.safePublicHttps(ref)) return "";
            final URI origin = URI.create(ref);
            final Map<String, List<String>> headers = new HashMap<>();
            headers.put("Referer", Collections.singletonList(ref));
            headers.put("Origin", Collections.singletonList(
                    origin.getScheme() + "://" + origin.getHost()));
            headers.put("Accept", Collections.singletonList("*/*"));
            headers.put("Range", Collections.singletonList("bytes=0-" + (MAX_BYTES - 1)));
            try (StreamingResponse response = NewPipe.getDownloader()
                    .getStreaming(url, headers)) {
                if (response.responseCode() != 200 && response.responseCode() != 206) return "";
                final String type = response.getHeader("Content-Type");
                if (type != null && (type.toLowerCase(Locale.ROOT).contains("text/html")
                        || type.toLowerCase(Locale.ROOT).contains("application/json"))) return "";
                final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                final InputStream body = response.body();
                final byte[] buffer = new byte[4096];
                while (bytes.size() < MAX_BYTES) {
                    final int n = body.read(buffer, 0, Math.min(
                            buffer.length, MAX_BYTES - bytes.size()));
                    if (n < 0) break;
                    if (n == 0) continue;
                    bytes.write(buffer, 0, n);
                    if (bytes.size() >= 8192 && bytes.toString("UTF-8")
                            .contains("#EXT-X-ENDLIST")) break;
                }
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8)
                        .replace("\uFEFF", "").trim();
            }
        } catch (final Exception ignored) {
            // Manifest metadata is optional: keep the working original URL.
            return "";
        }
    }
}
