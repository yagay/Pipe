package org.schabi.newpipe.extractor.services.internetarchive;

import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;

import javax.annotation.Nullable;

final class InternetArchiveStreamInfoItemExtractor implements StreamInfoItemExtractor {
    private final JsonObject item;
    private final String identifier;

    InternetArchiveStreamInfoItemExtractor(final JsonObject item) {
        this.item = item;
        identifier = InternetArchiveParsingHelper.string(item, "identifier");
    }

    @Override public String getName() {
        final String title = InternetArchiveParsingHelper.string(item, "title");
        return title.isEmpty() ? identifier : title;
    }
    @Override public String getUrl() { return InternetArchiveParsingHelper.detailsUrl(identifier); }
    @Override public String getThumbnailUrl() { return InternetArchiveParsingHelper.thumbnailUrl(identifier); }
    @Override public StreamType getStreamType() { return StreamType.VIDEO_STREAM; }
    @Override public long getDuration() {
        return InternetArchiveParsingHelper.runtimeSeconds(
                InternetArchiveParsingHelper.string(item, "runtime"));
    }
    @Override public long getViewCount() { return -1; }
    @Override public String getUploaderName() {
        final String creator = InternetArchiveParsingHelper.string(item, "creator");
        return creator.isEmpty() ? "Internet Archive" : creator;
    }
    @Override public String getUploaderUrl() { return ""; }
    @Nullable @Override public String getTextualUploadDate() {
        return InternetArchiveParsingHelper.string(item, "publicdate");
    }
    @Nullable @Override public DateWrapper getUploadDate() throws ParsingException { return null; }
    @Nullable @Override public String getShortDescription() {
        return InternetArchiveParsingHelper.string(item, "description");
    }
}
