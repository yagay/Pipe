package org.schabi.newpipe.extractor.services.internetarchive;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonParser;

import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class InternetArchiveServiceTest {
    @Test
    public void registersServiceAndRecognizesDetailsLinks() throws Exception {
        assertSame(ServiceList.InternetArchive,
                NewPipe.getServiceByUrl("https://archive.org/details/sample_item"));
        assertEquals(21, ServiceList.InternetArchive.getServiceId());
        assertEquals("Internet Archive", ServiceList.InternetArchive.getServiceInfo().getName());
        assertEquals("sample_item", ServiceList.InternetArchive.getStreamLHFactory()
                .fromUrl("https://www.archive.org/details/sample_item?view=theater").getId());
    }

    @Test
    public void searchAndLatestKioskUsePagedMovieSearchApi() throws Exception {
        final SearchQueryHandler handler = ServiceList.InternetArchive.getSearchQHFactory()
                .fromQuery("silent film");
        assertEquals("silent film", handler.getSearchString());
        assertTrue(handler.getUrl().contains("advancedsearch.php"));
        assertTrue(handler.getUrl().contains("mediatype%3Amovies"));
        assertTrue(handler.getUrl().contains("silent+film"));
        assertEquals("silent film", ServiceList.InternetArchive.getSearchQHFactory()
                .fromUrl(handler.getUrl()).getId());

        assertEquals("latest", ServiceList.InternetArchive.getKioskList().getDefaultKioskId());
        assertNotNull(ServiceList.InternetArchive.getKioskList()
                .getListLinkHandlerFactoryByType("latest").fromId("latest"));
    }

    @Test
    public void onlyDirectVideoFilesBecomePlayableStreams() throws Exception {
        final JsonArray files = JsonParser.array().from("[{"
                + "\"name\":\"video file.mp4\",\"height\":720,\"format\":\"MPEG4\"},"
                + "{\"name\":\"captions.srt\"},"
                + "{\"name\":\"__ia_thumb.mp4\"}]");

        final List<VideoStream> streams = InternetArchiveParsingHelper.videoStreams(files, "sample");
        assertEquals(1, streams.size());
        assertTrue(streams.get(0).getContent().contains("/download/sample/video%20file.mp4"));
        assertFalse(streams.get(0).getContent().contains("captions"));
        assertEquals("720p", streams.get(0).getResolution());
    }

    @Test
    public void rejectsOtherArchiveRoutesAndParsesRuntime() throws ParsingException {
        assertFalse(ServiceList.InternetArchive.getStreamLHFactory()
                .acceptUrl("https://archive.org/download/sample/video.mp4"));
        assertEquals(3723, InternetArchiveParsingHelper.runtimeSeconds("1:02:03"));
        assertEquals(-1, InternetArchiveParsingHelper.runtimeSeconds("not a duration"));
    }
}
