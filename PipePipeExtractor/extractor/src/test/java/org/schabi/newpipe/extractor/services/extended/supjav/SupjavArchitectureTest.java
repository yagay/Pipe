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
    public void defaultPrefersVasOnFirstCdAndPreservesManualAvailability() {
        final String html = "<div class='btnst'>"
                + "<a data-link='evs1'>EVS</a><a data-link='vas1'>VAS</a>"
                + "<a data-link='luc1'>LUC</a>"
                + "<a data-link='evs2'>EVS</a><a data-link='vas2'>VAS</a>"
                + "<a data-link='luc2'>LUC</a></div>";
        final List<SupjavServerCatalog.Entry> entries = SupjavServerCatalog.parse(
                Jsoup.parse(html, DETAIL), DETAIL);
        assertEquals(1, SupjavServerCatalog.preferredIndex(entries, null));
        assertEquals(4, SupjavServerCatalog.preferredIndex(entries, "CD2"));
        assertEquals(6, entries.size());
    }

    @Test
    public void cdAndServerPickersAreStrictlyScopedAndPreservePreferredProvider() {
        final String html = "<div class='btnst'>"
                + "<a data-cd='1' data-link='x1'>EVS</a>"
                + "<a data-cd='1' data-link='x2'>VAS</a>"
                + "<a data-cd='1' data-link='x3'>LUC</a>"
                + "<a data-cd='2' data-link='y1'>EVS</a>"
                + "<a data-cd='2' data-link='y2'>VOE</a>"
                + "<a data-cd='2' data-link='y3'>LUC</a></div>";
        final List<SupjavServerCatalog.Entry> entries =
                SupjavServerCatalog.parse(Jsoup.parse(html, DETAIL), DETAIL);
        assertEquals(java.util.Arrays.asList("CD1", "CD2"),
                SupjavServerCatalog.parts(entries));
        final List<SupjavServerCatalog.Entry> first =
                SupjavServerCatalog.serversForPart(entries, "CD1");
        final List<SupjavServerCatalog.Entry> second =
                SupjavServerCatalog.serversForPart(entries, "CD2");
        assertEquals(3, first.size());
        assertEquals(3, second.size());
        assertEquals("VAS", first.get(1).provider);
        assertEquals("VOE", second.get(1).provider);
        assertEquals("LUC", SupjavServerCatalog.preferredForPart(
                entries, "CD2", "LUC").provider);
        // CD2 has no VAS: fallback must stay in CD2, never jump to CD1 VAS.
        assertEquals("EVS", SupjavServerCatalog.preferredForPart(
                entries, "CD2", "VAS").provider);
        assertNull(SupjavServerCatalog.preferredForPart(entries, "CD3", "LUC"));
        assertEquals(5, SupjavServerCatalog.entryForIndex(entries, 5).index);
        assertNull(SupjavServerCatalog.entryForIndex(entries, 80));
    }

    @Test
    public void defaultUsesRealFirstLineWhenVasNotPresent() {
        final List<SupjavServerCatalog.Entry> entries = SupjavServerCatalog.parse(
                Jsoup.parse("<div class='btnst'><a data-link='evs'>EVS</a>"
                        + "<a data-link='voe'>VOE</a></div>", DETAIL), DETAIL);
        assertEquals(0, SupjavServerCatalog.preferredIndex(entries, null));
    }

    @Test
    public void evsAndLucPreferSignedHls2OverHls4AndHls3() {
        final String player = "<script>var sources = {"
                + "hls3:'https://cdn.example.com/hls3/master.txt?key=c',"
                + "hls4:'https://cdn.example.com/hls4/master.txt?key=b',"
                + "hls2:'https://cdn.example.com/hls2/master.m3u8?key=a'};</script>";
        for (final String provider : new String[]{"EVS", "LUC"}) {
            final SupjavServerCatalog.Entry entry = new SupjavServerCatalog.Entry(
                    0, "Main", provider, "sample", DETAIL);
            final List<VideoStream> streams = SupjavProviderExtractor.extract(
                    Jsoup.parse(player, "https://player.example/e/sample"), entry);
            assertEquals(3, streams.size());
            assertTrue(streams.get(0).getContent().contains("/hls2/"));
            assertTrue(streams.get(1).getContent().contains("/hls4/"));
            assertTrue(streams.get(2).getContent().contains("/hls3/"));
        }
    }

    @Test
    public void lucNativeProbePolicyIsCarriedWithoutChangingSignedMediaUrl() {
        final SupjavServerCatalog.Entry luc = new SupjavServerCatalog.Entry(
                0, "Main", "LUC", "sample", DETAIL);
        final String html = "<script>var sources={"
                + "hls3:'https://cdn.example.com/hls3/master.txt?key=c',"
                + "hls4:'https://cdn.example.com/hls4/master.txt?key=b',"
                + "hls2:'https://cdn.example.com/hls2/master.m3u8?key=a'};</script>";
        final List<VideoStream> candidates = SupjavProviderExtractor.extract(
                Jsoup.parse(html, "https://player.example/e/sample"), luc);
        assertEquals(3, candidates.size());
        final VideoStream original = candidates.get(0);
        final VideoStream withoutOrigin = SupjavProviderExtractor.withNativeOriginPolicy(
                original, false);
        assertTrue(withoutOrigin.getContent().contains("/hls2/master.m3u8?key=a"));
        assertTrue(withoutOrigin.getContent().endsWith("&origin=0"));
        assertEquals(original.getId(), withoutOrigin.getId());
        assertEquals(original.getDeliveryMethod(), withoutOrigin.getDeliveryMethod());
        assertEquals(original.getResolution(), withoutOrigin.getResolution());
        assertSame(original, SupjavProviderExtractor.withNativeOriginPolicy(original, true));
    }

    @Test
    public void lucNativeGatewayRejectsUnsafeRedirectsAndPreservesSignedQueries() {
        assertEquals("https://luminscdn.xyz/e/abc?token=A%2BB",
                SupjavLucNativeGateway.checkedRedirect(
                        "https://lk1.supremejav.com/supjav.php?c=ABC",
                        "https://luminscdn.xyz/e/abc?token=A%2BB"));
        assertEquals("https://luminscdn.xyz/e/next",
                SupjavLucNativeGateway.checkedRedirect(
                        "https://luminscdn.xyz/e/old", "/e/next"));
        assertNull(SupjavLucNativeGateway.checkedRedirect(
                "https://luminscdn.xyz/e/old", "http://127.0.0.1/payload"));
        assertNull(SupjavLucNativeGateway.checkedRedirect(
                "https://luminscdn.xyz/e/old", "https://localhost/payload"));
        assertTrue(SupjavLucNativeGateway.retryable(403));
        assertTrue(SupjavLucNativeGateway.retryable(416));
        assertFalse(SupjavLucNativeGateway.retryable(404));
        final SupjavServerCatalog.Entry luc =
                new SupjavServerCatalog.Entry(4, "CD1", "LUC", "token", DETAIL);
        final VideoStream signed = SupjavProviderExtractor.extract(
                Jsoup.parse("<script>var links={hls2:"
                                + "'https://cdn.example.com/hls2/master.m3u8?sig=a%26b'};"
                                + "</script>",
                        "https://luminscdn.xyz/e/abc"), luc).get(0);
        assertEquals("https://cdn.example.com/hls2/master.m3u8?sig=a%26b",
                SupjavLucNativeGateway.mediaUrl(signed));
    }

    @Test
    public void voeRedirectsOnlyFollowDeclaredPlayerAndIgnoreGenericScripts() {
        final SupjavServerCatalog.Entry entry = new SupjavServerCatalog.Entry(
                0, "Main", "VOE", "sample", DETAIL);
        final String html = "<script>window.location.href="
                + "'https://media.example/e/valid123';</script>"
                + "<a href='https://ads.example/advert.mp4'>ad</a>";
        final List<String> pages = SupjavProviderExtractor.declaredPlayerRedirects(
                Jsoup.parse(html, "https://voe.example/e/entry"), entry);
        assertEquals(1, pages.size());
        assertEquals("https://media.example/e/valid123", pages.get(0));
    }

    @Test
    public void selectedServerIndexIsBounded() {
        assertEquals(-1, SupjavServerCatalog.selectedIndex(DETAIL + "?pipe_server=99"));
        assertEquals(-1, SupjavServerCatalog.selectedIndex(DETAIL + "?pipe_server=-1"));
        assertEquals(79, SupjavServerCatalog.selectedIndex(DETAIL + "?pipe_server=79"));
    }
}
