package org.schabi.newpipe.extractor.services.missav;

import org.junit.Test;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MissAvHlsQualityParserTest {
    private static final String MASTER = "https://cdn.example.com/videos/playlist.m3u8?token=a";

    @Test
    public void parsesAndOrdersMasterRenditionsByHighestResolution() {
        final String manifest = "#EXTM3U\n"
                + "#EXT-X-VERSION:3\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360\n"
                + "360p/video.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=5500000,RESOLUTION=1920x1080\n"
                + "1080p/video.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2300000,RESOLUTION=1280x720\n"
                + "720p/video.m3u8\n";
        final List<MissAvHlsQualityParser.Variant> streams =
                MissAvHlsQualityParser.parse(MASTER, manifest);
        assertEquals(3, streams.size());
        assertEquals("1080p", streams.get(0).resolution);
        assertEquals("720p", streams.get(1).resolution);
        assertEquals("360p", streams.get(2).resolution);
        assertEquals("https://cdn.example.com/videos/1080p/video.m3u8",
                streams.get(0).url);
    }

    @Test
    public void retainsHigherBitrateWhenResolutionIsDuplicated() {
        final String manifest = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720\n"
                + "low.m3u8\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720\n"
                + "high.m3u8\n";
        final List<MissAvHlsQualityParser.Variant> streams =
                MissAvHlsQualityParser.parse(MASTER, manifest);
        assertEquals(1, streams.size());
        assertEquals("https://cdn.example.com/videos/high.m3u8", streams.get(0).url);
    }

    @Test
    public void mediaPlaylistDoesNotInventQualityOptions() {
        final String manifest = "#EXTM3U\n#EXT-X-TARGETDURATION:10\n"
                + "#EXTINF:10,\nsegment001.ts\n";
        assertTrue(MissAvHlsQualityParser.parse(MASTER, manifest).isEmpty());
    }

    @Test
    public void rejectsNonHttpsVariants() {
        final String manifest = "#EXTM3U\n"
                + "#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360\n"
                + "http://other.example.org/video.m3u8\n";
        assertTrue(MissAvHlsQualityParser.parse(MASTER, manifest).isEmpty());
    }
}
