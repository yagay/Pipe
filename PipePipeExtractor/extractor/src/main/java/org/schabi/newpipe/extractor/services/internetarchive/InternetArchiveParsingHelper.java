package org.schabi.newpipe.extractor.services.internetarchive;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class InternetArchiveParsingHelper {
    static final String BASE_URL = "https://archive.org";
    static final int RESULTS_PER_PAGE = 24;

    private InternetArchiveParsingHelper() { }

    static JsonObject getJson(final Downloader downloader, final String url)
            throws IOException, ExtractionException {
        final Response response = downloader.get(url);
        if (response == null || response.responseCode() < 200 || response.responseCode() >= 300) {
            throw new ParsingException("Internet Archive returned HTTP "
                    + (response == null ? "no response" : response.responseCode()));
        }
        try {
            return JsonParser.object().from(response.responseBody());
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse Internet Archive response", e);
        }
    }

    static String searchUrl(final String query, final int page) {
        final String trimmedQuery = query == null ? "" : query.trim();
        final String archiveQuery = trimmedQuery.isEmpty()
                ? "mediatype:movies"
                : "mediatype:movies AND \"" + escapeQuery(trimmedQuery) + "\"";
        return BASE_URL + "/advancedsearch.php?q=" + encode(archiveQuery)
                + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=description"
                + "&fl%5B%5D=creator&fl%5B%5D=publicdate&fl%5B%5D=downloads"
                + "&fl%5B%5D=runtime&output=json&rows=" + RESULTS_PER_PAGE
                + "&page=" + Math.max(1, page) + "&sort%5B%5D=publicdate+desc";
    }

    static String metadataUrl(final String identifier) {
        return BASE_URL + "/metadata/" + encodePath(identifier);
    }

    static String detailsUrl(final String identifier) {
        return BASE_URL + "/details/" + encodePath(identifier);
    }

    static String downloadUrl(final String identifier, final String filename) {
        return BASE_URL + "/download/" + encodePath(identifier) + "/" + encodePath(filename);
    }

    static String thumbnailUrl(final String identifier) {
        return BASE_URL + "/services/img/" + encodePath(identifier);
    }

    static String string(final JsonObject object, final String key) {
        if (object == null) {
            return "";
        }
        final Object value = object.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof JsonArray) {
            final List<String> values = new ArrayList<>();
            for (final Object item : (JsonArray) value) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    values.add(String.valueOf(item));
                }
            }
            return String.join(", ", values);
        }
        return "";
    }

    static List<String> strings(final JsonObject object, final String key) {
        if (object == null) {
            return Collections.emptyList();
        }
        final Object value = object.get(key);
        if (value instanceof JsonArray) {
            final List<String> values = new ArrayList<>();
            for (final Object item : (JsonArray) value) {
                if (item != null && !String.valueOf(item).trim().isEmpty()) {
                    values.add(String.valueOf(item));
                }
            }
            return values;
        }
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return Collections.singletonList((String) value);
        }
        return Collections.emptyList();
    }

    static List<VideoStream> videoStreams(final JsonArray files, final String identifier) {
        if (files == null) {
            return Collections.emptyList();
        }
        final List<VideoFile> candidates = new ArrayList<>();
        for (final Object value : files) {
            if (!(value instanceof JsonObject)) {
                continue;
            }
            final JsonObject file = (JsonObject) value;
            final String name = string(file, "name");
            final MediaFormat format = formatFor(name);
            if (format == null || isDerivativeOrThumbnail(name)) {
                continue;
            }
            candidates.add(new VideoFile(name, format,
                    file.getInt("height", 0), string(file, "format")));
        }
        candidates.sort(Comparator.comparingInt((VideoFile file) -> file.height).reversed());

        final List<VideoStream> streams = new ArrayList<>();
        for (final VideoFile file : candidates) {
            final String quality = file.height > 0 ? file.height + "p"
                    : file.formatName.isEmpty() ? file.name : file.formatName;
            streams.add(new VideoStream.Builder()
                    .setId(file.name)
                    .setContent(downloadUrl(identifier, file.name), true)
                    .setResolution(quality)
                    .setMediaFormat(file.mediaFormat)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .setIsVideoOnly(false)
                    .build());
        }
        return streams;
    }

    static long runtimeSeconds(final String runtime) {
        if (runtime == null || runtime.trim().isEmpty()) {
            return -1;
        }
        try {
            if (runtime.contains(":")) {
                long seconds = 0;
                for (final String part : runtime.trim().split(":")) {
                    seconds = seconds * 60 + Long.parseLong(part.trim());
                }
                return seconds;
            }
            return Math.max(0, Math.round(Double.parseDouble(runtime.trim())));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private static MediaFormat formatFor(final String filename) {
        final String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) {
            return MediaFormat.MPEG_4;
        }
        if (lower.endsWith(".webm")) {
            return MediaFormat.WEBM;
        }
        if (lower.endsWith(".3gp")) {
            return MediaFormat.v3GPP;
        }
        return null;
    }

    private static boolean isDerivativeOrThumbnail(final String filename) {
        final String lower = filename.toLowerCase(Locale.ROOT);
        return lower.contains(".thumbs/") || lower.contains("__ia_thumb")
                || lower.endsWith("_thumb.mp4") || lower.endsWith("_preview.mp4");
    }

    private static String escapeQuery(final String query) {
        return query.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(final String path) {
        final String[] segments = path.split("/", -1);
        final StringBuilder encoded = new StringBuilder();
        for (final String segment : segments) {
            if (encoded.length() > 0) {
                encoded.append('/');
            }
            encoded.append(encode(segment).replace("+", "%20"));
        }
        return encoded.toString();
    }

    private static final class VideoFile {
        final String name;
        final MediaFormat mediaFormat;
        final int height;
        final String formatName;

        VideoFile(final String name, final MediaFormat mediaFormat,
                  final int height, final String formatName) {
            this.name = name;
            this.mediaFormat = mediaFormat;
            this.height = height;
            this.formatName = formatName;
        }
    }
}
