package org.schabi.newpipe.extractor.services.missav;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads actual resolutions advertised by a MissAV HLS master playlist.
 *
 * Do not fabricate lower-quality URLs: a CDN can offer just one rendition.
 * A media playlist has no #EXT-X-STREAM-INF records and returns an empty list.
 */
public final class MissAvHlsQualityParser {
    private static final Pattern RESOLUTION =
            Pattern.compile("(?:^|,)\\s*RESOLUTION=(\\d+)x(\\d+)(?:,|$)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern BANDWIDTH =
            Pattern.compile("(?:^|,)\\s*BANDWIDTH=(\\d+)(?:,|$)",
                    Pattern.CASE_INSENSITIVE);
    private static final int MAX_VARIANTS = 24;

    private MissAvHlsQualityParser() {
    }

    public static final class Variant {
        public final String url;
        public final String resolution;
        public final int height;
        public final int bandwidth;

        private Variant(final String url, final int height, final int bandwidth) {
            this.url = url;
            this.height = height;
            this.resolution = height + "p";
            this.bandwidth = bandwidth;
        }
    }

    public static List<Variant> parse(final String masterUrl, final String manifest) {
        final List<Variant> result = new ArrayList<>();
        if (manifest == null || manifest.length() > 1_000_000
                || !manifest.trim().startsWith("#EXTM3U")) {
            return result;
        }
        final URI master;
        try {
            master = new URI(masterUrl);
            if (!"https".equalsIgnoreCase(master.getScheme())) {
                return result;
            }
        } catch (final URISyntaxException ignored) {
            return result;
        }

        final Map<Integer, Variant> byHeight = new LinkedHashMap<>();
        String attributes = null;
        for (final String rawLine : manifest.split("\\r?\\n")) {
            final String line = rawLine.trim();
            if (line.regionMatches(true, 0, "#EXT-X-STREAM-INF:", 0, 18)) {
                attributes = line.substring(18);
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (attributes == null) {
                continue;
            }
            final String attrs = attributes;
            attributes = null;
            final Matcher size = RESOLUTION.matcher(attrs);
            if (!size.find()) {
                continue;
            }
            try {
                final int width = Integer.parseInt(size.group(1));
                final int height = Integer.parseInt(size.group(2));
                if (width < 160 || height < 144 || height > 8640) {
                    continue;
                }
                final URI url = master.resolve(line);
                if (!"https".equalsIgnoreCase(url.getScheme())
                        || url.getHost() == null || url.getUserInfo() != null) {
                    continue;
                }
                final Matcher rate = BANDWIDTH.matcher(attrs);
                final int bandwidth = rate.find() ? Integer.parseInt(rate.group(1)) : 0;
                final Variant variant = new Variant(url.toString(), height, bandwidth);
                final Variant previous = byHeight.get(height);
                if (previous == null || previous.bandwidth < bandwidth) {
                    byHeight.put(height, variant);
                }
                if (byHeight.size() >= MAX_VARIANTS) {
                    break;
                }
            } catch (final NumberFormatException | IllegalArgumentException ignored) {
                // Ignore malformed or unsupported rendition metadata.
            }
        }
        result.addAll(byHeight.values());
        result.sort(Comparator.comparingInt((Variant v) -> v.height).reversed()
                .thenComparing(Comparator.comparingInt((Variant v) -> v.bandwidth).reversed()));
        return result;
    }
}
