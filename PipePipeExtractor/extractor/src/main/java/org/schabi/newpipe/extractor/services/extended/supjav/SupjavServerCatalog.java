package org.schabi.newpipe.extractor.services.extended.supjav;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supjav's first-party video/server manifest; no media network requests here.
 * Safe for the detail UI to display even if all native video hosts are blocked.
 * A separate selected-server resolver performs the expensive network work.
 */
public final class SupjavServerCatalog {
    public static final class Entry {
        public final int index;
        public final String part;
        public final String provider;
        public final String link;
        public final String page;
        Entry(final int index, final String part, final String provider,
              final String link, final String page) {
            this.index = index;
            this.part = part;
            this.provider = provider;
            this.link = link;
            this.page = page;
        }
        public String label() {
            return ("Main".equals(part) ? "" : part + " · ") + provider;
        }
    }

    private static final class Snapshot {
        final List<Entry> entries;
        final long created;
        Snapshot(final List<Entry> entries) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            created = System.currentTimeMillis();
        }
    }

    private static final long TTL_MS = 10 * 60 * 1000L;
    private static final Map<String, Snapshot> CACHE = new ConcurrentHashMap<>();
    private static final Pattern PART = Pattern.compile(
            "(?i)(?:^|[^a-z0-9])(?:cd|disc|part)[ _-]*0?([1-9][0-9]?)");
    private static final String SELECTORS =
            ".btn-server[data-link], .btnst a[data-link], .btns a[data-link], "
            + "#server-list [data-link], .server-list [data-link], "
            + ".servers [data-link], [data-server][data-link]";

    private SupjavServerCatalog() { }

    public static String pageUrl(final String url) {
        if (url == null) return "";
        final int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    public static int selectedIndex(final String url) {
        if (url == null) return -1;
        final Matcher selected = Pattern.compile("(?:[?&])pipe_server=([0-9]{1,2})(?:&|$)")
                .matcher(url);
        if (!selected.find()) return -1;
        try {
            final int i = Integer.parseInt(selected.group(1));
            return i >= 0 && i < 80 ? i : -1;
        } catch (final NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * Prefer VAS within the requested CD, then another VAS entry. If a video
     * does not offer VAS, preserve the site's first actual server rather than
     * fabricating a line or trying a different CD first.
     */
    public static int preferredIndex(final List<Entry> entries, final String part) {
        if (entries == null || entries.isEmpty()) return -1;
        final String preferredPart = part == null ? entries.get(0).part : part;
        for (final Entry entry : entries) {
            if (preferredPart.equals(entry.part) && "VAS".equalsIgnoreCase(entry.provider)) {
                return entry.index;
            }
        }
        for (final Entry entry : entries) {
            if ("VAS".equalsIgnoreCase(entry.provider)) return entry.index;
        }
        for (final Entry entry : entries) {
            if (preferredPart.equals(entry.part)) return entry.index;
        }
        return entries.get(0).index;
    }

    /** Identify a selected provider from the first-party detail snapshot without network I/O. */
    public static boolean selectedProviderIs(final String selectedUrl, final String provider) {
        final int index = selectedIndex(selectedUrl);
        final Snapshot snapshot = CACHE.get(pageUrl(selectedUrl));
        return index >= 0 && snapshot != null && index < snapshot.entries.size()
                && provider.equalsIgnoreCase(snapshot.entries.get(index).provider);
    }

    public static String selectedUrl(final String detailUrl, final int index) {
        if (index < 0 || index >= 80) throw new IllegalArgumentException("Invalid server index");
        return pageUrl(detailUrl) + "?pipe_server=" + index;
    }

    public static void record(final String url, final Document document) {
        if (CACHE.size() > 90) CACHE.clear();
        CACHE.put(pageUrl(url), new Snapshot(parse(document, pageUrl(url))));
    }

    public static List<Entry> forPage(final String url) throws IOException {
        final String key = pageUrl(url);
        final Snapshot cached = CACHE.get(key);
        if (cached != null && System.currentTimeMillis() - cached.created < TTL_MS) {
            return cached.entries;
        }
        try {
            final Response result = NewPipe.getDownloader().get(key);
            if (result.responseCode() != 200) {
                throw new IOException("Supjav detail HTTP " + result.responseCode());
            }
            final Document document = org.jsoup.Jsoup.parse(result.responseBody(), key);
            record(key, document);
            return CACHE.get(key).entries;
        } catch (final org.schabi.newpipe.extractor.exceptions.ReCaptchaException e) {
            throw new IOException("Supjav verification required", e);
        }
    }

    public static List<Entry> parse(final Document detail, final String pageUrl) {
        final List<Element> buttons = detail.select(SELECTORS);
        final List<String> providers = new ArrayList<>();
        final List<String> links = new ArrayList<>();
        final List<String> explicitParts = new ArrayList<>();
        final Set<String> unique = new LinkedHashSet<>();
        for (final Element button : buttons) {
            final String raw = button.attr("data-link").trim();
            if (raw.isEmpty() || raw.length() > 2048 || !unique.add(raw)) continue;
            final String value = button.text().trim().isEmpty()
                    ? button.attr("data-server").trim() : button.text().trim();
            final String normalized = value.replaceAll(
                    "(?i)\\b(?:CD|DISC|PART)[ _-]*[0-9]+\\b", "").trim()
                    .split("\\s+")[0].toUpperCase(Locale.ROOT);
            providers.add(normalized.isEmpty() ? "Player" : normalized);
            links.add(raw);
            explicitParts.add(findPart(button));
            if (links.size() >= 80) break;
        }
        if (links.isEmpty()) {
            for (final Element frame : detail.select(
                    "iframe#video[src], #player-wrap iframe[src], iframe#player[src]")) {
                final String link = frame.absUrl("src");
                if (link.startsWith("https://") && unique.add(link)) {
                    providers.add("Player");
                    links.add(link);
                    explicitParts.add(null);
                }
            }
        }

        // Infer CDs only for exact repeated sets of known, unique providers and tokens.
        int groupSize = 0;
        final Set<String> known = new HashSet<>(java.util.Arrays.asList(
                "EVS", "FST", "VAS", "VOE", "LUC", "TV", "ST",
                "STREAMTAPE", "STPLAYER", "FAST", "FC2STREAM", "FPLAYER", "FAV", "FAVJAV"));
        if (explicitParts.stream().allMatch(p -> p == null)
                && new HashSet<>(links).size() == links.size()
                && providers.stream().allMatch(known::contains)) {
            for (int groups = 2; groups <= 10; groups++) {
                if (providers.size() < 6 || providers.size() % groups != 0) continue;
                final int size = providers.size() / groups;
                if (size < 3 || size > 8) continue;
                final Set<String> template = new LinkedHashSet<>(providers.subList(0, size));
                if (template.size() != size) continue;
                boolean matches = true;
                for (int n = 1; n < groups; n++) {
                    final Set<String> current = new LinkedHashSet<>(
                            providers.subList(n * size, (n + 1) * size));
                    if (!template.equals(current)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    groupSize = size;
                    break;
                }
            }
        }
        final List<Entry> entries = new ArrayList<>();
        for (int n = 0; n < links.size(); n++) {
            final String part = explicitParts.get(n) != null ? explicitParts.get(n)
                    : groupSize > 0 ? "CD" + (1 + n / groupSize) : "Main";
            entries.add(new Entry(n, part, providers.get(n), links.get(n), pageUrl));
        }
        return Collections.unmodifiableList(entries);
    }

    private static String findPart(final Element button) {
        final List<String> texts = new ArrayList<>();
        texts.add(button.attr("data-part"));
        texts.add(button.attr("data-cd"));
        texts.add(button.attr("data-disc"));
        texts.add(button.attr("title"));
        texts.add(button.text());
        Element parent = button.parent();
        for (int i = 0; i < 2 && parent != null; i++, parent = parent.parent()) {
            texts.add(parent.attr("data-part"));
            texts.add(parent.attr("data-cd"));
            texts.add(parent.attr("data-disc"));
            final Element before = parent.previousElementSibling();
            if (before != null && before.text().length() < 24) texts.add(before.text());
        }
        for (final String raw : texts) {
            final Matcher match = PART.matcher(raw);
            if (match.find()) return "CD" + Integer.parseInt(match.group(1));
        }
        return null;
    }
}
