package org.schabi.newpipe.extractor.kiosk;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.services.kissjav.KissJavService;
import org.schabi.newpipe.extractor.services.kissjav.KissJavParsingHelper;
import org.schabi.newpipe.extractor.services.eightyfivepo.EightyFivePoService;
import org.schabi.newpipe.extractor.services.eightyfivepo.EightyFivePoParsingHelper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
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

    /**
     * Website categories are supported only where the existing video-card
     * extractor can parse category listing pages. Do not expose directory pages
     * or off-site links as playable categories.
     */
    public static List<Entry> getWebsiteCategories(final StreamingService service)
            throws IOException, ExtractionException {
        final String base;
        final String index;
        final Document document;
        if (service instanceof KissJavService) {
            base = KissJavParsingHelper.BASE_URL;
            index = base + "/categories/";
            document = KissJavParsingHelper.fetchDocument(index);
        } else if (service instanceof EightyFivePoService) {
            base = EightyFivePoParsingHelper.BASE_URL;
            index = base + "/ja/";
            document = EightyFivePoParsingHelper.fetchDocument(index);
        } else {
            return Collections.emptyList();
        }

        final Map<String, Entry> entries = new LinkedHashMap<>();
        for (final Element link : document.select("a[href]")) {
            final String rawUrl = link.absUrl("href");
            final String label = link.text().trim();
            if (label.length() < 2 || label.length() > 45) {
                continue;
            }
            final String path;
            try {
                final URI uri = new URI(rawUrl);
                final URI host = new URI(base);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || !host.getHost().equalsIgnoreCase(uri.getHost())) {
                    continue;
                }
                path = uri.getPath();
            } catch (final URISyntaxException ignored) {
                continue;
            }
            if (path == null || !path.matches(service instanceof KissJavService
                    ? "/categories/[^/]+/?"
                    : "/ja/categories/[^/]+/?")) {
                continue;
            }
            final String url = rawUrl.split("[?#]", 2)[0];
            entries.putIfAbsent(url, new Entry("category:" + url, label, url));
        }
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    /** Read the site's real sort links; keep the selected category path unchanged. */
    public static List<Entry> getWebsiteSorts(final StreamingService service,
                                              final String pageUrl)
            throws IOException, ExtractionException {
        final String base;
        final Document document;
        if (service instanceof KissJavService) {
            base = KissJavParsingHelper.BASE_URL;
            document = KissJavParsingHelper.fetchDocument(pageUrl);
        } else if (service instanceof EightyFivePoService) {
            base = EightyFivePoParsingHelper.BASE_URL;
            document = EightyFivePoParsingHelper.fetchDocument(pageUrl);
        } else {
            return Collections.emptyList();
        }
        final Map<String, Entry> sorts = new LinkedHashMap<>();
        final URI current;
        try {
            current = new URI(pageUrl);
        } catch (final URISyntaxException e) {
            return Collections.emptyList();
        }
        for (final Element link : document.select("a[href]")) {
            final String label = link.text().trim();
            final URI candidate;
            try {
                candidate = new URI(link.absUrl("href"));
            } catch (final URISyntaxException ignored) {
                continue;
            }
            if (label.isEmpty() || label.length() > 35
                    || candidate.getHost() == null
                    || !candidate.getHost().equalsIgnoreCase(current.getHost())
                    || !current.getPath().equals(candidate.getPath())
                    || candidate.getRawQuery() == null
                    || !candidate.getRawQuery().matches("(?:^|.*&)sort_by=[^&]+(?:&.*)?")) {
                continue;
            }
            final String url = candidate.toString();
            sorts.putIfAbsent(url, new Entry("sort:" + url, label, url));
        }
        if (!sorts.isEmpty()) {
            final String clean = pageUrl.replaceAll("([?&])sort_by=[^&#]*", "$1")
                    .replaceAll("[?&]$", "");
            sorts.put("default", new Entry("sort:" + clean, "Latest", clean));
        }
        return Collections.unmodifiableList(new ArrayList<>(sorts.values()));
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
