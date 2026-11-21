package org.schabi.newpipe.extractor.kiosk;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.services.kissjav.KissJavService;
import org.schabi.newpipe.extractor.services.kissjav.KissJavParsingHelper;
import org.schabi.newpipe.extractor.services.eightyfivepo.EightyFivePoService;
import org.schabi.newpipe.extractor.services.eightyfivepo.EightyFivePoParsingHelper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.Jsoup;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.services.pornhub.PornhubParsingHelper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;
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


    private static final long CATEGORY_TTL_MS = 20 * 60 * 1000L;
    private static final long PAGE_TTL_MS = 90 * 1000L;
    private static final Map<String, PageCache> PAGE_CACHE =
            new LinkedHashMap<String, PageCache>(24, .75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, PageCache> entry) {
                    return size() > 24;
                }
            };

    private static final class PageCache {
        final Document document;
        final long time;
        PageCache(final Document document) {
            this.document = document;
            time = System.currentTimeMillis();
        }
    }

    public static synchronized void invalidateCategories(final StreamingService service) {
        CATEGORY_CACHE.remove(service.getServiceId());
        PAGE_CACHE.clear();
    }
    private static final Map<Integer, CategoryCache> CATEGORY_CACHE = new HashMap<>();
    private static final Set<String> HTML_SITES = new HashSet<>(java.util.Arrays.asList(
            "KissJAV", "85po", "Pornhub", "JAV-NONI", "JAVSB", "TOKYO Motion",
            "SpankBang", "xHamster", "XVideos", "EPORNER", "MRDOUGA",
            "OHentai", "XNXX", "141tube", "Avgle.net", "Hanime1.me", "JAV-FUN.cc"));

    private static final class CategoryCache {
        final List<Entry> entries;
        final long updated;
        CategoryCache(final List<Entry> entries) {
            this.entries = entries;
            this.updated = System.currentTimeMillis();
        }
    }

    /** API-based sites keep their existing native endpoints, not a fake HTML navigator. */
    public static boolean supportsHtmlCategories(final StreamingService service) {
        return HTML_SITES.contains(service.getServiceInfo().getName());
    }

    /**
     * Allow-listed website listing URLs only. In particular, reject person
     * directories, account pages, payment pages and arbitrary offsite links.
     */
    public static boolean isWebsiteBrowseUrl(final StreamingService service,
                                              final String url) {
        if (!supportsHtmlCategories(service) || !sameSite(service.getBaseUrl(), url)) {
            return false;
        }
        try {
            final URI uri = new URI(url);
            final String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
            final String q = uri.getRawQuery() == null ? "" : uri.getRawQuery().toLowerCase(Locale.ROOT);
            if (path.matches(".*/(?:actors?|actresses|pornstars?|performers?|models?|members?|login|register|account|users?|profiles?|playlists?|premium|vip|payment|search)/?.*")) {
                return false;
            }
            // Pornhub's category navigation uses /video?c=<id>. Do not
            // accidentally accept a regular video detail or search page.
            if ("Pornhub".equals(service.getServiceInfo().getName())
                    && path.matches("/video/?") && q.matches("(?:^|.*&)c=[0-9]+(?:&.*)?")) {
                return true;
            }
            return path.matches("(?i).*/(?:categories|category|tags|tag|genres|genre|topics|topic|channels|channel|collections|collection|uncensored|amateur|c)/[^/]+/?")
                    || path.matches("(?i).*/(?:categories|category|tags|tag|genres|genre)/[^/]+/page/[0-9]+/?");
        } catch (final URISyntaxException ignored) {
            return false;
        }
    }

    /** Fetch using the original site's headers when possible. */
    public static Document fetchSitePage(final StreamingService service, final String url)
            throws IOException, ExtractionException {
        if (!sameSite(service.getBaseUrl(), url)) {
            throw new IOException("External website URL is not an allowed browse destination");
        }
        if (service instanceof KissJavService) {
            return KissJavParsingHelper.fetchDocument(url);
        }
        if (service instanceof EightyFivePoService) {
            return EightyFivePoParsingHelper.fetchDocument(url);
        }
        if ("Pornhub".equals(service.getServiceInfo().getName())) {
            return PornhubParsingHelper.fetchDocument(url);
        }
        synchronized (PAGE_CACHE) {
            final PageCache cached = PAGE_CACHE.get(url);
            if (cached != null && System.currentTimeMillis() - cached.time < PAGE_TTL_MS) {
                return cached.document;
            }
        }
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Collections.singletonList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"));
        headers.put("Accept", Collections.singletonList(
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
        headers.put("Referer", Collections.singletonList(service.getBaseUrl() + "/"));
        final org.schabi.newpipe.extractor.downloader.Response response =
                NewPipe.getDownloader().get(url, headers);
        if (response.responseCode() < 200 || response.responseCode() >= 300) {
            throw new IOException("Site navigation returned HTTP " + response.responseCode());
        }
        final String finalUrl = response.latestUrl() == null
                || response.latestUrl().isEmpty() ? url : response.latestUrl();
        if (!sameSite(service.getBaseUrl(), finalUrl)) {
            throw new IOException("Website navigation redirected outside the current source");
        }
        final Document document = Jsoup.parse(response.responseBody(), finalUrl);
        synchronized (PAGE_CACHE) {
            PAGE_CACHE.put(url, new PageCache(document));
        }
        return document;
    }

    private static boolean sameSite(final String base, final String target) {
        try {
            final URI site = new URI(base);
            final URI uri = new URI(target);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getUserInfo() != null || uri.getPort() != -1) {
                return false;
            }
            final String host = site.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            final String other = uri.getHost() == null ? ""
                    : uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            // Country-specific subdomain aliases are permitted for Pornhub.
            return host.equals(other)
                    || (host.endsWith("pornhub.com") && other.matches("(?:[a-z]{2}|www)\\.pornhub\\.com"));
        } catch (final URISyntaxException | NullPointerException ignored) {
            return false;
        }
    }

    /**
     * Discover category links from a website's HTML, never guessed paths.
     * One categories index may be fetched if linked from the home page.
     */
    public static synchronized List<Entry> getWebsiteCategories(final StreamingService service)
            throws IOException, ExtractionException {
        if (!supportsHtmlCategories(service)) {
            return Collections.emptyList();
        }
        final int key = service.getServiceId();
        final CategoryCache previous = CATEGORY_CACHE.get(key);
        if (previous != null
                && System.currentTimeMillis() - previous.updated < CATEGORY_TTL_MS) {
            return previous.entries;
        }
        try {
            final String home = service.getBaseUrl() + "/";
            final Document document = fetchSitePage(service,
                    service instanceof KissJavService
                            ? KissJavParsingHelper.BASE_URL + "/categories/" : home);
            final Map<String, Entry> found = new LinkedHashMap<>();
            String categoryIndex = null;
            for (final Element a : document.select("a[href]")) {
                final String target = a.absUrl("href");
                final String label = a.text().replaceAll("\\s+", " ").trim();
                if (label.length() < 2 || label.length() > 52
                        || !sameSite(service.getBaseUrl(), target)) {
                    continue;
                }
                if (isWebsiteBrowseUrl(service, target)) {
                    final String canonical = target.split("#", 2)[0];
                    found.putIfAbsent(canonical, new Entry("category:" + canonical,
                            label, canonical));
                } else if (categoryIndex == null && !target.equals(home)
                        && target.matches("(?i)https://[^/]+/(?:[a-z]{2}/)?(?:categories|category|genres|genre|tags|tag)/?")) {
                    categoryIndex = target;
                }
            }
            if (found.size() < 3 && categoryIndex != null) {
                final Document index = fetchSitePage(service, categoryIndex);
                for (final Element a : index.select("a[href]")) {
                    final String target = a.absUrl("href");
                    final String label = a.text().replaceAll("\\s+", " ").trim();
                    if (label.length() >= 2 && label.length() <= 52
                            && isWebsiteBrowseUrl(service, target)) {
                        final String canonical = target.split("#", 2)[0];
                        found.putIfAbsent(canonical, new Entry("category:" + canonical,
                                label, canonical));
                    }
                }
            }
            if (found.isEmpty() && previous != null) {
                return previous.entries;
            }
            final List<Entry> result = Collections.unmodifiableList(
                    new ArrayList<>(found.values()));
            CATEGORY_CACHE.put(key, new CategoryCache(result));
            return result;
        } catch (final IOException | ExtractionException e) {
            if (previous != null) {
                return previous.entries;
            }
            throw e;
        }
    }

    /**
     * Sort and filter are discovered for the current list only.
     * Same-site + same-path checks prevent leaked account or search links.
     */
    public static List<Entry> getWebsiteSorts(final StreamingService service,
                                              final String pageUrl)
            throws IOException, ExtractionException {
        if (!isWebsiteBrowseUrl(service, pageUrl)) {
            return Collections.emptyList();
        }
        final Document page = fetchSitePage(service, pageUrl);
        final Map<String, Entry> options = new LinkedHashMap<>();
        final URI current;
        try {
            current = new URI(pageUrl);
        } catch (final URISyntaxException e) {
            return Collections.emptyList();
        }
        for (final Element link : page.select("a[href]")) {
            final String label = link.text().replaceAll("\\s+", " ").trim();
            final String target = link.absUrl("href");
            if (label.isEmpty() || label.length() > 40 || !sameSite(service.getBaseUrl(), target)) {
                continue;
            }
            try {
                final URI candidate = new URI(target);
                if (!current.getPath().equals(candidate.getPath()) || candidate.getRawQuery() == null) {
                    continue;
                }
                final String query = candidate.getRawQuery();
                if (!query.matches("(?i)(?:^|.*&)(?:sort|sort_by|order|o)=[^&]+(?:&.*)?")) {
                    continue;
                }
                options.putIfAbsent(target, new Entry("sort:" + target, label, target));
            } catch (final URISyntaxException ignored) {
                // Ignore malformed website links.
            }
        }
        if (!options.isEmpty()) {
            final String clean = pageUrl.replaceAll("([?&])(?:sort|sort_by|order|o)=[^&#]*", "$1")
                    .replaceAll("[?&]$", "");
            options.putIfAbsent(clean, new Entry("sort:" + clean, "Default", clean));
        }
        return Collections.unmodifiableList(new ArrayList<>(options.values()));
    }

    /** Optional filters are only displayed if the current site renders working links. */
    public static List<Entry> getWebsiteFilters(final StreamingService service,
                                                final String pageUrl)
            throws IOException, ExtractionException {
        if (!isWebsiteBrowseUrl(service, pageUrl)) {
            return Collections.emptyList();
        }
        final Document page = fetchSitePage(service, pageUrl);
        final Map<String, Entry> found = new LinkedHashMap<>();
        final URI current;
        try {
            current = new URI(pageUrl);
        } catch (final URISyntaxException ignored) {
            return Collections.emptyList();
        }
        for (final Element a : page.select("a[href]")) {
            final String target = a.absUrl("href");
            final String label = a.text().replaceAll("\\s+", " ").trim();
            if (label.isEmpty() || label.length() > 40
                    || !sameSite(service.getBaseUrl(), target)) {
                continue;
            }
            try {
                final URI candidate = new URI(target);
                if (!current.getPath().equals(candidate.getPath())
                        || candidate.getRawQuery() == null) {
                    continue;
                }
                final String query = candidate.getRawQuery();
                if (!query.matches("(?i)(?:^|.*&)(?:filter|filters|duration|quality|hd|type|orientation|production)=[^&]+(?:&.*)?")) {
                    continue;
                }
                found.putIfAbsent(target, new Entry("filter:" + target, label, target));
            } catch (final URISyntaxException ignored) {
                // Drop malformed filter links.
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(found.values()));
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
