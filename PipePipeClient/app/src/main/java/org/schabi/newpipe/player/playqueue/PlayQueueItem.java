package org.schabi.newpipe.player.playqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.extended.supjav.SupjavServerCatalog;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.util.ExtractorHelper;

import java.io.Serializable;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PlayQueueItem implements Serializable {
    public static final long RECOVERY_UNSET = Long.MIN_VALUE;
    private static final String EMPTY_STRING = "";

    @NonNull
    private final String title;
    @NonNull
    private final String url;
    private final int serviceId;
    private final long duration;
    @NonNull
    private final String thumbnailUrl;
    @NonNull
    private final String uploader;
    private final String uploaderUrl;
    @NonNull
    private final StreamType streamType;

    private final boolean isRoundPlayStream;

    private boolean isAutoQueued;

    private long startAt;

    private long recoveryPosition;
    private Throwable error;

    /**
     * The queue stores the site's canonical video URL for thumbnails/order.
     * Supjav's CD/server selection is a playback property of this item,
     * not a reason to discard the page's queue or its pagination cursor.
     */
    @Nullable
    private volatile String supjavSelectedSource;
    @Nullable
    private transient volatile String supjavPrefetchedSource;
    @Nullable
    private transient volatile StreamInfo supjavPrefetchedInfo;

    PlayQueueItem(@NonNull final StreamInfo info) {
        this(info.getName(), info.getUrl(), info.getServiceId(), info.getDuration(),
                info.getThumbnailUrl(), info.getUploaderName(),
                info.getUploaderUrl(), info.getStreamType(), info.isRoundPlayStream(),
                info.getStartAt());

        if (info.getStartPosition() > 0) {
            setRecoveryPosition(info.getStartPosition() * 1000);
        }
    }

    PlayQueueItem(@NonNull final StreamInfoItem item) {
        this(item.getName(), item.getUrl(), item.getServiceId(), item.getDuration(),
                item.getThumbnailUrl(), item.getUploaderName(),
                item.getUploaderUrl(), item.getStreamType(), item.isRoundPlayStream(), item.getStartAt());
    }

    @SuppressWarnings("ParameterNumber")
    private PlayQueueItem(@Nullable final String name, @Nullable final String url,
                          final int serviceId, final long duration,
                          @Nullable final String thumbnailUrl, @Nullable final String uploader,
                          final String uploaderUrl, @NonNull final StreamType streamType,
                          final boolean isRoundPlayStream, final long startAt) {
        this.title = name != null ? name : EMPTY_STRING;
        this.url = url != null ? url : EMPTY_STRING;
        this.serviceId = serviceId;
        this.duration = duration;
        this.thumbnailUrl = thumbnailUrl != null ? thumbnailUrl : EMPTY_STRING;
        this.uploader = uploader != null ? uploader : EMPTY_STRING;
        this.uploaderUrl = uploaderUrl;
        this.streamType = streamType;
        this.isRoundPlayStream = isRoundPlayStream;
        this.startAt = startAt;

        this.recoveryPosition = RECOVERY_UNSET;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public int getServiceId() {
        return serviceId;
    }

    public long getDuration() {
        return duration;
    }

    @NonNull
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    @NonNull
    public String getUploader() {
        return uploader;
    }

    public String getUploaderUrl() {
        return uploaderUrl;
    }

    @NonNull
    public StreamType getStreamType() {
        return streamType;
    }

    public long getRecoveryPosition() {
        return recoveryPosition;
    }

    /*package-private*/ void setRecoveryPosition(final long recoveryPosition) {
        this.recoveryPosition = recoveryPosition;
    }

    @Nullable
    public Throwable getError() {
        return error;
    }

    /**
     * Preserve a manually selected Supjav server within the existing queue.
     * The item URL itself remains the canonical page URL.
     */
    public void setSupjavSelectedSource(@NonNull final String selectedSource) {
        if (serviceId != ServiceList.Supjav.getServiceId()
                || SupjavServerCatalog.selectedIndex(selectedSource) < 0
                || !SupjavServerCatalog.pageUrl(url)
                        .equals(SupjavServerCatalog.pageUrl(selectedSource))) {
            throw new IllegalArgumentException("Supjav server does not belong to queue item");
        }
        supjavSelectedSource = selectedSource;
        supjavPrefetchedSource = null;
        supjavPrefetchedInfo = null;
    }

    /**
     * Commit one resolved CD only after a real video stream has been found.
     * The media-source manager must use this exact source, rather than
     * re-fetching independently and potentially showing stale queue media.
     */
    public synchronized void setSupjavSelectedSource(
            @NonNull final String selectedSource, @NonNull final StreamInfo info) {
        if (info.getServiceId() != serviceId
                || SupjavServerCatalog.selectedIndex(info.getUrl())
                        != SupjavServerCatalog.selectedIndex(selectedSource)
                || !SupjavServerCatalog.pageUrl(info.getUrl())
                        .equals(SupjavServerCatalog.pageUrl(url))
                || info.getVideoStreams().isEmpty()) {
            throw new IllegalArgumentException("Supjav resolved source does not match CD");
        }
        setSupjavSelectedSource(selectedSource);
        supjavPrefetchedSource = selectedSource;
        supjavPrefetchedInfo = info;
    }

    @Nullable
    String getSupjavSelectedSource() {
        return supjavSelectedSource;
    }

    @Nullable
    synchronized StreamInfo getSupjavPrefetchedInfo() {
        return supjavPrefetchedSource != null
                && supjavPrefetchedSource.equals(supjavSelectedSource)
                ? supjavPrefetchedInfo : null;
    }

    @NonNull
    public Single<StreamInfo> getStream() {
        if (serviceId == ServiceList.Supjav.getServiceId()) {
            // Resolves only the item being played, not every card in a
            // paginated queue. VAS remains preferred on the item's own page.
            return SupjavQueueSourceResolver.load(this)
                    .doOnError(throwable -> error = throwable);
        }
        return ExtractorHelper.getStreamInfo(this.serviceId, this.url, false)
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> error = throwable);
    }

    public boolean isAutoQueued() {
        return isAutoQueued;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Item States, keep external access out
    ////////////////////////////////////////////////////////////////////////////

    public void setAutoQueued(final boolean autoQueued) {
        isAutoQueued = autoQueued;
    }

    public boolean isRoundPlayStream() {
        return isRoundPlayStream;
    }

    public long getStartAt() {
        return startAt;
    }
}
