package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.app.AlertDialog;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.missav.MissAvService;
import org.schabi.newpipe.extractor.services.missav.MissAvKioskLinkHandlerFactory;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.Localization;

import io.reactivex.rxjava3.core.Single;

/**
 * Created by Christian Schabesberger on 23.09.17.
 * <p>
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * KioskFragment.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class KioskFragment extends BaseListInfoFragment<StreamInfoItem, KioskInfo> {
    private static final int CATEGORY_MENU_ID = 0x4D495353;
    private static final String[][] CATEGORY_GROUPS = {
        {"Watch JAV", "latest", "recommended", "popular", "release",
            "uncensored-leak", "english-subtitle", "vr", "today-hot", "weekly-hot", "monthly-hot"},
        {"Amateur", "siro", "luxu", "gana", "maan", "scute", "ara"},
        {"Uncensored", "fc2", "heyzo", "tokyohot", "1pondo", "caribbeancom",
            "caribbeancompr", "10musume", "pacopacomama", "gachinco",
            "xxx-av", "married-slash", "4610", "0930"},
        {"Asia AV", "madou", "twav", "furuke", "korean-live", "chinese-live"}
    };
    String kioskId = "";
    String kioskTranslatedName;
    ContentCountry contentCountry;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    public static KioskFragment getInstance(final int serviceId) throws ExtractionException {
        return getInstance(serviceId, NewPipe.getService(serviceId)
                .getKioskList().getDefaultKioskId());
    }

    public static KioskFragment getInstance(final int serviceId, final String kioskId)
            throws ExtractionException {
        final KioskFragment instance = new KioskFragment();
        final StreamingService service = NewPipe.getService(serviceId);
        final ListLinkHandlerFactory kioskLinkHandlerFactory = service.getKioskList()
                .getListLinkHandlerFactoryByType(kioskId);
        instance.setInitialData(serviceId,
                kioskLinkHandlerFactory.fromId(kioskId).getUrl(), kioskId);
        instance.kioskId = kioskId;
        return instance;
    }

    public KioskFragment() {
        super(UserAction.REQUESTED_KIOSK);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("kioskId", kioskId);
        outState.putSerializable("contentCountry", contentCountry);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        kioskId = savedInstanceState.getString("kioskId", "");
        contentCountry = (ContentCountry) savedInstanceState.getSerializable("contentCountry");
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        kioskTranslatedName = KioskTranslator.getTranslatedKioskName(kioskId, activity);
        name = kioskTranslatedName;
        contentCountry = Localization.getPreferredContentCountry(requireContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!Localization.getPreferredContentCountry(requireContext()).equals(contentCountry)) {
            reloadContent();
        }
        if (useAsFrontPage && activity != null) {
            try {
                setTitle(kioskTranslatedName);
            } catch (final Exception e) {
                showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR, "Setting kiosk title"));
            }
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_kiosk, container, false);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (isMissAv()) {
            menu.add(Menu.NONE, CATEGORY_MENU_ID, Menu.NONE, "Categories")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null && useAsFrontPage) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == CATEGORY_MENU_ID && isMissAv()) {
            showCategoryPicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isMissAv() {
        try {
            return NewPipe.getService(serviceId) instanceof MissAvService;
        } catch (final ExtractionException e) {
            return false;
        }
    }

    private void showCategoryPicker() {
        final android.content.Context context = requireContext();
        final ScrollView scroll = new ScrollView(context);
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        final int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        root.setPadding(pad, 0, pad, 0);
        scroll.addView(root);
        final LinearLayout[] sections = new LinearLayout[CATEGORY_GROUPS.length];
        final TextView[] headings = new TextView[CATEGORY_GROUPS.length];
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("MissAV Categories")
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        for (int i = 0; i < CATEGORY_GROUPS.length; i++) {
            final int groupIndex = i;
            final String[] group = CATEGORY_GROUPS[i];
            final TextView heading = new TextView(context);
            heading.setText("▸ " + group[0]);
            heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            heading.setPadding(0, pad, 0, pad);
            headings[i] = heading;
            root.addView(heading);
            final LinearLayout section = new LinearLayout(context);
            section.setOrientation(LinearLayout.VERTICAL);
            section.setPadding(pad, 0, 0, pad);
            section.setVisibility(View.GONE);
            sections[i] = section;
            root.addView(section);
            for (int n = 1; n < group.length; n++) {
                final String id = group[n];
                final TextView choice = new TextView(context);
                choice.setText(MissAvKioskLinkHandlerFactory.getCategoryName(id)
                        .replace(group[0] + " · ", ""));
                choice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                choice.setPadding(0, pad / 2, 0, pad / 2);
                section.addView(choice);
                choice.setOnClickListener(v -> {
                    dialog.dismiss();
                    try {
                        NavigationHelper.openKioskFragment(getFM(), serviceId, id);
                    } catch (final ExtractionException e) {
                        showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR,
                                "Opening MissAV category"));
                    }
                });
            }
            heading.setOnClickListener(v -> {
                final boolean shouldOpen = sections[groupIndex].getVisibility() == View.GONE;
                for (int j = 0; j < sections.length; j++) {
                    sections[j].setVisibility(View.GONE);
                    headings[j].setText("▸ " + CATEGORY_GROUPS[j][0]);
                }
                if (shouldOpen) {
                    sections[groupIndex].setVisibility(View.VISIBLE);
                    headings[groupIndex].setText("▾ " + group[0]);
                }
            });
        }
        dialog.show();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public Single<KioskInfo> loadResult(final boolean forceReload) {
        contentCountry = Localization.getPreferredContentCountry(requireContext());
        return ExtractorHelper.getKioskInfo(serviceId, url, forceReload);
    }

    @Override
    public Single<ListExtractor.InfoItemsPage<StreamInfoItem>> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreKioskItems(serviceId, url, currentNextPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final KioskInfo result) {
        super.handleResult(result);

        name = kioskTranslatedName;
        setTitle(kioskTranslatedName);
    }
}
