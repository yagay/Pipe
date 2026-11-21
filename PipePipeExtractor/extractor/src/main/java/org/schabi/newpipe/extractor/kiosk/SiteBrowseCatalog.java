package org.schabi.newpipe.extractor.kiosk;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;

/**
 * Native browse entries advertised by each website's actual extractor.
 *
 * This is intentionally conservative: do not invent website category URLs
 * when a service has not implemented extraction for that page type.
 * Site-specific providers can later implement extra categories and filters.
 */
public final class SiteBrowseCatalog {
    public static final class Entry {
        private final String id;
        private final String label;
        private final String url;

        public Entry(final String id, final String label, final String url) {
            this.id = id;
            this.label = label;
            this.url = url;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public String getUrl() {
            return url;
        }
    }

    private SiteBrowseCatalog() {
    }

    /**
     * Discover only supported native kiosk destinations. New site-specific
     * directory types must not be silently routed through a video kiosk.
     */
    public static List<Entry> getEntries(final StreamingService service)
            throws ExtractionException {
        final KioskList kiosks = service.getKioskList();
        final List<String> ids = new ArrayList<>(kiosks.getAvailableKiosks());
        ids.sort(Comparator.comparingInt(SiteBrowseCatalog::order)
                .thenComparing(String::compareToIgnoreCase));
        final List<Entry> result = new ArrayList<>();
        for (final String id : ids) {
            try {
                final ListLinkHandlerFactory factory =
                        kiosks.getListLinkHandlerFactoryByType(id);
                final String url = factory.fromId(id).getUrl();
                if (url == null || url.trim().isEmpty()) {
                    continue;
                }
                result.add(new Entry(id, labelFor(id), url));
            } catch (final Exception ignored) {
                // An individual unsupported kiosk should not break the website.
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static int order(final String id) {
        if ("latest".equalsIgnoreCase(id)) {
            return 0;
        }
        if ("popular".equalsIgnoreCase(id)) {
            return 1;
        }
        if ("recommended".equalsIgnoreCase(id)) {
            return 2;
        }
        return 10;
    }

    private static String labelFor(final String id) {
        if ("latest".equalsIgnoreCase(id)) {
            return "Latest";
        }
        if ("popular".equalsIgnoreCase(id)) {
            return "Popular";
        }
        if ("recommended".equalsIgnoreCase(id)) {
            return "Recommended";
        }
        return id.replace('_', ' ').replace('-', ' ');
    }
}
