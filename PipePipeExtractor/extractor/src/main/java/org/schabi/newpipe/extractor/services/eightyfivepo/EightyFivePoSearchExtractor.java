package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.utils.PageNavigation;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

public final class EightyFivePoSearchExtractor extends SearchExtractor {
    private static final int PAGE_SIZE = 40;

    public EightyFivePoSearchExtractor(final StreamingService service,
                                       final SearchQueryHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
    }

    @Override
    protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        final org.jsoup.nodes.Document document = EightyFivePoParsingHelper.fetchDocument(getUrl());
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final EightyFivePoSearchResult result
                : EightyFivePoParsingHelper.extractVideoCards(document, PAGE_SIZE)) {
            collector.commit(new EightyFivePoInfoItemExtractor(result));
        }
        return new ListExtractor.InfoItemsPage<>(collector, PageNavigation.nextPage(document));
    }

    @Override
    protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        final org.jsoup.nodes.Document document = EightyFivePoParsingHelper.fetchDocument(
                page.getUrl(), getUrl());
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final EightyFivePoSearchResult result
                : EightyFivePoParsingHelper.extractVideoCards(document, PAGE_SIZE)) {
            collector.commit(new EightyFivePoInfoItemExtractor(result));
        }
        return new ListExtractor.InfoItemsPage<>(collector, PageNavigation.nextPage(document));
    }
}
