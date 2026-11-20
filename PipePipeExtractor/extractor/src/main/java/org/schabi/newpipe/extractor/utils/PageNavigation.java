package org.schabi.newpipe.extractor.utils;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Page;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helpers for extracting the next page from ordinary HTML pagination controls. */
public final class PageNavigation {
    private static final Pattern QUERY_PAGE = Pattern.compile("[?&](?:page|p)=(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATH_PAGE = Pattern.compile("/page/(\\d+)(?:/|\\.html|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENT_PAGE_NUMBER = Pattern.compile("^(\\d+)$");
    private static final String NEXT_PAGE_SELECTOR = "a[rel=next], "
            + ".pagination .next a[href], .pagination a.next[href], "
            + ".pagination__next[href], .pagination-next[href], "
            + "li.next a[href], a.next[href], .next-page[href], .next-page a[href], "
            + ".pager-next[href], .pager-next a[href], "
            + "a[aria-label*=Next][href], a[title*=Next][href]";

    private PageNavigation() {
    }

    /**
     * Returns the absolute URL of the page following {@code document}, when the page exposes a
     * conventional next-page link.
     */
    public static Page nextPage(final Document document) {
        if (document == null) {
            return null;
        }

        Element link = document.selectFirst(NEXT_PAGE_SELECTOR);
        if (link == null) {
            // Some sites use translated labels such as "pagination.next" without putting
            // the link inside a conventional .pagination/nav container.
            for (final Element candidate : document.select("a[href]")) {
                final String label = (candidate.text() + " "
                        + candidate.attr("aria-label") + " " + candidate.attr("title"))
                        .trim().toLowerCase(Locale.ROOT);
                if (label.matches(".*\\b(next|older)\\b.*")
                        || label.matches("^(?:›|»|→|❯|❯❯)$")) {
                    link = candidate;
                    break;
                }
            }
        }
        if (link != null) {
            final Page next = pageForLink(document, link);
            if (next != null) {
                return next;
            }
        }

        // A number of plain PHP pagination templates (including Avgle and OHentai) expose
        // only numbered links. Select the nearest later page when no explicit "next" link
        // was found.
        final int currentPage = currentPage(document);
        Element nearestNext = null;
        int nearestNumber = Integer.MAX_VALUE;
        for (final Element candidate : document.select("a[href]")) {
            final String url = candidate.absUrl("href");
            if (!isHttpUrl(url) || !sameHost(url, document.location())) {
                continue;
            }
            final int number = pageNumber(url);
            if (number > currentPage && number < nearestNumber) {
                nearestNext = candidate;
                nearestNumber = number;
            }
        }
        return nearestNext == null ? null : pageForLink(document, nearestNext);
    }

    private static Page pageForLink(final Document document, final Element link) {
        final String url = link.absUrl("href");
        if (!isHttpUrl(url) || url.equals(document.location())) {
            return null;
        }
        return new Page(url);
    }

    private static int currentPage(final Document document) {
        final int fromUrl = pageNumber(document.location());
        if (fromUrl > 0) {
            return fromUrl;
        }
        for (final Element current : document.select(
                "[aria-current=page], .pagination .active, .pagination .current, "
                        + ".pager .active, .pager .current, .page-item.active, "
                        + "[class*=current-page], [class*=active]")) {
            final Matcher number = CURRENT_PAGE_NUMBER.matcher(current.text().trim());
            if (number.matches()) {
                try {
                    return Integer.parseInt(number.group(1));
                } catch (final NumberFormatException ignored) {
                    // Continue with the next active marker.
                }
            }
        }
        return 1;
    }

    private static int pageNumber(final String url) {
        if (url == null) {
            return -1;
        }
        Matcher matcher = QUERY_PAGE.matcher(url);
        if (!matcher.find()) {
            matcher = PATH_PAGE.matcher(url);
            if (!matcher.find()) {
                return -1;
            }
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (final NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isHttpUrl(final String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private static boolean sameHost(final String first, final String second) {
        try {
            final String firstHost = java.net.URI.create(first).getHost();
            final String secondHost = java.net.URI.create(second).getHost();
            return firstHost != null && secondHost != null
                    && firstHost.equalsIgnoreCase(secondHost);
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }
}
