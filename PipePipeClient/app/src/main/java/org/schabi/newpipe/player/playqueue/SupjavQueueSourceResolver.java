package org.schabi.newpipe.player.playqueue;

import androidx.annotation.NonNull;
import org.schabi.newpipe.extractor.services.extended.supjav.SupjavServerCatalog;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.util.ExtractorHelper;

import java.io.IOException;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Lazy source selection for Supjav browse queues.
 *
 * KioskPlayQueue keeps the same page, cards and pagination as MissAV.
 * Only when an item is about to play do we fetch its own server catalog and
 * resolve the selected or preferred real server. No extra UI/player engine.
 */
final class SupjavQueueSourceResolver {
    private SupjavQueueSourceResolver() { }

    @NonNull
    static Single<StreamInfo> load(@NonNull final PlayQueueItem item) {
        return Single.fromCallable(() -> {
            final String chosen = item.getSupjavSelectedSource();
            if (chosen != null) return chosen;

            final String pageUrl = item.getUrl();
            if (SupjavServerCatalog.selectedIndex(pageUrl) >= 0) return pageUrl;

            final List<SupjavServerCatalog.Entry> available =
                    SupjavServerCatalog.forPage(pageUrl);
            final int preferred = SupjavServerCatalog.preferredIndex(available, null);
            if (preferred < 0) {
                throw new IOException("Supjav video page has no playable servers");
            }
            return SupjavServerCatalog.selectedUrl(pageUrl, preferred);
        }).subscribeOn(Schedulers.io())
                .flatMap(url -> ExtractorHelper.getStreamInfo(
                        item.getServiceId(), url, false));
    }
}
