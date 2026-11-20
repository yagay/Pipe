package org.schabi.newpipe.extractor.services.internetarchive;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

public final class InternetArchiveStreamExtractor extends StreamExtractor {
    private JsonObject metadata;
    private JsonArray files;

    public InternetArchiveStreamExtractor(final org.schabi.newpipe.extractor.StreamingService service,
                                          final LinkHandler linkHandler) {
        super(service, linkHandler);
    }

    @Override
    public void onFetchPage(@Nonnull final org.schabi.newpipe.extractor.downloader.Downloader downloader)
            throws IOException, ExtractionException {
        final JsonObject root = InternetArchiveParsingHelper.getJson(downloader,
                InternetArchiveParsingHelper.metadataUrl(getId()));
        metadata = root.getObject("metadata");
        files = root.getArray("files");
        if (metadata == null || files == null) {
            throw new ParsingException("Internet Archive item metadata is incomplete");
        }
    }

    @Nonnull
    @Override public String getName() throws ParsingException {
        final String title = InternetArchiveParsingHelper.string(metadata, "title");
        return title.isEmpty() ? getId() : title;
    }
    @Nonnull
    @Override public String getThumbnailUrl() throws ParsingException {
        return InternetArchiveParsingHelper.thumbnailUrl(getId());
    }
    @Nonnull
    @Override public Description getDescription() {
        final String description = InternetArchiveParsingHelper.string(metadata, "description");
        if (description.isEmpty()) {
            return Description.EMPTY_DESCRIPTION;
        }
        final int descriptionType = description.matches("(?s).*<[^>]+>.*")
                ? Description.HTML : Description.PLAIN_TEXT;
        return new Description(description, descriptionType);
    }
    @Override public long getLength() {
        return InternetArchiveParsingHelper.runtimeSeconds(
                InternetArchiveParsingHelper.string(metadata, "runtime"));
    }
    @Nonnull
    @Override public String getUploaderName() {
        final String creator = InternetArchiveParsingHelper.string(metadata, "creator");
        return creator.isEmpty() ? "Internet Archive" : creator;
    }
    @Nonnull
    @Override public String getUploaderUrl() { return ""; }
    @Override public List<String> getTags() {
        return InternetArchiveParsingHelper.strings(metadata, "subject");
    }
    @Override public List<AudioStream> getAudioStreams() { return Collections.emptyList(); }
    @Override public List<VideoStream> getVideoStreams() throws ParsingException {
        final List<VideoStream> streams = InternetArchiveParsingHelper.videoStreams(files, getId());
        if (streams.isEmpty()) {
            throw new ParsingException("No playable Internet Archive video files found");
        }
        return streams;
    }
    @Override public List<VideoStream> getVideoOnlyStreams() { return Collections.emptyList(); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Nonnull @Override public List<MetaInfo> getMetaInfo() { return Collections.emptyList(); }
}
