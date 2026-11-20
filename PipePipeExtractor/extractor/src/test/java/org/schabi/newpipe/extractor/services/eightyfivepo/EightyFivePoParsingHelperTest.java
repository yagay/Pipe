package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.jsoup.Jsoup;
import org.junit.Test;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class EightyFivePoParsingHelperTest {
    @Test
    public void extractsCardsFromCurrentVideoRoute() throws Exception {
        final String html = "<div class='item'>"
                + "<a href='/ja/video/98765/safe-title/' title='Example title'>"
                + "<img data-original='//thumbs.example/image.jpg'>"
                + "<span class='duration'>08:14</span></a></div>";

        final List<EightyFivePoSearchResult> results =
                EightyFivePoParsingHelper.extractVideoCards(
                        Jsoup.parse(html, EightyFivePoParsingHelper.BASE_URL + "/ja/search/test/"), 10);

        assertEquals(1, results.size());
        assertEquals("98765", results.get(0).id);
        assertEquals("https://www.85po.com/ja/video/98765/safe-title/", results.get(0).url);
        assertEquals("Example title", results.get(0).title);
        assertEquals("https://thumbs.example/image.jpg", results.get(0).thumbnail);
        assertEquals(494, results.get(0).duration);
    }

    @Test
    public void prefersVisibleTitleWhenThumbnailAltIsGenericImage() {
        final StringBuilder html = new StringBuilder();
        for (int i = 1; i <= 6; i++) {
            html.append("<a class='drop-card' href='/ja/video/").append(i)
                    .append("/video-title-").append(i).append("/'>")
                    .append("<div class='card-image'><img alt='image' src='/thumb.jpg'></div>")
                    .append("<div class='duration'>00:15</div>")
                    .append("<div class='card-title'>Actual title ").append(i)
                    .append("</div></a>");
        }

        final List<EightyFivePoSearchResult> results =
                EightyFivePoParsingHelper.extractVideoCards(
                        Jsoup.parse(html.toString(), EightyFivePoParsingHelper.BASE_URL + "/ja/"),
                        10);

        assertEquals(6, results.size());
        assertEquals("Actual title 1", results.get(0).title);
        assertEquals("Actual title 6", results.get(5).title);
    }

    @Test
    public void searchPageIgnoresSixFeaturedHeaderVideos() {
        final StringBuilder html = new StringBuilder("<div class='header-col mainly'>");
        for (int i = 1; i <= 6; i++) {
            html.append("<a class='drop-card' href='/ja/video/").append(i)
                    .append("/featured-").append(i).append("/'>")
                    .append("<img alt='image' src='/featured.jpg'>")
                    .append("<div class='card-title'>Featured ").append(i)
                    .append("</div></a>");
        }
        html.append("</div><div id='list_videos_videos_list_search_result_items'>")
                .append("<div class='item'><a href='/ja/video/98765/query-match/' ")
                .append("title='Actual query result'><img alt='image' src='/result.jpg'>")
                .append("<strong class='title'>Actual query result</strong></a></div></div>");

        final List<EightyFivePoSearchResult> results =
                EightyFivePoParsingHelper.extractVideoCards(
                        Jsoup.parse(html.toString(),
                                EightyFivePoParsingHelper.BASE_URL + "/ja/search/query/"), 20);

        assertEquals(1, results.size());
        assertEquals("98765", results.get(0).id);
        assertEquals("Actual query result", results.get(0).title);
    }

    @Test
    public void streamLinkHandlerPreservesTheSluggedPageUrl() throws Exception {
        final String url = "https://www.85po.com/ja/video/98765/safe-title/";
        final LinkHandler handler = EightyFivePoStreamLinkHandlerFactory.getInstance().fromUrl(url);

        assertEquals("98765", handler.getId());
        assertEquals(url, handler.getUrl());
        assertEquals("https://www.85po.com/ja/video/98765/",
                EightyFivePoStreamLinkHandlerFactory.getInstance().fromId("98765").getUrl());
    }

    @Test
    public void searchQueryUsesPathSegmentEncoding() {
        assertEquals("a%20b%2Bc", EightyFivePoParsingHelper.encodeQuery("a b+c"));
    }

    @Test
    public void legacyVideoRoutesRemainAccepted() throws Exception {
        assertTrue(EightyFivePoStreamLinkHandlerFactory.getInstance()
                .acceptUrl("https://www.85po.com/ja/v/98765/"));
    }
}
