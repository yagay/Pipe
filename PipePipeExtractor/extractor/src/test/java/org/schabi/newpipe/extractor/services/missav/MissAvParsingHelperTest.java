package org.schabi.newpipe.extractor.services.missav;

import org.jsoup.Jsoup;
import org.junit.Test;
import org.schabi.newpipe.extractor.exceptions.ParsingException;

import static org.junit.Assert.assertEquals;

public final class MissAvParsingHelperTest {
    @Test
    public void skipsInvalidM3u8CandidatesAndUsesLaterPlaylist() throws Exception {
        final String html = "<script>var old='https://cdn.example/playlist.m3u8.backup';"
                + "var current='https://cdn.example/live/playlist.m3u8?token=a+b&amp;part=1';</script>";

        assertEquals("https://cdn.example/live/playlist.m3u8?token=a+b&part=1",
                MissAvParsingHelper.extractHlsUrl(Jsoup.parse(html)));
    }

    @Test
    public void extractsEscapedPlaylistUrls() throws Exception {
        final String html = "<script>var current='https:\\/\\/cdn.example\\/live\\/playlist.m3u8"
                + "?token=one\\u0026part=two';</script>";

        assertEquals("https://cdn.example/live/playlist.m3u8?token=one&part=two",
                MissAvParsingHelper.extractHlsUrl(Jsoup.parse(html)));
    }

    @Test
    public void extractsPlaylistFromPackedMissAvPlayerScript() throws Exception {
        final String dictionary = "m3u8|9c7953bb2f64|8846|4441|e905|297483af|com|surrit|"
                + "https|video|1080p|source1280|720p|source842|playlist|source";
        final String packed = "f=\\'8://7.6/5-4-3-2-1/e.0\\';"
                + "d=\\'8://7.6/5-4-3-2-1/c/9.0\\';"
                + "b=\\'8://7.6/5-4-3-2-1/a/9.0\\';";
        final String html = "<script>eval(function(p,a,c,k,e,d){return p}('" + packed
                + "',16,16,'" + dictionary + "'.split('|'),0,{}));</script>";

        assertEquals("https://surrit.com/297483af-e905-4441-8846-9c7953bb2f64/playlist.m3u8",
                MissAvParsingHelper.extractHlsUrl(Jsoup.parse(html)));
    }

    @Test
    public void extractsPackedPlaylistWhenScriptHasLineBreaks() throws Exception {
        final String html = "<script>var packed='m3u8|segment5|segment4|segment3|"
                + "\nsegment2|segment1|cdn|example|https|video';</script>";

        assertEquals("https://example.cdn/segment1-segment2-segment3-segment4-segment5/playlist.m3u8",
                MissAvParsingHelper.extractHlsUrl(Jsoup.parse(html)));
    }

    @Test(expected = ParsingException.class)
    public void rejectsPageWithoutHlsMetadata() throws Exception {
        MissAvParsingHelper.extractHlsUrl(Jsoup.parse("<html><body>No player</body></html>"));
    }
}
