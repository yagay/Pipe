package org.schabi.newpipe.extractor.services.ohentai;

import org.jsoup.Jsoup;
import org.junit.Test;
import org.schabi.newpipe.extractor.downloader.Response;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OhentaiParserTest {
    @Test
    public void searchCardsAcceptDetailLinksWithVidParameter() {
        final String html = "<div class='result'><a href='/detail.php?vid=123' title='Example title'>"
                + "<img src='/thumbs/example.jpg' alt='Example title'></a></div>";
        final List<OhentaiItem> items = OhentaiParser.cards(
                Jsoup.parse(html, OhentaiParser.BASE + "/search.php?k=example"), 10);

        assertEquals(1, items.size());
        assertEquals("123", items.get(0).id);
        assertEquals("Example title", items.get(0).title);
        assertEquals("https://ohentai.org/thumbs/example.jpg", items.get(0).thumbnail);
    }

    @Test
    public void searchUrlUsesTheObservedGetParameter() {
        assertEquals("https://ohentai.org/search.php?k=a+b%2Bc", OhentaiParser.searchUrl("a b+c"));
    }

    @Test
    public void jwPlayerMediaPathSpacesAreEncodedAndSignedQueryIsPreserved() {
        final String html = "<script>player.setup({sources:[{\"file\":"
                + "\"https://videos.ohentai.org/videos/storage.bunnycdn.com/"
                + "Example Title.mp4?token=sample&expires=123\"}]});</script>";

        assertEquals("https://videos.ohentai.org/videos/storage.bunnycdn.com/"
                        + "Example%20Title.mp4?token=sample&expires=123",
                OhentaiParser.source(Jsoup.parse(html)));
    }

    @Test
    public void cloudflareBlockIsRecognizedAsUserChallenge() {
        final Response response = new Response(403, "Forbidden",
                Collections.singletonMap("cf-mitigated", Collections.singletonList("challenge")),
                "challenge-platform", new byte[0], "https://ohentai.org/search.php");

        assertTrue(OhentaiParser.isCloudflareChallenge(response, response.responseBody()));
    }

    @Test
    public void cloudflareRayHeaderAloneIsNotTreatedAsChallenge() {
        final Response response = new Response(403, "Forbidden",
                Collections.singletonMap("cf-ray", Collections.singletonList("sample-ray-id")),
                "Access denied", new byte[0], "https://ohentai.org/search.php");

        assertFalse(OhentaiParser.isCloudflareChallenge(response, response.responseBody()));
    }
}
