package org.schabi.newpipe.extractor.services.extended.supjav;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selected-line-only navigation; no fan-out to other CDs or servers.
 * A failure here cannot prevent the lightweight first-party detail screen
 * from opening. Third-party pages are visited only when the user selects
 * their corresponding server.
 */
final class SupjavServerResolver {
    private static final int MAX_HOPS = 6;
    private static final int MAX_CANDIDATES = 12;
    private static final int MAX_PROBES = 3;

    private static final class Route {
        final String url;
        final String referer;
        Route(final String url, final String referer) {
            this.url = url;
            this.referer = referer;
        }
    }

    private SupjavServerResolver() { }

    static List<VideoStream> resolveSelected(final Document detail,
                                             final String selectedUrl,
                                             final int selectedIndex)
            throws IOException, ParsingException {
        final String detailUrl = SupjavServerCatalog.pageUrl(selectedUrl);
        final List<SupjavServerCatalog.Entry> entries =
                SupjavServerCatalog.parse(detail, detailUrl);
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            throw new ParsingException("Selected Supjav server is not on this page");
        }
        final SupjavServerCatalog.Entry selected = entries.get(selectedIndex);
        final String start = SupjavProviderExtractor.resolveDeclaredUrl(selected.link, detailUrl);
        if (start == null) {
            throw new ParsingException("Supjav " + selected.label()
                    + " has an unsupported player link; browser playback required");
        }

        final ArrayDeque<Route> pending = new ArrayDeque<>();
        final Set<String> visited = new HashSet<>();
        final Map<String, VideoStream> candidates = new LinkedHashMap<>();
        pending.add(new Route(start, detailUrl));
        final long deadline = System.nanoTime() + 30_000_000_000L;
        int hops = 0;
        int lastHttp = 0;
        boolean verification = false;

        while (!pending.isEmpty() && hops < MAX_HOPS
                && candidates.size() < MAX_CANDIDATES && System.nanoTime() < deadline) {
            final Route route = pending.removeFirst();
            if (!visited.add(route.url) ||
                    !SupjavProviderExtractor.safePublicHttps(route.url)) continue;
            hops++;

            if (SupjavProviderExtractor.isMedia(route.url)) {
                SupjavProviderExtractor.add(candidates, route.url,
                        selected.part + "|" + selected.provider, route.referer);
                continue;
            }

            final Map<String, List<String>> headers = new HashMap<>();
            headers.put("Referer", Collections.singletonList(route.referer));
            headers.put("Accept", Collections.singletonList(
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
            try {
                final Response response = NewPipe.getDownloader().get(route.url, headers);
                lastHttp = response.responseCode();
                if (lastHttp == 403 || lastHttp == 429) continue;
                if (lastHttp < 200 || lastHttp >= 300) continue;
                if (response.responseBody().length() > 1_000_000) continue;
                final String finalUrl = response.latestUrl();
                if (!SupjavProviderExtractor.safePublicHttps(finalUrl)) continue;
                final Document page = Jsoup.parse(response.responseBody(), finalUrl);
                if (challenge(page)) {
                    verification = true;
                    continue;
                }
                for (final VideoStream stream : SupjavProviderExtractor.extract(page, selected)) {
                    candidates.putIfAbsent(stream.getContent(), stream);
                    if (candidates.size() >= MAX_CANDIDATES) break;
                }

                // VOE can navigate from its entry page to the real player
                // using a declared window.location assignment, not an iframe.
                for (final String destination :
                        SupjavProviderExtractor.declaredPlayerRedirects(page, selected)) {
                    if (pending.size() >= 8) break;
                    if ((validFrame(destination)
                            || SupjavProviderExtractor.isMedia(destination))
                            && !visited.contains(destination)) {
                        pending.addLast(new Route(destination, finalUrl));
                    }
                }

                // Only follow recognized player embeds linked from this selected
                // provider, never unrelated ad/sidebar frames.
                for (final Element frame : page.select(
                        "iframe#video[src], iframe#player[src], "
                        + ".player iframe[src], iframe[src*=/embed/], "
                        + "iframe[src*=/e/], iframe[src*=/player/]")) {
                    if (pending.size() >= 8) break;
                    final String target = frame.absUrl("src");
                    if (!validFrame(target) || visited.contains(target)) continue;
                    pending.addLast(new Route(target, finalUrl));
                }
            } catch (final Exception ignored) {
                // A host can fail or require JavaScript; report a source-specific
                // failure instead of globally invalidating the video's details.
            }
        }

        final List<VideoStream> playable = new ArrayList<>();
        final List<VideoStream> unknown = new ArrayList<>();
        int confirmedBlocked = 0;
        int probes = 0;
        final List<String> probeDetails = new ArrayList<>();
        for (final VideoStream candidate : candidates.values()) {
            final SupjavMediaProbe.Assessment check = probes++ < MAX_PROBES
                    ? SupjavMediaProbe.assess(candidate)
                    : new SupjavMediaProbe.Assessment(
                            SupjavMediaProbe.Status.UNKNOWN, "not-probed");
            // These diagnostics omit full URLs and tokens by design.
            if (probeDetails.size() < 8) {
                probeDetails.add(check.status.name() + "(" + check.profiles + ")");
            }
            if (check.status == SupjavMediaProbe.Status.PLAYABLE) {
                playable.add(candidate);
            } else if (check.status == SupjavMediaProbe.Status.BLOCKED) {
                confirmedBlocked++;
            } else {
                unknown.add(candidate);
            }
        }
        playable.addAll(unknown);
        if (playable.isEmpty()) {
            final String reason = verification ? "requires browser verification"
                    : confirmedBlocked > 0 ? "media returned HTTP error or invalid response"
                    : lastHttp == 403 || lastHttp == 429 ? "access refused (HTTP " + lastHttp + ")"
                    : "requires JavaScript or browser player";
            throw new ParsingException("Supjav " + selected.label() + ": " + reason
                    + "; candidates=" + candidates.size()
                    + "; checks=" + String.join(", ", probeDetails));
        }
        return playable;
    }

    private static boolean challenge(final Document doc) {
        final String text = doc.text().toLowerCase(java.util.Locale.ROOT);
        return text.contains("verify you are human")
                || text.contains("checking your browser")
                || text.contains("cloudflare challenge");
    }

    private static boolean validFrame(final String url) {
        if (!SupjavProviderExtractor.safePublicHttps(url)) return false;
        try {
            final String path = URI.create(url).getPath().toLowerCase(java.util.Locale.ROOT);
            return path.matches("^/(?:e|embed|player|v|supjav\\.php)(?:/.*)?$")
                    || path.matches("^/(?:e|embed|player|v)/.+");
        } catch (final Exception ignored) {
            return false;
        }
    }
}
