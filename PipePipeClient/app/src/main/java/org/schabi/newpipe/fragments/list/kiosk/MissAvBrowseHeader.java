package org.schabi.newpipe.fragments.list.kiosk;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.services.missav.MissAvBrowseCatalog;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** MissAV-only presentation. Does not alter PipePipe's global navigation or player. */
final class MissAvBrowseHeader {
    private final CompositeDisposable subscriptions = new CompositeDisposable();
    private final View root;
    private final String pageUrl;
    private final Consumer<MissAvBrowseCatalog.Entry> navigate;
    private final LinearLayout channels;
    private final LinearLayout toolbar;
    private final ScrollView panelScroll;
    private final GridLayout panel;
    private Map<String, List<MissAvBrowseCatalog.Entry>> entries =
            Collections.emptyMap();
    private int expandedGroup = -1;
    private List<MissAvBrowseCatalog.Entry> currentFilters = Collections.emptyList();
    private List<MissAvBrowseCatalog.Entry> currentSorts = Collections.emptyList();
    private TextView[] headings = new TextView[MissAvBrowseCatalog.GROUPS.length];
    private final int padding;

    MissAvBrowseHeader(@NonNull final View rootView, @NonNull final String url,
                       @NonNull final Consumer<MissAvBrowseCatalog.Entry> onNavigate) {
        root = rootView;
        pageUrl = url;
        navigate = onNavigate;
        channels = root.findViewById(R.id.missav_browse_channels);
        toolbar = root.findViewById(R.id.missav_browse_toolbar);
        panelScroll = root.findViewById(R.id.missav_browse_panel_scroll);
        panel = root.findViewById(R.id.missav_browse_panel);
        padding = dp(10);
        panelScroll.setVisibility(View.GONE);
        renderChannels();
        renderToolbar("Categories", Collections.emptyList(), Collections.emptyList());
        subscriptions.add(Single.fromCallable(MissAvBrowseCatalog::getCategories)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(categories -> {
                    if (!isAttached()) {
                        return;
                    }
                    entries = categories;
                    renderChannels();
                    renderToolbar(currentCategoryLabel(), currentFilters, currentSorts);
                }, error -> android.util.Log.w("MissAV", "Category navigation unavailable", error)));

        subscriptions.add(Single.fromCallable(() ->
                        MissAvBrowseCatalog.getPageOptions(pageUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(options -> {
                    if (isAttached()) {
                        currentFilters = options.filters;
                        currentSorts = options.sorts;
                        renderToolbar(currentCategoryLabel(), currentFilters, currentSorts);
                    }
                }, error -> android.util.Log.w("MissAV", "Page options unavailable", error)));
    }

    private boolean isAttached() {
        return root.isAttachedToWindow();
    }

    private int dp(final int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                root.getResources().getDisplayMetrics());
    }

    private TextView textButton(final String text) {
        final TextView button = new TextView(root.getContext());
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setPadding(padding, padding, padding, padding);
        button.setClickable(true);
        button.setFocusable(true);
        final TypedValue attribute = new TypedValue();
        root.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, attribute, true);
        if (attribute.resourceId != 0) {
            button.setBackgroundResource(attribute.resourceId);
        }
        return button;
    }

    private String currentCategoryLabel() {
        for (final List<MissAvBrowseCatalog.Entry> group : entries.values()) {
            for (final MissAvBrowseCatalog.Entry item : group) {
                if (sameCategory(item.url, pageUrl)) {
                    return item.label;
                }
            }
        }
        return "Categories";
    }

    private static boolean sameCategory(final String a, final String b) {
        return a.split("[?#]", 2)[0].equalsIgnoreCase(b.split("[?#]", 2)[0]);
    }

    private void renderChannels() {
        channels.removeAllViews();
        headings = new TextView[MissAvBrowseCatalog.GROUPS.length];
        for (int index = 0; index < MissAvBrowseCatalog.GROUPS.length; index++) {
            final int selectedIndex = index;
            final String name = MissAvBrowseCatalog.GROUPS[index][0];
            final TextView heading = textButton(name
                    + (expandedGroup == index ? " ▴" : " ▾"));
            heading.setTypeface(null, expandedGroup == index
                    ? Typeface.BOLD : Typeface.NORMAL);
            headings[index] = heading;
            channels.addView(heading);
            heading.setOnClickListener(view -> {
                expandedGroup = expandedGroup == selectedIndex ? -1 : selectedIndex;
                renderChannels();
                renderExpandedGroup();
            });
        }
    }

    private void renderExpandedGroup() {
        panel.removeAllViews();
        if (expandedGroup < 0) {
            panelScroll.setVisibility(View.GONE);
            return;
        }
        final String name = MissAvBrowseCatalog.GROUPS[expandedGroup][0];
        final List<MissAvBrowseCatalog.Entry> items = entries.get(name);
        if (items == null || items.isEmpty()) {
            final TextView message = textButton("No categories available");
            panel.addView(message);
        } else {
            for (final MissAvBrowseCatalog.Entry item : items) {
                final TextView button = textButton(item.label);
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                if (sameCategory(pageUrl, item.url)) {
                    button.setTypeface(null, Typeface.BOLD);
                }
                final GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 0;
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
                button.setLayoutParams(params);
                panel.addView(button);
                button.setOnClickListener(v -> {
                    expandedGroup = -1;
                    panelScroll.setVisibility(View.GONE);
                    navigate.accept(item);
                });
            }
        }
        final int itemCount = items == null ? 1 : items.size();
        final ViewGroup.LayoutParams params = panelScroll.getLayoutParams();
        params.height = Math.min(dp(200), dp(48) * Math.max(1, (itemCount + 2) / 3));
        panelScroll.setLayoutParams(params);
        panelScroll.setVisibility(View.VISIBLE);
    }

    private void renderToolbar(final String category,
                               final List<MissAvBrowseCatalog.Entry> filters,
                               final List<MissAvBrowseCatalog.Entry> sorts) {
        toolbar.removeAllViews();
        final TextView name = textButton(category);
        name.setTypeface(null, Typeface.BOLD);
        final LinearLayout.LayoutParams nameParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        toolbar.addView(name, nameParams);
        if (!filters.isEmpty()) {
            final TextView filter = textButton(activeLabel("Filter", filters) + " ▾");
            toolbar.addView(filter);
            filter.setOnClickListener(v -> showOptions("Filter", filters));
        }
        if (!sorts.isEmpty()) {
            final TextView sort = textButton(activeLabel("Sort", sorts) + " ▾");
            toolbar.addView(sort);
            sort.setOnClickListener(v -> showOptions("Sort", sorts));
        }
    }

    private String activeLabel(final String prefix, final List<MissAvBrowseCatalog.Entry> items) {
        for (final MissAvBrowseCatalog.Entry entry : items) {
            if (entry.selected) {
                return entry.label;
            }
        }
        return prefix;
    }

    private void showOptions(final String title, final List<MissAvBrowseCatalog.Entry> options) {
        if (!isAttached()) {
            return;
        }
        final CharSequence[] labels = new CharSequence[options.size()];
        int selected = -1;
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).label;
            if (options.get(i).selected) {
                selected = i;
            }
        }
        new AlertDialog.Builder(root.getContext())
                .setTitle(title)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    navigate.accept(options.get(which));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    void dispose() {
        subscriptions.dispose();
    }
}
