package org.schabi.newpipe.extractor.services.extended.supjav;

import org.jsoup.Jsoup;
import org.junit.Test;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

import static org.junit.Assert.*;

/** Offline contract tests: no external network requests or credentials. */
public final class SupjavArchitectureTest {
    private static final String DETAIL = "https://supjav.com/123456.html";

    @Test
    public void detailAndSelectedServerUrlsRoundTripIndependently() throws Exception {
        final StreamFactory factory = new StreamFactory(Config.SUPJAV);
        final LinkHandler root = factory.fromUrl(DETAIL);
        final LinkHandler selected = factory.fromUrl(DETAIL + "?pipe_server=4");
        assertEquals(DETAIL, root.getUrl());
        assertEquals(DETAIL + "?pipe_server=4", selected.getUrl());
        assertEquals("123456~s4", selected.getId());
        assertEquals(4, SupjavServerCatalog.selectedIndex(selected.getUrl()));
        assertEquals(-1, SupjavServerCatalog.selectedIndex(DETAIL));
        assertEquals(DETAIL, SupjavServerCatalog.pageUrl(selected.getUrl()));
        assertEquals(selected.getUrl(), factory.fromId(selected.getId()).getUrl());
    }

    @Test
    public void repeatedKnownProviderSetsBecomeRealCds() {
        final String html = "<div class='btnst'>"
                + "<a data-link='alpha1'>EVS</a><a data-link='beta1'>VOE</a>"
                + "<a data-link='gamma1'>LUC</a>"
                + "<a data-link='alpha2'>EVS</a><a data-link='beta2'>VOE</a>"
                + "<a data-link='gamma2'>LUC</a></div>";
        final List<SupjavServerCatalog.Entry> entries = SupjavServerCatalog.parse(
                Jsoup.parse(html, DETAIL), DETAIL);
        assertEquals(6, entries.size());
        assertEquals("CD1", entries.get(0).part);
        assertEquals("CD1", entries.get(2).part);
        assertEquals("CD2", entries.get(3).part);
        assertEquals("VOE", entries.get(4).provider);
    }

    @Test
    public void distinctMirrorsAreNotMistakenForCdParts() {
        final String html = "<div class='btnst'>"
                + "<a data-link='alpha'>EVS</a><a data-link='beta'>VOE</a>"
                + "<a data-link='gamma'>LUC</a></div>";
        final List<SupjavServerCatalog.Entry> entries = SupjavServerCatalog.parse(
                Jsoup.parse(html, DETAIL), DETAIL);
        assertEquals(3, entries.size());
        assertEquals("Main", entries.get(0).part);
        assertEquals("Main", entries.get(2).part);
    }

    @Test
    public void explicitCdLabelsTakePrecedenceOverGuessedGrouping() {
        final String html = "<h4>CD1</h4><div class='btnst'>"
                + "<a data-link='aa1'>EVS</a><a data-link='bb1'>VOE</a>"
                + "</div><h4>CD2</h4><div class='btnst'>"
                + "<a data-link='aa2'>EVS</a><a data-link='bb2'>VOE</a></div>";
        final List<SupjavServerCatalog.Entry> entries = SupjavServerCatalog.parse(
                Jsoup.parse(html, DETAIL), DETAIL);
        assertEquals(4, entries.size());
        assertEquals("CD1", entries.get(0).part);
        assertEquals("CD2", entries.get(2).part);
    }

    @Test
    public void providerParserDoesNotAcceptSiteDetailAsMedia() {
        final SupjavServerCatalog.Entry server =
                new SupjavServerCatalog.Entry(0, "Main", "EVS", "token", DETAIL);
        final List<VideoStream> streams = SupjavProviderExtractor.extract(Jsoup.parse(
                "<html><a href='https://example.org/advert.mp4'>Click</a>"
                        + "<script>var unrelated='https://example.org/preview.mp4'</script></html>",
                "https://provider.example/e/test"), server);
        assertTrue(streams.isEmpty());
    }

    @Test
    public void selectedServerIndexIsBounded() {
        assertEquals(-1, SupjavServerCatalog.selectedIndex(DETAIL + "?pipe_server=99"));
        assertEquals(-1, SupjavServerCatalog.selectedIndex(DETAIL + "?pipe_server=-1"));
        assertEquals(79, SupjavServerCatalog.selectedIndex(DETAIL + "?pipe_server=79"));
    }
}
