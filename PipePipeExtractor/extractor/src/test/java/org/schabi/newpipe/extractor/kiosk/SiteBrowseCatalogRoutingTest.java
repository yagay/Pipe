package org.schabi.newpipe.extractor.kiosk;

import org.junit.Test;
import org.schabi.newpipe.extractor.services.pornhub.PornhubService;
import org.schabi.newpipe.extractor.services.kissjav.KissJavService;
import static org.junit.Assert.*;

public final class SiteBrowseCatalogRoutingTest {
    @Test
    public void allowsPornhubCategoryQueryButRejectsVideoDetail() {
        final PornhubService service = new PornhubService(900);
        assertTrue(SiteBrowseCatalog.isWebsiteBrowseUrl(service,
                "https://www.pornhub.com/video?c=3"));
        assertFalse(SiteBrowseCatalog.isWebsiteBrowseUrl(service,
                "https://www.pornhub.com/view_video.php?viewkey=abc"));
    }

    @Test
    public void blocksCrossDomainNavigation() {
        final PornhubService service = new PornhubService(900);
        assertFalse(SiteBrowseCatalog.isWebsiteBrowseUrl(service,
                "https://notpornhub.com/categories/action"));
        assertFalse(SiteBrowseCatalog.isWebsiteBrowseUrl(service,
                "http://www.pornhub.com/categories/action"));
    }

    @Test
    public void blocksAccountAndSearchResults() {
        final PornhubService service = new PornhubService(900);
        assertFalse(SiteBrowseCatalog.isWebsiteBrowseUrl(service,
                "https://www.pornhub.com/account/settings"));
        assertFalse(SiteBrowseCatalog.isWebsiteBrowseUrl(service,
                "https://www.pornhub.com/search/action"));
    }

    @Test
    public void allowsRegisteredCategoryPath() {
        final KissJavService service = new KissJavService(901);
        assertTrue(SiteBrowseCatalog.isWebsiteBrowseUrl(service,
                service.getBaseUrl() + "/categories/action/"));
    }
}
