package org.schabi.newpipe.extractor.services.missav;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Navigation metadata, independent of the player and kiosk item extraction.
 * Resolve actual navigation URLs from MissAV HTML instead of guessing category slugs.
 */
public final class MissAvBrowseCatalog {
    public static final String[][] GROUPS = {
            {"Watch JAV", "Recent update", "New Releases", "Uncensored leak",
                    "English subtitle", "VR", "Most viewed today",
                    "Most viewed by week", "Most viewed by month"},
            {"Amateur", "SIRO", "LUXU", "GANA", "PRESTIGE PREMIUM", "S-CUTE", "ARA"},
            {"Uncensored", "FC2", "HEYZO", "Tokyo Hot", "1pondo",
                    "Caribbeancom", "Caribbeancompr", "10musume", "pacopacomama",
                    "Gachinco", "XXX-AV", "Married Slash", "Naughty 4610", "Naughty 0930"},
            {"Asia AV", "Madou", "TWAV", "Furuke", "Korean Live", "Chinese Live"}
    };

    public static final class Entry {
        public final String label;
        public final String url;
        public final boolean selected;

        public Entry(final String label, final String url, final boolean selected) {
            this.label = label;
            this.url = url;
            this.selected = selected;
        }
    }

    public static final class PageOptions {
        public final List<Entry> filters;
        public final List<Entry> sorts;

        PageOptions(final List<Entry> filters, final List<Entry> sorts) {
            this.filters = filters;
            this.sorts = sorts;
        }
    }

    private static final long CATEGORY_CACHE_TTL_MS = 30 * 60 * 1000L;
    private static Map<String, List<Entry>> lastGoodCategories;
    private static long lastCategoryLoad;

    private MissAvBrowseCatalog() {
    }

    public static synchronized void invalidateCategories() {
        lastCategoryLoad = 0;
        MissAvParsingHelper.invalidateDocument(
                MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE);
    }

    private static String key(final String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private static String aliasKey(final String label) {
        final String normalized = key(label);
        if ("recentupdate".equals(normalized) || "recentlyupdated".equals(normalized)
                || "latest".equals(normalized)) {
            return "recentupdate";
        }
        if ("newrelease".equals(normalized) || "newreleases".equals(normalized)) {
            return "newreleases";
        }
        if ("englishsubtitle".equals(normalized) || "englishsubtitles".equals(normalized)) {
            return "englishsubtitle";
        }
        if ("prestigepremium".equals(normalized) || "maan".equals(normalized)) {
            return "prestigepremium";
        }
        if ("mostviewedbyweek".equals(normalized) || "mostviewedthisweek".equals(normalized)) {
            return "mostviewedbyweek";
        }
        if ("mostviewedbymonth".equals(normalized) || "mostviewedthismonth".equals(normalized)) {
            return "mostviewedbymonth";
        }
        return normalized;
    }

    public static synchronized Map<String, List<Entry>> getCategories()
            throws IOException, ExtractionException {
        if (lastGoodCategories != null
                && System.currentTimeMillis() - lastCategoryLoad < CATEGORY_CACHE_TTL_MS) {
            return lastGoodCategories;
        }
        try {
            final Document page = MissAvParsingHelper.fetchDocument(
                    MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE);
            final Map<String, String> available = new LinkedHashMap<>();
            // Prefer actual navigation links. An exact label is not required:
            // capitalization, spaces and common menu translations can change.
            for (final Element link : page.select("nav a[href], header a[href], aside a[href], a[href]")) {
                final String label = link.text().trim();
                final String url = normalizedSiteUrl(link.absUrl("href"));
                if (!label.isEmpty() && !url.isEmpty() && isBrowsePage(url)) {
                    available.putIfAbsent(aliasKey(label), url);
                }
            }
            final Map<String, List<Entry>> result = new LinkedHashMap<>();
            int total = 0;
            for (final String[] group : GROUPS) {
                final List<Entry> items = new ArrayList<>();
                final Set<String> seen = new HashSet<>();
                for (int i = 1; i < group.length; i++) {
                    final String label = group[i];
                    final String url = available.get(aliasKey(label));
                    if (url != null && seen.add(url)) {
                        items.add(new Entry(label, url, false));
                    }
                }
                total += items.size();
                result.put(group[0], Collections.unmodifiableList(items));
            }
            // A temporary layout change or blocked HTML must not replace usable
            // navigation with a permanently empty catalog.
            if (total == 0 && lastGoodCategories != null) {
                return lastGoodCategories;
            }
            if (total == 0) {
                throw new IOException("MissAV returned no supported category links");
            }
            lastGoodCategories = Collections.unmodifiableMap(result);
            lastCategoryLoad = System.currentTimeMillis();
            return lastGoodCategories;
        } catch (final IOException | ExtractionException failure) {
            if (lastGoodCategories != null) {
                return lastGoodCategories;
            }
            throw failure;
        }
    }

    public static PageOptions getPageOptions(final String pageUrl)
            throws IOException, ExtractionException {
        final Document page = MissAvParsingHelper.fetchDocument(pageUrl);
        final Map<String, Entry> filters = new LinkedHashMap<>();
        final Map<String, Entry> sorts = new LinkedHashMap<>();
        for (final Element link : page.select("a[href]")) {
            final String label = link.text().trim();
            final String url = normalizedSiteUrl(link.absUrl("href"));
            if (label.isEmpty() || label.length() > 50 || url.isEmpty()
                    || !pathOnly(url).equals(pathOnly(pageUrl))) {
                continue;
            }
            for (final String parameter : new String[]{"filters", "sort"}) {
                final String value = getParameter(url, parameter);
                if (value == null || value.isEmpty()) {
                    continue;
                }
                final String merged = setParameter(pageUrl, parameter, value);
                final boolean active = value.equals(getParameter(pageUrl, parameter));
                ("filters".equals(parameter) ? filters : sorts)
                        .putIfAbsent(value, new Entry(label, merged, active));
            }
        }
        if (!filters.isEmpty()) {
            filters.put("", new Entry("All", removeParameter(pageUrl, "filters"),
                    getParameter(pageUrl, "filters") == null));
        }
        if (!sorts.isEmpty()) {
            sorts.put("", new Entry("Default", removeParameter(pageUrl, "sort"),
                    getParameter(pageUrl, "sort") == null));
        }
        return new PageOptions(new ArrayList<>(filters.values()),
                new ArrayList<>(sorts.values()));
    }

    public static boolean isBrowsePage(final String url) {
        final String path = pathOnly(url);
        if (path.isEmpty() || !isSiteUrl(url)) {
            return false;
        }
        // A people/genre/maker directory needs its own extractor; do not feed
        // those result pages to the generic video Kiosk.
        if (path.matches("(?i).*/(actresses|actress|actors|actor|makers|maker|genres|genre|search|login|register|account|playlists|playlist)$")
                || path.matches("(?i).*/(search|login|register|account|playlists|playlist)/.*")) {
            return false;
        }
        final String slug = path.substring(path.lastIndexOf('/') + 1);
        return slug.isEmpty() || "naughty4610".equals(slug) || "naughty0930".equals(slug)
                || !MissAvParsingHelper.isVideoId(slug);
    }

    public static String normalizedSiteUrl(final String url) {
        if (!isSiteUrl(url)) {
            return "";
        }
        final String result = MissAvParsingHelper.localizeUrl(url);
        return result.replaceFirst("^(https?://(?:www\\.)?missav\\.ws)/dm[0-9]+(?=/)",
                "$1");
    }

    private static boolean isSiteUrl(final String url) {
        try {
            final URI uri = new URI(url);
            final String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("missav.ws")
                    || host.equalsIgnoreCase("www.missav.ws"));
        } catch (final URISyntaxException e) {
            return false;
        }
    }

    private static String pathOnly(final String url) {
        try {
            final String path = new URI(url).getPath();
            return path == null ? "" : path.replaceFirst("^/dm[0-9]+(?=/)", "")
                    .replaceFirst("/+$", "");
        } catch (final URISyntaxException e) {
            return "";
        }
    }

    private static String getParameter(final String url, final String key) {
        try {
            final String query = new URI(url).getRawQuery();
            if (query != null) {
                for (final String part : query.split("&")) {
                    if (part.startsWith(key + "=")) {
                        return part.substring(key.length() + 1);
                    }
                }
            }
        } catch (final URISyntaxException ignored) {
        }
        return null;
    }

    private static String removeParameter(final String url, final String key) {
        final int mark = url.indexOf('?');
        if (mark < 0) {
            return url;
        }
        final StringBuilder query = new StringBuilder();
        for (final String part : url.substring(mark + 1).split("&")) {
            if (!part.startsWith(key + "=") && !part.isEmpty()) {
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(part);
            }
        }
        return url.substring(0, mark) + (query.length() == 0 ? "" : "?" + query);
    }

    private static String setParameter(final String url, final String key, final String value) {
        final String base = removeParameter(url, key);
        return base + (base.contains("?") ? "&" : "?") + key + "=" + value;
    }
}
