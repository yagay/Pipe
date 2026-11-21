package org.schabi.newpipe.extractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Navigation-only grouping. Extended providers still use the same StreamingService
 * interfaces and playback pipeline, but remain isolated from the built-in sites.
 */
public final class ServiceCategoryRegistry {
    public enum Category { NATIVE, EXTENDED }

    private ServiceCategoryRegistry() { }

    public static Category categoryOf(final StreamingService service) {
        return service == ServiceList.Supjav ? Category.EXTENDED : Category.NATIVE;
    }

    public static List<StreamingService> servicesIn(final Category category) {
        final List<StreamingService> result = new ArrayList<>();
        for (final StreamingService service : ServiceList.all()) {
            if (categoryOf(service) == category) {
                result.add(service);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
