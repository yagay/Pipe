package org.schabi.newpipe.extractor.kiosk;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure URL state operations for native website browsing.
 *
 * Selecting a sort must preserve the current filters; selecting a filter must
 * preserve the current sort. Raw encoded query values remain untouched.
 */
public final class BrowseUrlState {
    public enum OptionGroup {
        SORT("sort", "sort_by", "order", "o"),
        FILTER("filter", "filters", "duration", "quality", "hd",
                "type", "orientation", "production");

        private final List<String> keys;

        OptionGroup(final String... keys) {
            this.keys = Collections.unmodifiableList(Arrays.asList(keys));
        }

        boolean contains(final String key) {
            return keys.contains(key.toLowerCase(Locale.ROOT));
        }
    }

    private BrowseUrlState() {
    }

    public static boolean samePath(final String left, final String right) {
        try {
            final URI a = new URI(left);
            final URI b = new URI(right);
            return a.getScheme() != null && b.getScheme() != null
                    && a.getScheme().equalsIgnoreCase(b.getScheme())
                    && a.getHost() != null && b.getHost() != null
                    && a.getHost().equalsIgnoreCase(b.getHost())
                    && a.getPort() == b.getPort()
                    && java.util.Objects.equals(a.getRawPath(), b.getRawPath());
        } catch (final URISyntaxException ignored) {
            return false;
        }
    }

    /**
     * Carry active filter and sort parameters to a pagination link that omitted
     * them. Never carry options to another host or an unrelated category path.
     */
    public static String carryOptionsToNextPage(final String current, final String next) {
        try {
            final URI a = new URI(current);
            final URI b = new URI(next);
            if (a.getScheme() == null || b.getScheme() == null
                    || !a.getScheme().equalsIgnoreCase(b.getScheme())
                    || a.getHost() == null || b.getHost() == null
                    || !a.getHost().equalsIgnoreCase(b.getHost())
                    || a.getPort() != b.getPort()
                    || !basePaginationPath(a.getPath()).equals(basePaginationPath(b.getPath()))) {
                return next;
            }
            final List<String> result = new ArrayList<>(parts(b.getRawQuery()));
            for (final OptionGroup group : OptionGroup.values()) {
                if (groupParts(b.getRawQuery(), group).isEmpty()) {
                    result.addAll(groupParts(a.getRawQuery(), group));
                }
            }
            final StringBuilder url = new StringBuilder();
            url.append(b.getScheme()).append("://").append(b.getRawAuthority())
                    .append(b.getRawPath());
            if (!result.isEmpty()) {
                url.append('?').append(String.join("&", result));
            }
            return url.toString();
        } catch (final URISyntaxException ignored) {
            return next;
        }
    }

    private static String basePaginationPath(final String path) {
        if (path == null) {
            return "";
        }
        return path.replaceFirst("(?i)/page/[0-9]+/?$", "").replaceFirst("/+$", "");
    }

    /**
     * Compare category identity while ignoring sort, filter and page parameters.
     * Query-based categories (for example /video?c=3) must remain distinct.
     */
    public static boolean sameCategory(final String current, final String candidate) {
        if (!samePath(current, candidate)) {
            return false;
        }
        try {
            final List<String> a = categoryParts(new URI(current).getRawQuery());
            final List<String> b = categoryParts(new URI(candidate).getRawQuery());
            Collections.sort(a);
            Collections.sort(b);
            return a.equals(b);
        } catch (final URISyntaxException ignored) {
            return false;
        }
    }

    private static List<String> categoryParts(final String query) {
        final List<String> result = new ArrayList<>();
        for (final String part : parts(query)) {
            final String name = key(part).toLowerCase(Locale.ROOT);
            if (!OptionGroup.FILTER.contains(name) && !OptionGroup.SORT.contains(name)
                    && !name.equals("page") && !name.equals("p")
                    && !name.equals("from")) {
                result.add(part);
            }
        }
        return result;
    }

    public static boolean hasOption(final String url, final OptionGroup group) {
        try {
            for (final String part : parts(new URI(url).getRawQuery())) {
                if (group.contains(key(part))) {
                    return true;
                }
            }
        } catch (final URISyntaxException ignored) {
            // Invalid links must not be treated as active options.
        }
        return false;
    }

    /**
     * Apply the chosen option to the current page without discarding parameters
     * belonging to other groups. An option without group keys resets only that group.
     */
    public static String applyOption(final String current, final String selected,
                                     final OptionGroup group) {
        try {
            final URI base = new URI(current);
            final URI choice = new URI(selected);
            if (!samePath(current, selected)) {
                throw new IllegalArgumentException("Option belongs to a different page");
            }
            final List<String> query = new ArrayList<>();
            for (final String part : parts(base.getRawQuery())) {
                if (!group.contains(key(part))) {
                    query.add(part);
                }
            }
            for (final String part : parts(choice.getRawQuery())) {
                if (group.contains(key(part))) {
                    query.add(part);
                }
            }
            final StringBuilder result = new StringBuilder();
            result.append(base.getScheme()).append("://").append(base.getRawAuthority())
                    .append(base.getRawPath());
            if (!query.isEmpty()) {
                result.append('?').append(String.join("&", query));
            }
            if (base.getRawFragment() != null) {
                result.append('#').append(base.getRawFragment());
            }
            return result.toString();
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid browse URL", e);
        }
    }

    /** True if applying this option would not change the current browser state. */
    public static boolean selected(final String current, final String option,
                                   final OptionGroup group) {
        if (!samePath(current, option)) {
            return false;
        }
        try {
            final List<String> active = groupParts(new URI(current).getRawQuery(), group);
            final List<String> candidate = groupParts(new URI(option).getRawQuery(), group);
            Collections.sort(active);
            Collections.sort(candidate);
            return active.equals(candidate);
        } catch (final URISyntaxException ignored) {
            return false;
        }
    }

    private static List<String> groupParts(final String query, final OptionGroup group) {
        final List<String> result = new ArrayList<>();
        for (final String part : parts(query)) {
            if (group.contains(key(part))) {
                result.add(part);
            }
        }
        return result;
    }

    private static List<String> parts(final String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>();
        for (final String part : query.split("&")) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    private static String key(final String part) {
        final int equals = part.indexOf('=');
        return equals < 0 ? part : part.substring(0, equals);
    }
}
