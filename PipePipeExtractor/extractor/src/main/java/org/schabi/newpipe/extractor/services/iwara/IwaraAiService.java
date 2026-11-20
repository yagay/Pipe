package org.schabi.newpipe.extractor.services.iwara;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.InfoItemsCollector;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.channel.ChannelExtractor;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.comments.CommentsExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.filter.FilterItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor;
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Video extractor for the Iwara AI sister site, using its shared Iwara API site context. */
public final class IwaraAiService extends StreamingService {
    public IwaraAiService(final int id) {
        super(id, "Iwara.ai", Collections.singletonList(ServiceInfo.MediaCapability.VIDEO));
    }

    @Override
    public String getBaseUrl() {
        return IwaraAiApi.WEB;
    }

    @Override
    public LinkHandlerFactory getStreamLHFactory() {
        return IwaraAiStreamLinkHandlerFactory.INSTANCE;
    }

    @Override
    public ListLinkHandlerFactory getChannelLHFactory() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getChannelTabLHFactory() {
        return null;
    }

    @Override
    public ListLinkHandlerFactory getPlaylistLHFactory() {
        return null;
    }

    @Override
    public SearchQueryHandlerFactory getSearchQHFactory() {
        return IwaraAiSearchQueryHandlerFactory.INSTANCE;
    }

    @Override
    public ListLinkHandlerFactory getCommentsLHFactory() {
        return null;
    }

    @Override
    public SearchExtractor getSearchExtractor(final SearchQueryHandler handler) {
        return new IwaraAiSearchExtractor(this, handler);
    }

    @Override
    public SuggestionExtractor getSuggestionExtractor() {
        return null;
    }

    @Override
    public SubscriptionExtractor getSubscriptionExtractor() {
        return null;
    }

    @Override
    public ChannelExtractor getChannelExtractor(final ListLinkHandler handler) {
        return null;
    }

    @Override
    public ChannelTabExtractor getChannelTabExtractor(final ListLinkHandler handler)
            throws ExtractionException {
        throw new ExtractionException("Iwara.ai channels are not supported");
    }

    @Override
    public PlaylistExtractor getPlaylistExtractor(final ListLinkHandler handler)
            throws ExtractionException {
        throw new ExtractionException("Iwara.ai playlists are not supported");
    }

    @Override
    public StreamExtractor getStreamExtractor(final LinkHandler handler) {
        return new IwaraAiStreamExtractor(this, handler);
    }

    @Override
    public CommentsExtractor getCommentsExtractor(final ListLinkHandler handler) {
        return null;
    }

    @Override
    public BulletCommentsExtractor getBulletCommentsExtractor(final ListLinkHandler handler) {
        return null;
    }

    @Override
    public KioskList getKioskList() throws ExtractionException {
        try {
            final KioskList kiosks = new KioskList(this);
            kiosks.addKioskEntry((service, url, id) -> new IwaraAiKioskExtractor(
                    service, IwaraAiKioskLinkHandlerFactory.INSTANCE.fromId(id), id),
                    IwaraAiKioskLinkHandlerFactory.INSTANCE, "trending");
            kiosks.setDefaultKiosk("trending");
            return kiosks;
        } catch (final Exception e) {
            throw new ExtractionException("Could not initialize Iwara.ai kiosks", e);
        }
    }
}

final class IwaraAiStreamLinkHandlerFactory extends LinkHandlerFactory {
    static final IwaraAiStreamLinkHandlerFactory INSTANCE = new IwaraAiStreamLinkHandlerFactory();

    @Override
    public String getId(final String url) throws ParsingException {
        return IwaraAiApi.id(url);
    }

    @Override
    public String getUrl(final String id) {
        return IwaraAiApi.WEB + "/video/" + id;
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return IwaraAiApi.isVideoUrl(url);
    }
}

final class IwaraAiSearchQueryHandlerFactory extends SearchQueryHandlerFactory {
    static final IwaraAiSearchQueryHandlerFactory INSTANCE = new IwaraAiSearchQueryHandlerFactory();

    @Override
    public String getUrl(final String query, final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilters) {
        return IwaraAiApi.search(query, 0);
    }
}

final class IwaraAiKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    static final IwaraAiKioskLinkHandlerFactory INSTANCE = new IwaraAiKioskLinkHandlerFactory();

    @Override
    public String getId(final String url) {
        return "trending";
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilters,
                         final List<FilterItem> sortFilters) {
        return IwaraAiApi.trending(0);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return true;
    }
}

final class IwaraAiSearchExtractor extends SearchExtractor {
    IwaraAiSearchExtractor(final StreamingService service, final SearchQueryHandler handler) {
        super(service, handler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
        // API requests are performed by the page methods below.
    }

    @Override
    protected InfoItemsPage<InfoItem> getInitialPageInternal()
            throws IOException, ExtractionException {
        return page(0);
    }

    @Override
    protected InfoItemsPage<InfoItem> getPageInternal(final Page page)
            throws IOException, ExtractionException {
        return page(IwaraAiApi.pageNumber(page));
    }

    private InfoItemsPage<InfoItem> page(final int number) throws IOException, ExtractionException {
        final JsonObject response = IwaraAiApi.json(IwaraAiApi.search(getSearchString(), number));
        final JsonArray results = response.getArray("results");
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        for (final Object item : results) {
            collector.commit(new IwaraAiStreamInfoItemExtractor((JsonObject) item));
        }
        final int next = number + 1;
        final Page nextPage = results.size() >= 32
                ? new Page(IwaraAiApi.search(getSearchString(), next), String.valueOf(next)) : null;
        return new InfoItemsPage<>(collector, nextPage);
    }
}

final class IwaraAiKioskExtractor extends KioskExtractor<StreamInfoItem> {
    IwaraAiKioskExtractor(final StreamingService service, final ListLinkHandler handler,
                          final String id) {
        super(service, handler, id);
    }

    @Nonnull
    @Override
    public String getName() {
        return "Trending";
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader) {
        // API requests are performed by the page methods below.
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage()
            throws IOException, ExtractionException {
        return page(0);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return page(IwaraAiApi.pageNumber(page));
    }

    private InfoItemsPage<StreamInfoItem> page(final int number)
            throws IOException, ExtractionException {
        final JsonArray results = IwaraAiApi.json(IwaraAiApi.trending(number))
                .getArray("results");
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        for (final Object item : results) {
            collector.commit(new IwaraAiStreamInfoItemExtractor((JsonObject) item));
        }
        final int next = number + 1;
        final Page nextPage = results.size() >= IwaraAiApi.PAGE_SIZE
                ? new Page(IwaraAiApi.trending(next), String.valueOf(next)) : null;
        return new InfoItemsPage<>(collector, nextPage);
    }
}

final class IwaraAiStreamExtractor extends StreamExtractor {
    private JsonObject data;

    IwaraAiStreamExtractor(final StreamingService service, final LinkHandler handler) {
        super(service, handler);
    }

    @Override
    public void onFetchPage(@Nonnull final Downloader downloader)
            throws IOException, ExtractionException {
        data = IwaraAiApi.json(IwaraAiApi.API + "/video/" + getId());
    }

    private void ensureFetched() throws ParsingException {
        if (data == null) {
            throw new ParsingException("Iwara.ai video data was not fetched");
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        ensureFetched();
        return data.getString("title", "");
    }

    @Nonnull
    @Override
    public String getThumbnailUrl() throws ParsingException {
        ensureFetched();
        return IwaraAiApi.thumbnail(data.getObject("file"), data.getInt("thumbnail", 0));
    }

    @Nonnull
    @Override
    public Description getDescription() throws ParsingException {
        ensureFetched();
        final String body = data.getString("body");
        return body == null ? Description.EMPTY_DESCRIPTION
                : new Description(body, Description.PLAIN_TEXT);
    }

    @Override
    public long getLength() throws ParsingException {
        ensureFetched();
        final JsonObject file = data.getObject("file");
        return file == null ? -1 : file.getLong("duration", -1);
    }

    @Nonnull
    @Override
    public String getUploaderName() throws ParsingException {
        ensureFetched();
        final JsonObject user = data.getObject("user");
        return user == null ? "Iwara.ai" : user.getString("name", "Iwara.ai");
    }

    @Nonnull
    @Override
    public String getUploaderUrl() {
        return IwaraAiApi.WEB + "/";
    }

    @Nonnull
    @Override
    public List<String> getTags() throws ParsingException {
        ensureFetched();
        final List<String> tags = new ArrayList<>();
        final JsonArray values = data.getArray("tags");
        if (values != null) {
            for (final Object value : values) {
                if (value instanceof JsonObject) {
                    final String tag = ((JsonObject) value).getString("id");
                    if (tag != null) {
                        tags.add(tag);
                    }
                }
            }
        }
        return tags;
    }

    @Override
    public String getTextualUploadDate() {
        return "";
    }

    @Override
    public List<AudioStream> getAudioStreams() {
        return Collections.emptyList();
    }

    @Override
    public List<VideoStream> getVideoStreams() throws IOException, ExtractionException {
        ensureFetched();
        final String fileApiUrl = data.getString("fileUrl");
        if (fileApiUrl == null || fileApiUrl.isEmpty()) {
            throw new ParsingException("Iwara.ai video has no file metadata URL");
        }

        final LinkedHashMap<String, VideoStream> streams = new LinkedHashMap<>();
        for (final Object value : IwaraAiApi.array(fileApiUrl)) {
            if (!(value instanceof JsonObject)) {
                continue;
            }
            final JsonObject item = (JsonObject) value;
            final JsonObject source = item.getObject("src");
            if (source == null) {
                continue;
            }
            final String mediaUrl = IwaraAiApi.absolute(source.getString("view"));
            if (mediaUrl.isEmpty()) {
                continue;
            }
            final String quality = item.getString("name", "");
            streams.put(mediaUrl, new VideoStream.Builder()
                    .setId(quality)
                    .setContent(IwaraAiApi.mark(mediaUrl, getUrl()), true)
                    .setResolution(quality)
                    .setMediaFormat(MediaFormat.MPEG_4)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .setIsVideoOnly(false)
                    .build());
        }
        if (streams.isEmpty()) {
            throw new ParsingException("Iwara.ai returned no playable video streams");
        }
        return new ArrayList<>(streams.values());
    }

    @Override
    public List<VideoStream> getVideoOnlyStreams() {
        return Collections.emptyList();
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public InfoItemsCollector<? extends InfoItem, ? extends org.schabi.newpipe.extractor.InfoItemExtractor>
    getRelatedItems() throws IOException, ExtractionException {
        ensureFetched();
        final JsonObject response = IwaraAiApi.json(
                IwaraAiApi.API + "/video/" + getId() + "/related");
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());
        final JsonArray results = response.getArray("results");
        for (final Object item : results) {
            collector.commit(new IwaraAiStreamInfoItemExtractor((JsonObject) item));
        }
        return collector;
    }

    @Nonnull
    @Override
    public List<MetaInfo> getMetaInfo() {
        return Collections.emptyList();
    }
}

final class IwaraAiStreamInfoItemExtractor implements StreamInfoItemExtractor {
    private final JsonObject data;

    IwaraAiStreamInfoItemExtractor(final JsonObject data) {
        this.data = data;
    }

    @Override
    public String getName() {
        return data.getString("title", "");
    }

    @Override
    public String getUrl() {
        return IwaraAiApi.WEB + "/video/" + data.getString("id", "");
    }

    @Override
    public String getThumbnailUrl() {
        return IwaraAiApi.thumbnail(data.getObject("file"), data.getInt("thumbnail", 0));
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public long getDuration() {
        final JsonObject file = data.getObject("file");
        return file == null ? -1 : file.getLong("duration", -1);
    }

    @Override
    public long getViewCount() {
        return data.getLong("numViews", -1);
    }

    @Override
    public String getUploaderName() {
        final JsonObject user = data.getObject("user");
        return user == null ? "Iwara.ai" : user.getString("name", "Iwara.ai");
    }

    @Override
    public String getUploaderUrl() {
        return IwaraAiApi.WEB + "/";
    }

    @Nullable
    @Override
    public String getTextualUploadDate() {
        return null;
    }

    @Nullable
    @Override
    public org.schabi.newpipe.extractor.localization.DateWrapper getUploadDate() {
        return null;
    }
}

final class IwaraAiApi {
    static final String WEB = "https://www.iwara.ai";
    static final String API = "https://api.iwara.tv";
    // Match the public web client when it requests the signed file-variant JSON from filesq.
    static final String FILE_VERSION_SALT = "_mSvL05GfEmeEmsEYfGCnVpEjYgTJraJN";
    static final int PAGE_SIZE = 24;

    private IwaraAiApi() {
    }

    static JsonObject json(final String url) throws IOException, ExtractionException {
        final Response response = NewPipe.getDownloader().get(url, apiHeaders());
        if (response.responseCode() != 200) {
            throw new ParsingException("Iwara.ai API returned HTTP " + response.responseCode());
        }
        try {
            return JsonParser.object().from(response.responseBody());
        } catch (final Exception e) {
            throw new ParsingException("Invalid Iwara.ai API response", e);
        }
    }

    static JsonArray array(final String signedFileUrl) throws IOException, ExtractionException {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept", Collections.singletonList("application/json"));
        headers.put("Referer", Collections.singletonList(WEB + "/"));
        headers.put("Origin", Collections.singletonList(WEB));
        headers.put("X-Version", Collections.singletonList(fileVersion(signedFileUrl)));
        headers.put("User-Agent", Collections.singletonList(userAgent()));
        final Response response = NewPipe.getDownloader().get(signedFileUrl, headers);
        if (response.responseCode() != 200) {
            throw new ParsingException("Iwara.ai file API returned HTTP " + response.responseCode());
        }
        try {
            return JsonParser.array().from(response.responseBody());
        } catch (final Exception e) {
            throw new ParsingException("Invalid Iwara.ai file metadata", e);
        }
    }

    static String fileVersion(final String signedFileUrl) throws ParsingException {
        try {
            final URI uri = URI.create(signedFileUrl);
            final String[] segments = uri.getPath().split("/");
            final String fileId = segments[segments.length - 1];
            String expires = null;
            final String query = uri.getRawQuery();
            if (query != null) {
                for (final String pair : query.split("&")) {
                    final int separator = pair.indexOf('=');
                    if (separator > 0 && "expires".equals(pair.substring(0, separator))) {
                        expires = URLDecoder.decode(pair.substring(separator + 1),
                                StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
            if (fileId.isEmpty() || expires == null || expires.isEmpty()) {
                throw new ParsingException("Iwara.ai file URL is missing its signature fields");
            }
            final byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((fileId + "_" + expires + FILE_VERSION_SALT)
                            .getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(digest.length * 2);
            for (final byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (final ParsingException e) {
            throw e;
        } catch (final Exception e) {
            throw new ParsingException("Could not generate Iwara.ai file request version", e);
        }
    }

    static String absolute(@Nullable final String url) {
        if (url == null) {
            return "";
        }
        return url.startsWith("//") ? "https:" + url : url;
    }

    static String id(final String url) throws ParsingException {
        final int videoIndex = url.indexOf("/video/");
        if (videoIndex < 0) {
            throw new ParsingException("Invalid Iwara.ai video URL");
        }
        final String id = url.substring(videoIndex + "/video/".length()).split("[/?#]", 2)[0];
        if (id.isEmpty()) {
            throw new ParsingException("Iwara.ai video URL has no ID");
        }
        return id;
    }

    static boolean isVideoUrl(final String url) {
        if (url == null) {
            return false;
        }
        try {
            final String host = URI.create(url).getHost();
            return ("iwara.ai".equalsIgnoreCase(host) || "www.iwara.ai".equalsIgnoreCase(host))
                    && URI.create(url).getPath().startsWith("/video/");
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    static String search(final String query, final int page) {
        return API + "/search?type=videos&page=" + page + "&query=" + encode(query);
    }

    static String trending(final int page) {
        final String url = API + "/trending/video?rating=all&limit=" + PAGE_SIZE;
        return page <= 0 ? url : url + "&page=" + page;
    }

    static int pageNumber(final Page page) {
        try {
            return Math.max(0, Integer.parseInt(page.getId() == null
                    ? page.getUrl() : page.getId()));
        } catch (final NumberFormatException e) {
            return 1;
        }
    }

    static String thumbnail(@Nullable final JsonObject file, final int thumbnailIndex) {
        if (file == null || file.getString("id") == null) {
            return "";
        }
        return "https://i.iwara.tv/image/thumbnail/" + file.getString("id")
                + "/thumbnail-" + String.format(Locale.US, "%02d", thumbnailIndex) + ".jpg";
    }

    static String mark(final String mediaUrl, final String pageUrl) {
        return mediaUrl + "#iwaraai=1&ref=" + encode(pageUrl);
    }

    private static Map<String, List<String>> apiHeaders() {
        final Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept", Collections.singletonList("application/json"));
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("Referer", Collections.singletonList(WEB + "/"));
        headers.put("Origin", Collections.singletonList(WEB));
        headers.put("X-Site", Collections.singletonList("www.iwara.ai"));
        headers.put("User-Agent", Collections.singletonList(userAgent()));
        return headers;
    }

    private static String userAgent() {
        return "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";
    }

    private static String encode(final String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
