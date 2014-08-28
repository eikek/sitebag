$(function() {
    createEntrySearchForm(function() {
        $('.sb-search-form').fillSbSearchForm();
        loadEntries();
    });
});
