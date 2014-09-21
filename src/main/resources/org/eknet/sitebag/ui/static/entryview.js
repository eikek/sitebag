function rewriteUrls() {
    $('.sb-entry-content').find('img[src]').each(function(i, el) {
        var url = el.src;
        if (!settings.isBinPath(url)) {
            $(el).attr("src", settings.binPath(url));
            $(el).addClass("img-responsive");
        }
    });
}
function codeHighlight() {
    if (settings.enableHighlightJs) {
        $('pre code').each(function(i, e) {hljs.highlightBlock(e);});
    }
}

var sitebagEntryId = "";
function sb_renderActions(entry) {
    var actions = render('sbtpl-entry-actions', {
        favourite: $.inArray('favourite', entry.value.tags) >= 0,
        archived: entry.value.archived
    });
    $('.sb-entry-actions').html(actions);
    bindEntryActions(function(action, id) {
        if (action === "delete") {
            location.href = settings.uiPath();
        } else {
            getEntry(true, function(entry) {
                sb_renderActions(entry);
                sb_renderTags(entry);
            });
        }
    });
}
function sb_renderTags(entry) {
    var tagstr = function() {
        return entry.value.tags.reduce(function(a, b) {
            return a +", "+ b;
        });
    };
    $('.sb-tagstring').html(entry.value.tags.length == 0 ? "" : tagstr());
}
function sb_renderContent(entry) {
    $('.sb-entry-content').html(entry.value.content);
}
function getEntry(meta, callback) {
    ajaxGet(settings.apiPath("/entry/"+sitebagEntryId + (meta ? "?meta" : "")), function(data) {
        if (data.success) {
            callback(data);
        } else {
            feedback(data);
        }
    });
}

function bindAddLinks() {
    $('.sb-add-entry-link').click(function(ev) {
        var self = $(this);
        var url = self.attr('data-id');
        if (url) {
            spin(self.parent());
            ajaxPost(settings.apiPath("/entry"), { url: url }, function(data) {
                stopSpin(self.parent());
                feedback(data);
                if (data.success) {
                    self.remove();
                    $('a[href="'+url+'"]').attr("href", settings.uiPath("/entry/"+data.value));
                }
            });
        }
        ev.preventDefault();
        return false;
    });
}

$(function() {
    sitebagEntryId = window.location.pathname.substr(settings.uiPath("/entry/").length);
    spin($('.sb-entry-content'));
    getEntry(false, function(entry) {
        sb_renderActions(entry);
        sb_renderTags(entry);
        sb_renderContent(entry);
        rewriteUrls();
        codeHighlight();
        stopSpin($('.sb-entry-content'));
        bindAddLinks();
    });
});
