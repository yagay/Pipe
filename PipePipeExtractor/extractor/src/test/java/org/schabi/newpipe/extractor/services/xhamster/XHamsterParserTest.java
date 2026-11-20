package org.schabi.newpipe.extractor.services.xhamster;

import org.jsoup.Jsoup;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class XHamsterParserTest {
    @Test
    public void latestUrlUsesTheCanonicalNewestFeed() {
        assertEquals("https://jp.xhamster.com/newest", XHamsterParser.latestUrl());
    }

    @Test
    public void cardsAcceptTheCurrentThumbnailMarkup() {
        final List<XHamsterItem> items = XHamsterParser.cards(Jsoup.parse(
                "<div class='video-thumb' data-video-id='123'>"
                        + "<a data-role='thumb-link' href='https://jp.xhamster.com/videos/example-123' "
                        + "aria-label='Example title'><img data-src='https://cdn.example/thumb.jpg'/></a>"
                        + "<span data-role='video-duration'>01:02</span></div>"), 10);

        assertEquals(1, items.size());
        assertEquals("123", items.get(0).id);
        assertEquals("Example title", items.get(0).title);
        assertFalse(items.get(0).thumb.isEmpty());
    }

    @Test
    public void relatedCardsReadCurrentInitialsAndPlayerData() {
        final String html = "<script id='initials-script'>window.initials={"
                + "\"relatedVideosComponent\":{\"videoTabInitialData\":{"
                + "\"videoListProps\":{\"videoThumbProps\":[{"
                + "\"id\":\"101\",\"pageURL\":\"https://jp.xhamster.com/videos/first-101\","
                + "\"title\":\"First title\",\"thumbURL\":\"https://cdn.example/first.jpg\","
                + "\"duration\":62}]}}}};</script>"
                + "<script>window.xplayerPluginSettings = {\"relatedVideos\":[{"
                + "\"idHash\":\"second-hash\",\"url\":\"https://jp.xhamster.com/videos/second-202\","
                + "\"title\":\"Second title\",\"thumbUrl\":\"https://cdn.example/second.jpg\","
                + "\"duration\":\"01:03\"}]};</script>";

        final List<XHamsterItem> items = XHamsterParser.relatedCards(Jsoup.parse(html), 10);

        assertEquals(2, items.size());
        assertEquals("first-101", items.get(0).id);
        assertEquals("First title", items.get(0).title);
        assertEquals(62, items.get(0).duration);
        assertEquals("second-202", items.get(1).id);
        assertEquals("Second title", items.get(1).title);
        assertEquals(63, items.get(1).duration);
    }
}
