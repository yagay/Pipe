package org.schabi.newpipe.extractor.kiosk;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BrowseUrlStateTest {
    private static final String BASE = "https://site.example/categories/action";

    @Test
    public void sortingPreservesOtherFiltersAndPageContext() {
        final String current = BASE + "?filters=individual&quality=hd&sort=views";
        final String selected = BASE + "?sort=recent";
        assertEquals(BASE + "?filters=individual&quality=hd&sort=recent",
                BrowseUrlState.applyOption(current, selected,
                        BrowseUrlState.OptionGroup.SORT));
    }

    @Test
    public void filteringPreservesSorting() {
        assertEquals(BASE + "?sort=views&filters=multiple",
                BrowseUrlState.applyOption(BASE + "?filters=individual&sort=views",
                        BASE + "?filters=multiple", BrowseUrlState.OptionGroup.FILTER));
    }

    @Test
    public void filterResetDoesNotEraseSort() {
        assertEquals(BASE + "?sort_by=recent",
                BrowseUrlState.applyOption(BASE + "?filters=individual&sort_by=recent",
                        BASE, BrowseUrlState.OptionGroup.FILTER));
    }

    @Test
    public void encodedOptionValuesRemainEncoded() {
        assertEquals(BASE + "?other=a%2Bb&sort=most%20viewed",
                BrowseUrlState.applyOption(BASE + "?other=a%2Bb",
                        BASE + "?sort=most%20viewed", BrowseUrlState.OptionGroup.SORT));
    }

    @Test
    public void activeStateIsDerivedFromFullUrl() {
        assertTrue(BrowseUrlState.selected(BASE + "?filters=individual&sort=views",
                BASE + "?sort=views", BrowseUrlState.OptionGroup.SORT));
        assertFalse(BrowseUrlState.selected(BASE + "?filters=individual&sort=views",
                BASE + "?sort=recent", BrowseUrlState.OptionGroup.SORT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOptionsFromOtherDomains() {
        BrowseUrlState.applyOption(BASE, "https://other.example/categories/action?sort=x",
                BrowseUrlState.OptionGroup.SORT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOptionsFromOtherDirectories() {
        BrowseUrlState.applyOption(BASE, "https://site.example/categories/other?sort=x",
                BrowseUrlState.OptionGroup.SORT);
    }

    @Test
    public void detectsAndResetsAllSortKeys() {
        assertTrue(BrowseUrlState.hasOption(BASE + "?o=mv", BrowseUrlState.OptionGroup.SORT));
        assertEquals(BASE + "?filters=hd", BrowseUrlState.applyOption(
                BASE + "?filters=hd&o=mv", BASE, BrowseUrlState.OptionGroup.SORT));
    }
    @Test
    public void queryBasedCategoriesAreDistinct() {
        assertFalse(BrowseUrlState.sameCategory(
                "https://www.pornhub.com/video?c=3&sort=views",
                "https://www.pornhub.com/video?c=4"));
        assertTrue(BrowseUrlState.sameCategory(
                "https://www.pornhub.com/video?c=3&sort=views&page=2",
                "https://www.pornhub.com/video?c=3"));
    }

    @Test
    public void ignoresQueryOrderWhenSelectingOptions() {
        assertTrue(BrowseUrlState.selected(
                BASE + "?sort=views&filters=individual",
                BASE + "?sort=views", BrowseUrlState.OptionGroup.SORT));
    }
    @Test
    public void preservesActiveOptionsOnNextPage() {
        assertEquals(BASE + "?page=2&sort=views&filters=individual",
                BrowseUrlState.carryOptionsToNextPage(
                        BASE + "?sort=views&filters=individual",
                        BASE + "?page=2"));
    }

    @Test
    public void paginationDoesNotOverwriteSiteProvidedOption() {
        assertEquals(BASE + "?page=2&sort=recent&filters=individual",
                BrowseUrlState.carryOptionsToNextPage(
                        BASE + "?sort=views&filters=individual",
                        BASE + "?page=2&sort=recent"));
    }

    @Test
    public void paginationNeverLeaksOptionsAcrossCategories() {
        assertEquals("https://site.example/categories/other?page=2",
                BrowseUrlState.carryOptionsToNextPage(
                        BASE + "?filters=individual",
                        "https://site.example/categories/other?page=2"));
    }

}
