package org.schabi.newpipe.extractor.services.missav;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.utils.PageNavigation;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nonnull;

public final class MissAvKioskExtractor extends KioskExtractor<StreamInfoItem> {
    private Document document;

    public MissAvKioskExtractor(final StreamingService service,
                                final ListLinkHandler linkHandler,
                                final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = MissAvParsingHelper.fetchDocument(getUrl());
    }

    @Nonnull
    @Override
    public String getName() {
        return MissAvKioskLinkHandlerFactory.getCategoryName(getId());
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        assertPageFetched();
        final StreamInfoItemsCollector collector =
                new StreamInfoItemsCollector(getServiceId());
        final Set<String> seen = new LinkedHashSet<>();
        for (final Element link : document.select("a[href]")) {
            final String href = link.absUrl("href");
            if (isVideoCard(href, link) && seen.add(href)) {
                collector.commit(new MissAvKioskInfoItemExtractor(link));
            }
        }
        return new InfoItemsPage<>(collector, PageNavigation.nextPage(document));
    }

    private static boolean isVideoCard(final String href, final Element link) {
        if (link.selectFirst("img") == null || !MissAvStreamLinkHandlerFactory
                .getInstance().onAcceptUrl(href)) {
            return false;
        }
        try {
            return MissAvParsingHelper.isVideoId(MissAvParsingHelper.extractId(href));
        } catch (final ParsingException e) {
            return false;
        }
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        final Document nextDocument = MissAvParsingHelper.fetchDocument(page.getUrl());
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final Set<String> seen = new LinkedHashSet<>();
        for (final Element link : nextDocument.select("a[href]")) {
            final String href = link.absUrl("href");
            if (isVideoCard(href, link) && seen.add(href)) {
                collector.commit(new MissAvKioskInfoItemExtractor(link));
            }
        }
        return new InfoItemsPage<>(collector, PageNavigation.nextPage(nextDocument));
    }
}
