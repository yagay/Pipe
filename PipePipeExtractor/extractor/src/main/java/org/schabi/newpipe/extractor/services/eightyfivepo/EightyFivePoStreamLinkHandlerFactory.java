package org.schabi.newpipe.extractor.services.eightyfivepo;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

import java.net.URI;

public final class EightyFivePoStreamLinkHandlerFactory extends LinkHandlerFactory {
    private static final EightyFivePoStreamLinkHandlerFactory INSTANCE =
            new EightyFivePoStreamLinkHandlerFactory();

    public static EightyFivePoStreamLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    private EightyFivePoStreamLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) throws ParsingException {
        return EightyFivePoParsingHelper.extractId(url);
    }

    @Override
    public String getUrl(final String id) {
        return EightyFivePoParsingHelper.videoUrlFromId(id);
    }

    @Override
    public LinkHandler fromUrl(final String url, final String baseUrl) throws ParsingException {
        if (!acceptUrl(url)) {
            throw new ParsingException("URL not accepted: " + url);
        }
        final String normalized = EightyFivePoParsingHelper.normalizeUrl(url);
        return new LinkHandler(url, normalized, getId(normalized));
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        final String normalized = EightyFivePoParsingHelper.normalizeUrl(url);
        if (normalized == null) {
            return false;
        }
        try {
            final URI uri = URI.create(normalized);
            final String host = uri.getHost();
            final String path = uri.getPath();
            return ("85po.com".equalsIgnoreCase(host) || "www.85po.com".equalsIgnoreCase(host))
                    && path != null && path.matches("/(?:[a-z]{2}/)?(?:v|video|embed)/\\d+(?:/.*)?");
        } catch (final IllegalArgumentException ignored) {
            return false;
        }
    }
}
