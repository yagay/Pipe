package org.schabi.newpipe.extractor.services.missav;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Available filter/sort choices are discovered from the selected MissAV category page. */
public final class MissAvCategoryOptions {
    public static final class Option {
        public final String label;
        public final String url;
        public final boolean selected;

        public Option(final String label, final String url, final boolean selected) {
            this.label = label;
            this.url = url;
            this.selected = selected;
        }
    }

    public final List<Option> filters;
    public final List<Option> sorts;

    private MissAvCategoryOptions(final List<Option> filters, final List<Option> sorts) {
        this.filters = filters;
        this.sorts = sorts;
    }

    public static MissAvCategoryOptions load(final String categoryUrl)
            throws IOException, ExtractionException {
        final Document doc = MissAvParsingHelper.fetchDocument(categoryUrl);
        final URI current;
        try {
            current = new URI(categoryUrl);
        } catch (final URISyntaxException e) {
            throw new IOException("Invalid MissAV category URL", e);
        }
        final String categoryPath = stripDomainPrefix(current.getPath());
        final Map<String, Option> filterOptions = new LinkedHashMap<>();
        final Map<String, Option> sortOptions = new LinkedHashMap<>();

        for (final Element link : doc.select("a[href]")) {
            final String href = link.absUrl("href");
            final String label = link.text().trim();
            if (href.isEmpty() || label.isEmpty() || label.length() > 60) {
                continue;
            }
            try {
                final URI option = new URI(href);
                if (!isTrustedHost(option.getHost())
                        || !stripDomainPrefix(option.getPath()).equals(categoryPath)) {
                    continue;
                }
                final String query = option.getRawQuery();
                final String parameter;
                if (containsParam(query, "filters")) {
                    parameter = "filters";
                } else if (containsParam(query, "sort")) {
                    parameter = "sort";
                } else {
                    continue;
                }
                final String value = getParam(query, parameter);
                if (value.isEmpty()) {
                    continue;
                }
                // Preserve the other selector when choosing a filter or sort.
                final String mergedUrl = setParameter(categoryUrl, parameter, value);
                final boolean selected = value.equals(getParam(current.getRawQuery(), parameter));
                if ("filters".equals(parameter)) {
                    filterOptions.putIfAbsent(value, new Option(label, mergedUrl, selected));
                } else {
                    sortOptions.putIfAbsent(value, new Option(label, mergedUrl, selected));
                }
            } catch (final URISyntaxException ignored) {
                // Skip malformed or external links, not the entire category.
            }
        }
        if (!filterOptions.isEmpty()) {
            filterOptions.put("", new Option("All", removeParameter(categoryUrl, "filters"),
                    getParam(current.getRawQuery(), "filters").isEmpty()));
        }
        if (!sortOptions.isEmpty()) {
            sortOptions.put("", new Option("Default", removeParameter(categoryUrl, "sort"),
                    getParam(current.getRawQuery(), "sort").isEmpty()));
        }
        return new MissAvCategoryOptions(new ArrayList<>(filterOptions.values()),
                new ArrayList<>(sortOptions.values()));
    }

    public static boolean isCategoryOptionUrl(final String url) {
        try {
            final URI uri = new URI(url);
            return isTrustedHost(uri.getHost())
                    && (containsParam(uri.getRawQuery(), "sort")
                    || containsParam(uri.getRawQuery(), "filters"));
        } catch (final URISyntaxException ignored) {
            return false;
        }
    }

    private static boolean isTrustedHost(final String host) {
        return host != null && (host.equals("missav.ws") || host.equals("www.missav.ws"));
    }

    private static String stripDomainPrefix(final String path) {
        return path == null ? "" : path.replaceFirst("^/dm[0-9]+(?=/)", "");
    }

    private static boolean containsParam(final String query, final String key) {
        if (query == null) {
            return false;
        }
        for (final String part : query.split("&")) {
            if (part.startsWith(key + "=")) {
                return true;
            }
        }
        return false;
    }

    private static String getParam(final String query, final String key) {
        if (query != null) {
            for (final String part : query.split("&")) {
                if (part.startsWith(key + "=")) {
                    return part.substring(key.length() + 1);
                }
            }
        }
        return "";
    }

    private static String removeParameter(final String url, final String key) {
        final int question = url.indexOf('?');
        if (question < 0) {
            return url;
        }
        final StringBuilder query = new StringBuilder();
        for (final String item : url.substring(question + 1).split("&")) {
            if (!item.isEmpty() && !item.startsWith(key + "=")) {
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(item);
            }
        }
        return url.substring(0, question) + (query.length() == 0 ? "" : "?" + query);
    }

    private static String setParameter(final String url, final String key, final String value) {
        final String base = removeParameter(url, key);
        return base + (base.contains("?") ? "&" : "?") + key + "=" + value;
    }
}
