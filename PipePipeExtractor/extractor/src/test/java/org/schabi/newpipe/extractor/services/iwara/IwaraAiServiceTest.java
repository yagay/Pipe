package org.schabi.newpipe.extractor.services.iwara;

import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class IwaraAiServiceTest {
    @Test
    public void iwaraAiAndTvVideoUrlsRemainIsolated() throws Exception {
        assertSame(ServiceList.IwaraAi,
                NewPipe.getServiceByUrl("https://www.iwara.ai/video/example-id/slug"));
        assertSame(ServiceList.IwaraAi,
                NewPipe.getServiceByUrl("https://iwara.ai/video/example-id/slug"));
        assertSame(ServiceList.Iwara,
                NewPipe.getServiceByUrl("https://www.iwara.tv/video/example-id/slug"));
        assertEquals(20, ServiceList.IwaraAi.getServiceId());
    }

    @Test
    public void searchAndTrendingUseIwaraAiSiteEndpoints() throws Exception {
        assertEquals("https://api.iwara.tv/search?type=videos&page=0&query=ai+art",
                ServiceList.IwaraAi.getSearchQHFactory().fromQuery("ai art").getUrl());
        assertEquals("https://api.iwara.tv/trending/video?rating=all&limit=24",
                ServiceList.IwaraAi.getKioskList()
                        .getListLinkHandlerFactoryByType("trending")
                        .fromId("trending").getUrl());
    }

    @Test
    public void fileMetadataRequestVersionIsDerivedFromSignedUrlFields() throws Exception {
        assertEquals("681390f0426284e866e55cf8aab028aa2013ae29",
                IwaraAiApi.fileVersion(
                        "https://filesq.iwara.tv/file/video-id?expires=1700000000&hash=ignored"));
    }
}
