package org.schabi.newpipe.extractor.services.internetarchive;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.io.IOException;

import javax.annotation.Nonnull;

public final class InternetArchiveKiosk extends KioskExtractor<StreamInfoItem> {
    private JsonObject response;

    public InternetArchiveKiosk(final StreamingService service, final ListLinkHandler linkHandler,
                                final String kioskId) {
        super(service, linkHandler, kioskId);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        final JsonObject root = InternetArchiveParsingHelper.getJson(downloader, getUrl());
        response = root.getObject("response");
        if (response == null) {
            throw new ExtractionException("Internet Archive latest response has no results object");
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        return makePage(response, 1);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || page.getUrl() == null || page.getUrl().isEmpty()) {
            return InfoItemsPage.emptyPage();
        }
        final JsonObject root = InternetArchiveParsingHelper.getJson(getDownloader(), page.getUrl());
        return makePage(root.getObject("response"), pageNumber(page));
    }

    @Nonnull
    @Override
    public String getName() {
        return "Latest";
    }

    private InfoItemsPage<StreamInfoItem> makePage(final JsonObject result, final int pageNumber)
            throws ExtractionException {
        if (result == null) {
            throw new ExtractionException("Internet Archive response has no results object");
        }
        final JsonArray docs = result.getArray("docs");
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        if (docs != null) {
            for (final Object item : docs) {
                if (item instanceof JsonObject) {
                    collector.commit(new InternetArchiveStreamInfoItemExtractor((JsonObject) item));
                }
            }
        }
        final long total = result.getLong("numFound", -1);
        final int nextPage = pageNumber + 1;
        final Page next = total >= 0 && pageNumber * InternetArchiveParsingHelper.RESULTS_PER_PAGE < total
                ? new Page(InternetArchiveParsingHelper.searchUrl("", nextPage), String.valueOf(nextPage))
                : null;
        return new InfoItemsPage<>(collector, next);
    }

    private int pageNumber(final Page page) throws ParsingException {
        try {
            return Math.max(1, Integer.parseInt(page.getId()));
        } catch (final Exception e) {
            throw new ParsingException("Invalid Internet Archive page", e);
        }
    }
}
