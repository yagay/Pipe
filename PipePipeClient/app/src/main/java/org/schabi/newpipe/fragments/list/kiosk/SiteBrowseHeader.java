package org.schabi.newpipe.fragments.list.kiosk;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.kiosk.SiteBrowseCatalog;
import org.schabi.newpipe.extractor.StreamingService;

import java.util.List;
import java.util.Collections;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.util.function.Consumer;

/**
 * Shared native browse bar for services which expose kiosk destinations.
 * Unlike the MissAV-specific website category component, this never attempts
 * to open website menu URLs that their extractors cannot currently handle.
 */
final class SiteBrowseHeader {
    private final View root;
    private final LinearLayout tabs;
    private final LinearLayout categoriesToolbar;
    private final CompositeDisposable disposable = new CompositeDisposable();
    private final Consumer<SiteBrowseCatalog.Entry> navigate;
    private final String activeId;
    private final StreamingService service;
    private final String pageUrl;
    private boolean categoryFailed;
    private List<SiteBrowseCatalog.Entry> filters = Collections.emptyList();
    private final int padding;
    private List<SiteBrowseCatalog.Entry> categories = Collections.emptyList();
    private List<SiteBrowseCatalog.Entry> sorts = Collections.emptyList();

    SiteBrowseHeader(@NonNull final View view,
                     @NonNull final List<SiteBrowseCatalog.Entry> entries,
                     @NonNull final StreamingService service,
                     @NonNull final String currentUrl,
                     @NonNull final String selectedKioskId,
                     @NonNull final Consumer<SiteBrowseCatalog.Entry> navigation) {
        root = view;
        tabs = view.findViewById(R.id.site_browse_tabs);
        categoriesToolbar = view.findViewById(R.id.site_category_toolbar);
        navigate = navigation;
        activeId = selectedKioskId;
        this.service = service;
        pageUrl = currentUrl;
        padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                12, view.getResources().getDisplayMetrics());
        render(entries);
        if (SiteBrowseCatalog.supportsHtmlCategories(service)) {
            loadCategories(service);
            if (selectedKioskId.startsWith("category:")) {
                loadSorts(service, currentUrl);
                loadFilters(service, currentUrl);
            }
        }
    }

    private void loadCategories(final StreamingService service) {
        disposable.add(Single.fromCallable(() -> SiteBrowseCatalog.getWebsiteCategories(service))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (root.isAttachedToWindow()) {
                        categories = result;
                        categoryFailed = false;
                        renderCategories();
                    }
                }, error -> {
                    android.util.Log.w("SiteBrowse", "Category list unavailable", error);
                    if (root.isAttachedToWindow()) {
                        categoryFailed = true;
                        renderCategories();
                    }
                }));
    }

    private void loadSorts(final StreamingService service, final String pageUrl) {
        disposable.add(Single.fromCallable(() ->
                        SiteBrowseCatalog.getWebsiteSorts(service, pageUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (root.isAttachedToWindow() && !result.isEmpty()) {
                        sorts = result;
                        renderCategories();
                    }
                }, error -> android.util.Log.w("SiteBrowse", "Sort list unavailable", error)));
    }

    private void loadFilters(final StreamingService service, final String pageUrl) {
        disposable.add(Single.fromCallable(() ->
                        SiteBrowseCatalog.getWebsiteFilters(service, pageUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (root.isAttachedToWindow()) {
                        filters = result;
                        renderCategories();
                    }
                }, error -> android.util.Log.w("SiteBrowse", "Filter list unavailable", error)));
    }

    private TextView makeOptionButton(final String label) {
        final TextView view = new TextView(root.getContext());
        view.setText(label);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setPadding(padding, padding / 2, padding, padding / 2);
        view.setClickable(true);
        return view;
    }

    private void showOptions(final String title, final List<SiteBrowseCatalog.Entry> items) {
        final String[] labels = new String[items.size()];
        int selected = -1;
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i).getLabel();
            if (items.get(i).getId().equals(activeId)
                    || (items.get(i).getId().startsWith("filter:")
                        && activeId.equals("category:" + items.get(i).getUrl()))
                    || (items.get(i).getId().startsWith("sort:")
                        && activeId.equals("category:" + items.get(i).getUrl()))) {
                selected = i;
            }
        }
        new AlertDialog.Builder(root.getContext()).setTitle(title)
                .setSingleChoiceItems(labels, selected, (dialog, index) -> {
                    dialog.dismiss();
                    if (!items.get(index).getId().equals(activeId)) {
                        navigate.accept(items.get(index));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void renderCategories() {
        categoriesToolbar.removeAllViews();
        if (!categories.isEmpty()) {
            final TextView choose = new TextView(root.getContext());
            choose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            choose.setPadding(padding, padding / 2, padding, padding / 2);
            String selectedName = "Categories ▾";
            for (final SiteBrowseCatalog.Entry item : categories) {
                if (activeId.startsWith("category:")
                        && item.getUrl().split("[?#]", 2)[0]
                            .equals(activeId.substring("category:".length()).split("[?#]", 2)[0])) {
                    selectedName = item.getLabel() + " ▾";
                    break;
                }
            }
            choose.setText(selectedName);
            choose.setTypeface(null, Typeface.BOLD);
            choose.setClickable(true);
            categoriesToolbar.addView(choose);
            categoriesToolbar.setVisibility(View.VISIBLE);
            root.findViewById(R.id.site_browse_container).setVisibility(View.VISIBLE);
            choose.setOnClickListener(v -> {
                if (!categories.isEmpty()) {
                    showOptions("Categories", categories);
                }
            });
        }
        if (categoryFailed) {
            final TextView retry = makeOptionButton("Retry categories");
            categoriesToolbar.addView(retry);
            retry.setOnClickListener(v -> {
                categoryFailed = false;
                SiteBrowseCatalog.invalidateCategories(service);
                loadCategories(service);
            });
        }
        if (!sorts.isEmpty()) {
            String sortTitle = "Sort ▾";
            for (final SiteBrowseCatalog.Entry sort : sorts) {
                if (activeId.equals("category:" + sort.getUrl())) {
                    sortTitle = sort.getLabel() + " ▾";
                    break;
                }
            }
            final TextView sortButton = makeOptionButton(sortTitle);
            categoriesToolbar.addView(sortButton);
            sortButton.setOnClickListener(v -> showOptions("Sort", sorts));
        }
        if (!filters.isEmpty()) {
            final TextView filterButton = makeOptionButton("Filter ▾");
            categoriesToolbar.addView(filterButton);
            filterButton.setOnClickListener(v -> showOptions("Filter", filters));
        }
        if (categories.isEmpty() && sorts.isEmpty() && filters.isEmpty() && !categoryFailed) {
            categoriesToolbar.setVisibility(View.GONE);
        }
    }

    void dispose() {
        disposable.dispose();
    }

    private void render(final List<SiteBrowseCatalog.Entry> entries) {
        tabs.removeAllViews();
        // The site might only expose one list; do not waste vertical space
        // with a row of buttons which cannot change the current page.
        if (entries.size() < 2) {
            root.findViewById(R.id.site_browse_header).setVisibility(View.GONE);
            return;
        }
        root.findViewById(R.id.site_browse_header).setVisibility(View.VISIBLE);
        root.findViewById(R.id.site_browse_container).setVisibility(View.VISIBLE);
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
