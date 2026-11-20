package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.HorizontalScrollView;
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
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null && useAsFrontPage) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        setupMissAvTopCategories(rootView);
    }

    private boolean isMissAv() {
        try {
            return NewPipe.getService(serviceId) instanceof MissAvService;
        } catch (final ExtractionException e) {
            return false;
        }
    }

    private void setupMissAvTopCategories(@NonNull final View rootView) {
        final View header = rootView.findViewById(R.id.missav_category_header);
        if (header == null) {
            return;
        }
        header.setVisibility(isMissAv() ? View.VISIBLE : View.GONE);
        if (!isMissAv()) {
            return;
        }
        final LinearLayout primary = rootView.findViewById(R.id.missav_primary_categories);
        final LinearLayout secondary = rootView.findViewById(R.id.missav_secondary_categories);
        final HorizontalScrollView secondaryScroll =
                rootView.findViewById(R.id.missav_secondary_scroller);
        primary.removeAllViews();
        secondary.removeAllViews();
        secondaryScroll.setVisibility(View.GONE); // Initially collapsed.
        final TextView[] headings = new TextView[CATEGORY_GROUPS.length];
        final int[] expandedIndex = {-1};
        final int pad = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        for (int i = 0; i < CATEGORY_GROUPS.length; i++) {
            final int index = i;
            final String[] group = CATEGORY_GROUPS[i];
            final TextView heading = new TextView(requireContext());
            heading.setText(group[0] + " ▾");
            heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            heading.setPadding(pad, pad, pad, pad);
            heading.setClickable(true);
            heading.setFocusable(true);
            headings[i] = heading;
            primary.addView(heading);
            heading.setOnClickListener(v -> {
                final boolean opening = expandedIndex[0] != index;
                expandedIndex[0] = opening ? index : -1;
                for (int j = 0; j < headings.length; j++) {
                    headings[j].setText(CATEGORY_GROUPS[j][0]
                            + (expandedIndex[0] == j ? " ▴" : " ▾"));
                    headings[j].setSelected(expandedIndex[0] == j);
                }
                secondary.removeAllViews();
                secondaryScroll.setVisibility(opening ? View.VISIBLE : View.GONE);
                if (!opening) {
                    return;
                }
                secondaryScroll.scrollTo(0, 0);
                for (int n = 1; n < group.length; n++) {
                    final String id = group[n];
                    final TextView choice = new TextView(requireContext());
                    choice.setText(MissAvKioskLinkHandlerFactory.getCategoryName(id)
                            .replace(group[0] + " · ", ""));
                    choice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    choice.setPadding(pad, pad / 2, pad, pad / 2);
                    choice.setSelected(id.equals(kioskId));
                    choice.setClickable(true);
                    choice.setFocusable(true);
                    secondary.addView(choice);
                    choice.setOnClickListener(child -> {
                        try {
                            NavigationHelper.openKioskFragment(getFM(), serviceId, id);
                        } catch (final ExtractionException e) {
                            showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR,
                                    "Opening MissAV category"));
                        }
                    });
                }
            });
        }
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
