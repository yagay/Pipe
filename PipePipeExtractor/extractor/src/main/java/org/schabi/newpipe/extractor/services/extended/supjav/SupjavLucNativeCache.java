package org.schabi.newpipe.extractor.services.extended.supjav;

import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory bridge between LUC's selected browser iframe and the one Pipe
 * Media3 stream. Never persists a signed URL, cookie, or source token.
 *
 * Only a source that was verified using the actual browser's network context
 * may be published here. Re-check the server link on each extraction so a
 * stale index cannot accidentally play another CD or provider.
 */
public final class SupjavLucNativeCache {
    private static final long TTL_MS = 120_000L;
    private static final Map<String, Record> SOURCES = new ConcurrentHashMap<>();

    private static final class Record {
        final String selectedLink;
        final VideoStream stream;
        final long expires;
        Record(final String selectedLink, final VideoStream stream) {
            this.selectedLink = selectedLink;
            this.stream = stream;
            this.expires = System.currentTimeMillis() + TTL_MS;
        }
    }

    private SupjavLucNativeCache() { }

    public static boolean registerVerified(final SupjavServerCatalog.Entry selected,
                                           final String mediaUrl,
                                           final String playerReferer,
                                           final boolean includeOrigin) {
        if (selected == null || !"LUC".equalsIgnoreCase(selected.provider)
                || selected.index < 0 || selected.index >= 80
                || !selected.page.matches("https://(?:www\\.)?supjav\\.com/[0-9]+\\.html")
                || !SupjavProviderExtractor.safePublicHttps(playerReferer)
                || !SupjavProviderExtractor.safePublicHttps(mediaUrl)
                || !SupjavProviderExtractor.isMedia(mediaUrl)) {
            return false;
        }
        final Map<String, VideoStream> collected = new LinkedHashMap<>();
        SupjavProviderExtractor.add(collected, mediaUrl,
                selected.part + "|" + selected.provider, playerReferer);
        if (collected.isEmpty()) return false;
        VideoStream stream = collected.values().iterator().next();
        stream = SupjavProviderExtractor.withNativeOriginPolicy(stream, includeOrigin);
        if (SOURCES.size() > 24) SOURCES.clear();
        SOURCES.put(key(selected), new Record(selected.link, stream));
        return true;
    }

    static VideoStream find(final SupjavServerCatalog.Entry selected) {
        if (selected == null || !"LUC".equalsIgnoreCase(selected.provider)) return null;
        final Record record = SOURCES.get(key(selected));
        if (record == null) return null;
        if (!selected.link.equals(record.selectedLink)
                || System.currentTimeMillis() > record.expires) {
            SOURCES.remove(key(selected), record);
            return null;
        }
        return record.stream;
    }

    public static void clear(final SupjavServerCatalog.Entry selected) {
        if (selected != null) SOURCES.remove(key(selected));
    }

    private static String key(final SupjavServerCatalog.Entry selected) {
        return SupjavServerCatalog.selectedUrl(selected.page, selected.index);
    }
}
