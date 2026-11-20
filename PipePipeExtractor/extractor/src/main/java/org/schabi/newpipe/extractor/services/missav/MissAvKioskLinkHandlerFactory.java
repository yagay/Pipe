package org.schabi.newpipe.extractor.services.missav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.List;

public final class MissAvKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final MissAvKioskLinkHandlerFactory INSTANCE =
            new MissAvKioskLinkHandlerFactory();

    public static MissAvKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private MissAvKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url == null || url.isEmpty()) {
            return "latest";
        }
        final String path = url.split("[?#]", 2)[0].replaceFirst("/+$", "");
        final String slug = path.substring(path.lastIndexOf('/') + 1);
        if ("new".equals(slug) || "en".equals(slug) || "ja".equals(slug)) {
            return "latest";
        }
        return slug.isEmpty() ? "latest" : slug;
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        switch (id) {
            case "popular":
                return MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE
                        + "/popular";
            case "recommended":
                return MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE;
            case "latest":
            default:
                return MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE
                        + "/new";
        }
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && url.contains("missav.ws");
    }
}
