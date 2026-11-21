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
        switch (getId()) {
            case "popular":
                return "Popular";
            case "recommended":
                return "Recommended";
            case "latest":
            default:
                return "Latest";
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        assertPageFetched();
        return collectPage(document);

    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        final Document nextDocument = MissAvParsingHelper.fetchDocument(page.getUrl());
        return collectPage(nextDocument);

    }
    private InfoItemsPage<StreamInfoItem> collectPage(final Document page)
            throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector =
                new StreamInfoItemsCollector(getServiceId());
        final Set<String> seenIds = new LinkedHashSet<>();
        for (final Element link : page.select("a[href]:has(img)")) {
            final String href = link.absUrl("href");
            final String id;
            try {
                if (!MissAvStreamLinkHandlerFactory.getInstance().onAcceptUrl(href)) {
                    continue;
                }
                id = MissAvParsingHelper.extractId(href).toLowerCase(java.util.Locale.ROOT);
            } catch (final ParsingException ignored) {
                continue;
            }
            // Category/directory links are often image-backed too; accept only video IDs.
            if (!MissAvParsingHelper.isVideoId(id) || !seenIds.add(id)) {
                continue;
            }
            collector.commit(new MissAvKioskInfoItemExtractor(link));
        }
        Page next = PageNavigation.nextPage(page);
        if (next != null) {
            final String nextUrl = next.getUrl();
            if (nextUrl == null || !MissAvBrowseCatalog.isBrowsePage(nextUrl)
                    || nextUrl.equalsIgnoreCase(page.baseUri())) {
                next = null;
            }
        }
        return new InfoItemsPage<>(collector, next);
    }

}
