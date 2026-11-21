package org.schabi.newpipe.fragments.list.kiosk;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.kiosk.SiteBrowseCatalog;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared native browse bar for services which expose kiosk destinations.
 * Unlike the MissAV-specific website category component, this never attempts
 * to open website menu URLs that their extractors cannot currently handle.
 */
final class SiteBrowseHeader {
    private final View root;
    private final LinearLayout tabs;
    private final Consumer<SiteBrowseCatalog.Entry> navigate;
    private final String activeId;
    private final int padding;

    SiteBrowseHeader(@NonNull final View view,
                     @NonNull final List<SiteBrowseCatalog.Entry> entries,
                     @NonNull final String selectedKioskId,
                     @NonNull final Consumer<SiteBrowseCatalog.Entry> navigation) {
        root = view;
        tabs = view.findViewById(R.id.site_browse_tabs);
        navigate = navigation;
        activeId = selectedKioskId;
        padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                12, view.getResources().getDisplayMetrics());
        render(entries);
    }

    private void render(final List<SiteBrowseCatalog.Entry> entries) {
        tabs.removeAllViews();
        // The site might only expose one list; do not waste vertical space
        // with a row of buttons which cannot change the current page.
        if (entries.size() < 2) {
            final View header = root.findViewById(R.id.site_browse_header);
            header.setVisibility(View.GONE);
            return;
        }
        root.findViewById(R.id.site_browse_header).setVisibility(View.VISIBLE);
        for (final SiteBrowseCatalog.Entry item : entries) {
            final TextView tab = new TextView(root.getContext());
            tab.setText(item.getLabel());
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tab.setPadding(padding, padding, padding, padding);
            tab.setSingleLine(true);
            tab.setClickable(true);
            tab.setFocusable(true);
            final boolean selected = item.getId().equals(activeId);
            tab.setSelected(selected);
            tab.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            tab.setAlpha(selected ? 1.0f : 0.77f);
            final TypedValue selectable = new TypedValue();
            root.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, selectable, true);
            if (selectable.resourceId != 0) {
                tab.setBackgroundResource(selectable.resourceId);
            }
            tabs.addView(tab, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tab.setOnClickListener(v -> {
                if (!selected && navigate != null) {
                    navigate.accept(item);
                }
            });
        }
    }
}
