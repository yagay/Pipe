package org.schabi.newpipe.extractor.services.kissjav;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.util.Collections;

public final class KissJavService extends StreamingService {
    public KissJavService(final int id) {
        super(id, "KissJAV", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }

    @Override
    public String getBaseUrl() {
        return KissJavParsingHelper.BASE_URL;
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return KissJavStreamLinkHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return null;
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return KissJavSearchQueryHandlerFactory.getInstance();
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return null;
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler queryHandler) {
        return new KissJavSearchExtractor(this, queryHandler);
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return new KissJavSuggestionExtractor(this);
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {
        final KioskList list = new KioskList(this) {
            @Override
            public KioskExtractor getExtractorByUrl(final String url,
                    final Page nextPage, final Localization localization)
                    throws ExtractionException, IOException {
                if (isNativeCategoryUrl(url)) {
                    final ListLinkHandler handler = new ListLinkHandler(url, url, "category",
                            Collections.emptyList(), null);
                    return new KissJavKioskExtractor(KissJavService.this, handler, "category");
                }
                return super.getExtractorByUrl(url, nextPage, localization);
            }
        };
        try {
            list.addKioskEntry((service, url, kioskId) -> new KissJavKioskExtractor(
                            service,
                            KissJavKioskLinkHandlerFactory.getInstance().fromId(kioskId),
                            kioskId),
                    KissJavKioskLinkHandlerFactory.getInstance(),
                    "latest");
            list.addKioskEntry((service, url, kioskId) -> new KissJavKioskExtractor(
                            service,
                            KissJavKioskLinkHandlerFactory.getInstance().fromId(kioskId),
                            kioskId),
                    KissJavKioskLinkHandlerFactory.getInstance(),
                    "popular");
            list.addKioskEntry((service, url, kioskId) -> new KissJavKioskExtractor(
                            service,
                            KissJavKioskLinkHandlerFactory.getInstance().fromId(kioskId),
                            kioskId),
                    KissJavKioskLinkHandlerFactory.getInstance(),
                    "recommended");
            list.setDefaultKiosk("latest");
        } catch (final Exception e) {
            throw new ExtractionException("Could not initialize KissJAV kiosks", e);
        }
        return list;
    }

    private static boolean isNativeCategoryUrl(final String url) {
        try {
            final URI uri = new URI(url);
            final URI site = new URI(KissJavParsingHelper.BASE_URL);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && site.getHost().equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().matches("/categories/[^/]+/?");
        } catch (final URISyntaxException ignored) {
            return false;
        }
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        throw new ExtractionException("KissJAV channels are not implemented");
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        throw new ExtractionException("KissJAV channel tabs are not implemented");
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler linkHandler)
            throws ExtractionException {
        throw new ExtractionException("KissJAV playlists are not implemented");
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler) {
        return new KissJavStreamExtractor(this, linkHandler);
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler linkHandler) {
        return null;
    }

    @Override
    public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler linkHandler) {
        return null;
    }
}
