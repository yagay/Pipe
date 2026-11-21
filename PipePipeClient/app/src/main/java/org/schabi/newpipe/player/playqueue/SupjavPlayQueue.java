package org.schabi.newpipe.player.playqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * An independent snapshot of Pipe's selected queue for browser-backed HLS.
 * Keeping it separate from the Media3 queue avoids reusing a disposed queue
 * or causing the regular MediaSourceManager to resolve browser-only media.
 *
 * All known entries stay visible in the ORIGINAL Pipe queue drawer.
 * The source's later pagination is deliberately not assumed to be available.
 */
public final class SupjavPlayQueue extends PlayQueue {
    public SupjavPlayQueue(@NonNull final PlayQueue source) {
        super(source.getIndex(), new ArrayList<>(source.getStreams()));
    }

    public SupjavPlayQueue(@NonNull final StreamInfo info) {
        super(0, singleton(info));
    }

    private static List<PlayQueueItem> singleton(final StreamInfo info) {
        final List<PlayQueueItem> list = new ArrayList<>(1);
        list.add(new PlayQueueItem(info));
        return list;
    }

    @Override public boolean isComplete() { return true; }

    @Override public void fetch() { /* Snapshot; original source owns pagination. */ }
}
