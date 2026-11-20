package org.schabi.newpipe.extractor.services.internetarchive;

import org.schabi.newpipe.extractor.StreamingService;
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

import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.VIDEO;

public final class InternetArchiveService extends StreamingService {
    public static final String LATEST_KIOSK_ID = "latest";

    public InternetArchiveService(final int id) {
        super(id, "Internet Archive", Collections.singletonList(VIDEO));
    }

    @Override
    public String getBaseUrl() {
        return InternetArchiveParsingHelper.BASE_URL;
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return new InternetArchiveStreamLinkHandlerFactory();
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return new InternetArchiveSearchQueryHandlerFactory();
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler queryHandler) {
        return new InternetArchiveSearchExtractor(this, queryHandler);
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler linkHandler) {
        return new InternetArchiveStreamExtractor(this, linkHandler);
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {
        final KioskList kioskList = new KioskList(this);
        final InternetArchiveKioskLinkHandlerFactory linkHandlerFactory =
                new InternetArchiveKioskLinkHandlerFactory();
        try {
            kioskList.addKioskEntry(
                    (service, url, kioskId) -> new InternetArchiveKiosk(this,
                            linkHandlerFactory.fromUrl(url), kioskId),
                    linkHandlerFactory, LATEST_KIOSK_ID);
            kioskList.setDefaultKiosk(LATEST_KIOSK_ID);
        } catch (final Exception e) {
            throw new ExtractionException("Could not initialize Internet Archive kiosk", e);
        }
        return kioskList;
    }

    @Override public ListLinkHandlerFactory getChannelLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getChannelTabLHFactory() { return null; }
    @Override public ListLinkHandlerFactory getPlaylistLHFactory() { return null; }
    @Override public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) { return null; }
    @Override public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler) { return null; }
    @Override public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler) { return null; }
    @Override public SuggestionExtractor getSuggestionExtractor() { return null; }
    @Override public SubscriptionExtractor getSubscriptionExtractor() { return null; }
    @Override public ListLinkHandlerFactory getCommentsLHFactory() { return null; }
    @Override public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) { return null; }
}
