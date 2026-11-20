package org.schabi.newpipe.extractor.services.internetarchive;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.search.SearchExtractor;

import java.io.IOException;

import javax.annotation.Nonnull;

public final class InternetArchiveSearchExtractor extends SearchExtractor {
    public InternetArchiveSearchExtractor(final StreamingService service,
                                          final SearchQueryHandler queryHandler) {
        super(service, queryHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) { }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPageInternal() throws IOException, ExtractionException {
        return fetchPage(getUrl());
    }

    @Override
    public InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        if (page == null || page.getUrl() == null || page.getUrl().isEmpty()) {
            return InfoItemsPage.emptyPage();
        }
        return fetchPage(page.getUrl());
    }

    private InfoItemsPage<InfoItem> fetchPage(final String url)
            throws IOException, ExtractionException {
        final JsonObject root = InternetArchiveParsingHelper.getJson(getDownloader(), url);
        final JsonObject response = root.getObject("response");
        if (response == null) {
            throw new ExtractionException("Internet Archive search response has no results object");
        }
        final JsonArray docs = response.getArray("docs");
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        if (docs != null) {
            for (final Object item : docs) {
                if (item instanceof JsonObject) {
                    collector.commit(new InternetArchiveStreamInfoItemExtractor((JsonObject) item));
                }
            }
        }

        final long total = response.getLong("numFound", -1);
        final int pageNumber = response.getInt("start", 0) / InternetArchiveParsingHelper.RESULTS_PER_PAGE + 1;
        final int nextPage = pageNumber + 1;
        final Page next = total >= 0 && pageNumber * InternetArchiveParsingHelper.RESULTS_PER_PAGE < total
                ? new Page(InternetArchiveParsingHelper.searchUrl(getSearchString(), nextPage),
                        String.valueOf(nextPage))
                : null;
        return new InfoItemsPage<>(collector, next);
    }
}
