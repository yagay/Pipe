package org.schabi.newpipe.extractor.services.missav;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.search.filter.FilterItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MissAV navigation catalog. Items are normal PipePipe kiosks, not embedded web pages.
 * Only video-list categories are registered here; directory pages require a different
 * InfoItem type (actress, maker and genre listings must not be treated as videos).
 */
public final class MissAvKioskLinkHandlerFactory extends ListLinkHandlerFactory {
    private static final MissAvKioskLinkHandlerFactory INSTANCE =
            new MissAvKioskLinkHandlerFactory();
    private static final LinkedHashMap<String, String> PATHS = new LinkedHashMap<>();
    private static final LinkedHashMap<String, String> TITLES = new LinkedHashMap<>();

    static {
        add("latest", "Watch JAV · Recent update", "new");
        add("recommended", "Watch JAV · Recommended", "");
        add("popular", "Watch JAV · Popular", "popular");
        add("release", "Watch JAV · New releases", "release");
        add("uncensored-leak", "Watch JAV · Uncensored leak", "uncensored-leak");
        add("english-subtitle", "Watch JAV · English subtitle", "english-subtitle");
        add("vr", "Watch JAV · VR", "genres/VR");
        add("today-hot", "Watch JAV · Most viewed today", "today-hot");
        add("weekly-hot", "Watch JAV · Most viewed this week", "weekly-hot");
        add("monthly-hot", "Watch JAV · Most viewed this month", "monthly-hot");

        add("siro", "Amateur · SIRO", "siro");
        add("luxu", "Amateur · LUXU", "luxu");
        add("gana", "Amateur · GANA", "gana");
        add("maan", "Amateur · PRESTIGE PREMIUM", "maan");
        add("scute", "Amateur · S-CUTE", "scute");
        add("ara", "Amateur · ARA", "ara");

        add("fc2", "Uncensored · FC2", "fc2");
        add("heyzo", "Uncensored · HEYZO", "heyzo");
        add("tokyohot", "Uncensored · Tokyo Hot", "tokyohot");
        add("1pondo", "Uncensored · 1pondo", "1pondo");
        add("caribbeancom", "Uncensored · Caribbeancom", "caribbeancom");
        add("caribbeancompr", "Uncensored · Caribbeancompr", "caribbeancompr");
        add("10musume", "Uncensored · 10musume", "10musume");
        add("pacopacomama", "Uncensored · pacopacomama", "pacopacomama");
        add("gachinco", "Uncensored · Gachinco", "gachinco");
        add("xxx-av", "Uncensored · XXX-AV", "xxx-av");
        add("married-slash", "Uncensored · Married Slash", "married-slash");
        add("4610", "Uncensored · Naughty 4610", "4610");
        add("0930", "Uncensored · Naughty 0930", "0930");

        add("madou", "Asia AV · Madou", "madou");
        add("twav", "Asia AV · TWAV", "twav");
        add("furuke", "Asia AV · Furuke", "furuke");
        add("korean-live", "Asia AV · Korean Live", "korean-live");
        add("chinese-live", "Asia AV · Chinese Live", "chinese-live");
    }

    private static void add(final String id, final String title, final String path) {
        PATHS.put(id, path);
        TITLES.put(id, title);
    }

    public static MissAvKioskLinkHandlerFactory getInstance() {
        return INSTANCE;
    }

    public static List<String> getCategoryIds() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(PATHS.keySet()));
    }

    public static String getCategoryName(final String id) {
        final String result = TITLES.get(id);
        return result == null ? id : result;
    }

    private MissAvKioskLinkHandlerFactory() {
    }

    @Override
    public String getId(final String url) {
        if (url != null) {
            final String normalized = url.split("[?#]", 2)[0].toLowerCase(Locale.ROOT);
            for (final Map.Entry<String, String> entry : PATHS.entrySet()) {
                final String path = entry.getValue();
                if (!path.isEmpty() && normalized.endsWith("/" + path.toLowerCase(Locale.ROOT))) {
                    return entry.getKey();
                }
            }
        }
        return "latest";
    }

    @Override
    public String getUrl(final String id, final List<FilterItem> contentFilter,
                         final List<FilterItem> sortFilter) throws ParsingException {
        if (!PATHS.containsKey(id)) {
            throw new ParsingException("Unknown MissAV category: " + id);
        }
        final String path = PATHS.get(id);
        return MissAvParsingHelper.BASE_URL + "/" + MissAvParsingHelper.DEFAULT_LANGUAGE
                + (path.isEmpty() ? "" : "/" + path);
    }

    @Override
    public boolean onAcceptUrl(final String url) {
        return url != null && (url.startsWith(MissAvParsingHelper.BASE_URL + "/")
                || url.startsWith("https://missav.ai/"));
    }
}
