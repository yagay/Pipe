package org.schabi.newpipe.extractor.kiosk;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.PageNavigation;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Conservative fallback for HTML sites with native StreamLinkHandler support.
 * API-driven sites should continue using their dedicated kiosk extractors.
 */
public final class SiteHtmlKioskExtractor extends KioskExtractor<StreamInfoItem> {
    private Document document;

    public SiteHtmlKioskExtractor(final StreamingService service,
                                 final ListLinkHandler handler) {
        super(service, handler, "category");
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        document = SiteBrowseCatalog.fetchSitePage(getService(), getUrl());
    }

    @Nonnull
    @Override
    public String getName() {
        return "Categories";
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        assertPageFetched();
        return collect(document);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return collect(SiteBrowseCatalog.fetchSitePage(getService(), page.getUrl()));
    }

    private InfoItemsPage<StreamInfoItem> collect(final Document page)
            throws IOException, ExtractionException {
        final StreamInfoItemsCollector items = new StreamInfoItemsCollector(getServiceId());
        final Set<String> seen = new HashSet<>();
        int accepted = 0;
        for (final Element link : page.select("a[href]")) {
            final String target = link.absUrl("href");
            try {
                if (target.isEmpty() || !getService().getStreamLHFactory().onAcceptUrl(target)) {
                    continue;
                }
            } catch (final ParsingException ignored) {
                continue;
            }
            // Supjav cards often wrap the thumbnail and title in separate
            // anchors inside one post. Resolve the common post container first,
            // then fall back to the existing generic behaviour for other sites.
            Element card = link;
            if ("Supjav".equals(getService().getServiceInfo().getName())) {
                final Element post = link.closest(
                        "article,.post,.video-item,.video,.item,.thumb-item,li");
                if (post != null) {
                    card = post;
                } else if (link.selectFirst("img") == null && link.parent() != null) {
                    card = link.parent();
                }
            } else if (link.selectFirst("img") == null && link.parent() != null) {
                card = link.parent();
            }
            final Element img = card.selectFirst(
                    "img[data-src],img[data-original],img[data-lazy-src],img[src]");
            if (img == null) {
                continue;
            }
            final String id;
            try {
                id = getService().getStreamLHFactory().getId(target);
            } catch (final ParsingException ignored) {
                continue;
            }
            if (id == null || id.isEmpty() || !seen.add(id)) {
                continue;
            }
            items.commit(new VideoCard(card, target, getService().getServiceInfo().getName()));
            if (++accepted >= 60) {
                break;
            }
        }

        Page next = PageNavigation.nextPage(page);
        // A few Supjav directory themes use a numbered pagination link
        // without rel=next. Prefer the actual next link, never guess a URL.
        if (next == null && "Supjav".equals(getService().getServiceInfo().getName())) {
            final Element link = page.selectFirst(
                    "a.next[href],a.nextpostslink[href],"
                    + ".pagination a.next[href],a[rel=next][href]");
            if (link != null && SiteBrowseCatalog.isWebsiteBrowseUrl(
                    getService(), link.absUrl("href"))) {
                next = new Page(link.absUrl("href"));
            }
        }
        if (next != null && (!SiteBrowseCatalog.isWebsiteBrowseUrl(getService(), next.getUrl())
                || next.getUrl().equalsIgnoreCase(page.baseUri()))) {
            next = null;
        }
        if (next != null) {
            final String target = BrowseUrlState.carryOptionsToNextPage(
                    page.location(), next.getUrl());
            if (SiteBrowseCatalog.isWebsiteBrowseUrl(getService(), target)) {
                next = new Page(target);
            } else {
                next = null;
            }
        }
        return new InfoItemsPage<>(items, next);
    }

    private static final class VideoCard implements StreamInfoItemExtractor {
        private final Element card;
        private final String url;
        private final String source;

        VideoCard(final Element card, final String url, final String source) {
            this.card = card;
            this.url = url;
            this.source = source;
        }

        @Override public String getName() {
            final Element img = card.selectFirst("img[alt]");
            if (img != null && !img.attr("alt").trim().isEmpty()) {
                return img.attr("alt").trim();
            }
            final Element title = card.selectFirst("[title],h2,h3,.title");
            if (title != null) {
                final String value = title.hasAttr("title")
                        ? title.attr("title").trim() : title.text().trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
            final String text = card.text().trim();
            return text.isEmpty() ? url : text;
        }
        @Override public String getUrl() { return url; }
        @Override public String getThumbnailUrl() {
            final Element img = card.selectFirst(
                    "img[data-src],img[data-original],img[data-lazy-src],img[src]");
            if (img == null) {
                return "";
            }
            for (final String attr : new String[]{"data-original",
                    "data-src", "data-lazy-src", "src"}) {
                final String value = img.absUrl(attr);
                if (!value.isEmpty()) {
                    return value;
                }
            }
            return "";
        }
        @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
        @Override public long getDuration() { return -1; }
        @Override public long getViewCount() { return -1; }
        @Override public String getUploaderName() { return source; }
        @Nullable @Override public String getTextualUploadDate() { return null; }
        @Nullable @Override public DateWrapper getUploadDate() { return null; }
    }
}
