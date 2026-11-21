package org.schabi.newpipe.player.playqueue;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Playlist for a website browse page. Starts with the items already displayed
 * by the fragment, and continues fetching that same kiosk's next pages when
 * the player queue reaches the end.
 */
public final class KioskPlayQueue extends AbstractInfoPlayQueue<KioskInfo> {
    public KioskPlayQueue(final int serviceId, final String url, final Page nextPage,
                          final List<StreamInfoItem> streams, final int index) {
        super(serviceId, url, nextPage, streams, index);
    }

    @Override
    protected String getTag() {
        return "KioskPlayQueue@" + Integer.toHexString(hashCode());
    }

    @Override
    public void fetch() {
        if (isComplete()) {
            return;
        }
        if (isInitial) {
            ExtractorHelper.getKioskInfo(serviceId, baseUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getHeadListObserver());
        } else {
            ExtractorHelper.getMoreKioskItems(serviceId, baseUrl, nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver());
        }
    }
}
