package org.schabi.newpipe.extractor.services.internetarchive;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class InternetArchiveStreamLinkHandlerFactory extends LinkHandlerFactory {
    @Override
    public String getId(final String url) throws ParsingException {
        final String path = pathOf(url);
        final String prefix = "/details/";
        if (!path.startsWith(prefix)) {
            throw new ParsingException("Not an Internet Archive details URL");
        }
        final String identifier = path.substring(prefix.length()).split("/", 2)[0];
        if (identifier.isEmpty()) {
            throw new ParsingException("Internet Archive identifier is empty");
        }
        return identifier;
    }

    @Override
    public String getUrl(final String id) {
        return InternetArchiveParsingHelper.detailsUrl(id);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        try {
            final URI uri = URI.create(url);
            final String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("archive.org")
                    || host.toLowerCase().endsWith(".archive.org"))
                    && uri.getPath() != null && uri.getPath().startsWith("/details/");
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    private static String pathOf(final String url) throws ParsingException {
        try {
            return URI.create(url).getPath();
        } catch (final IllegalArgumentException e) {
            throw new ParsingException("Invalid Internet Archive URL", e);
        }
    }
}

final class InternetArchiveSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    @Override
    public String getUrl(final String query, final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilters) {
        return InternetArchiveParsingHelper.searchUrl(query, 1);
    }

    @Override
    public String getSearchString(final String url) {
        if (url == null) {
            return "";
        }
        for (final String parameter : url.split("[?&]")) {
            if (!parameter.startsWith("q=")) {
                continue;
            }
            final String query = URLDecoder.decode(parameter.substring(2), StandardCharsets.UTF_8);
            final String prefix = "mediatype:movies AND \"";
            if (query.startsWith(prefix) && query.endsWith("\"")) {
                return query.substring(prefix.length(), query.length() - 1)
                        .replace("\\\"", "\"").replace("\\\\", "\\");
            }
            return query.equals("mediatype:movies") ? "" : query;
        }
        return "";
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        try {
            final URI uri = URI.create(url);
            final String host = uri.getHost();
            return host != null && (host.equalsIgnoreCase("archive.org")
                    || host.toLowerCase().endsWith(".archive.org"))
                    && "/advancedsearch.php".equals(uri.getPath());
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }
}

final class InternetArchiveKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    @Override
    public String getId(final String url) {
        return InternetArchiveService.LATEST_KIOSK_ID;
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilters) {
        return InternetArchiveParsingHelper.searchUrl("", 1);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && url.startsWith(InternetArchiveParsingHelper.BASE_URL
                + "/advancedsearch.php");
    }
}
