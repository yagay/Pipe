package org.schabi.newpipe.fragments.list.kiosk;

import android.os.Bundle;
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
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.kiosk.SiteBrowseCatalog;
import org.schabi.newpipe.extractor.kiosk.BrowseUrlState;
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
    private MissAvBrowseHeader missAvBrowseHeader;
    private SiteBrowseHeader siteBrowseHeader;
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

    public static KioskFragment getMissAvCategoryInstance(final int sid,
                                                           final String categoryUrl,
                                                           final String title) {
        final KioskFragment instance = new KioskFragment();
        instance.setInitialData(sid, categoryUrl, title);
        instance.kioskId = title;
        return instance;
    }

    public static KioskFragment getSiteCategoryInstance(final int siteId,
                                                        final String pageUrl,
                                                        final String categoryName) {
        final KioskFragment instance = new KioskFragment();
        instance.setInitialData(siteId, pageUrl, categoryName);
        instance.kioskId = "category:" + pageUrl;
        instance.kioskTranslatedName = categoryName;
        return instance;
    }

    public KioskFragment() {
        super(UserAction.REQUESTED_KIOSK);
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("kioskId", kioskId);
        outState.putString("kioskTranslatedName", kioskTranslatedName);
        outState.putSerializable("contentCountry", contentCountry);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        kioskId = savedInstanceState.getString("kioskId", "");
        kioskTranslatedName = savedInstanceState.getString("kioskTranslatedName");
        contentCountry = (ContentCountry) savedInstanceState.getSerializable("contentCountry");
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (kioskTranslatedName == null) {
            kioskTranslatedName = kioskId.startsWith("category:")
                    ? "Categories" : KioskTranslator.getTranslatedKioskName(kioskId, activity);
        }
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

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        final View missAvHeader = rootView.findViewById(R.id.missav_browse_header);
        final View universalHeader = rootView.findViewById(R.id.site_browse_header);
        missAvHeader.setVisibility(View.GONE);
        universalHeader.setVisibility(View.GONE);
        rootView.findViewById(R.id.site_browse_container).setVisibility(View.GONE);
        final StreamingService currentService;
        try {
            currentService = NewPipe.getService(serviceId);
        } catch (final ExtractionException e) {
            return;
        }
        if (currentService instanceof MissAvService) {
            missAvHeader.setVisibility(View.VISIBLE);
            missAvBrowseHeader = new MissAvBrowseHeader(rootView, url, option -> {
                if (!option.url.equals(url)) {
                    NavigationHelper.openMissAvBrowseFragment(getFM(), serviceId,
                            option.url, option.label);
                }
            });
            return;
        }

        // Every other site uses the same presentation, but only for destinations
        // which its own extractor has already registered as native kiosks.
        try {
            siteBrowseHeader = new SiteBrowseHeader(rootView,
                    SiteBrowseCatalog.getEntries(currentService), currentService, url, kioskId,
                    entry -> {
                        if (entry.getId().equals(kioskId)) {
                            return;
                        }
                        if (entry.getType() != SiteBrowseCatalog.EntryType.KIOSK) {
                            String destination = entry.getUrl();
                            if (entry.getType() == SiteBrowseCatalog.EntryType.SORT
                                    || entry.getType() == SiteBrowseCatalog.EntryType.FILTER) {
                                try {
                                    destination = BrowseUrlState.applyOption(url, entry.getUrl(),
                                            entry.getType() == SiteBrowseCatalog.EntryType.SORT
                                                    ? BrowseUrlState.OptionGroup.SORT
                                                    : BrowseUrlState.OptionGroup.FILTER);
                                } catch (final IllegalArgumentException invalidOption) {
                                    showSnackBarError(new ErrorInfo(invalidOption,
                                            UserAction.UI_ERROR, "Invalid site filter"));
                                    return;
                                }
                            }
                            if (!destination.equals(url)) {
                                NavigationHelper.openSiteCategoryFragment(getFM(), serviceId,
                                        destination,
                                        entry.getType() == SiteBrowseCatalog.EntryType.VIDEO_CATEGORY
                                                ? entry.getLabel() : kioskTranslatedName);
                            }
                            return;
                        }
                        try {
                            NavigationHelper.openKioskFragment(getFM(), serviceId, entry.getId());
                        } catch (final ExtractionException e) {
                            showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR,
                                    "Opening site browse page"));
                        }
                    });
        } catch (final ExtractionException e) {
            showSnackBarError(new ErrorInfo(e, UserAction.UI_ERROR,
                    "Building site navigation"));
        }
    }

    @Override
    public void onDestroyView() {
        if (siteBrowseHeader != null) {
            siteBrowseHeader.dispose();
            siteBrowseHeader = null;
        }
        if (missAvBrowseHeader != null) {
            missAvBrowseHeader.dispose();
            missAvBrowseHeader = null;
        }
        super.onDestroyView();
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
