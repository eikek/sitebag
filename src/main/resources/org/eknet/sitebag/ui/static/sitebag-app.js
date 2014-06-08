// ----- spin.js
var __spinner = new Spinner({
  lines: 13,
  length: 15,
  width: 3,
  radius: 9,
  hwaccel: true
});
function spin(target) {
  __spinner.spin(target.get(0));
}
function stopSpin(target) {
  __spinner.stop(target.get(0));
}

/// ---- post json

function postJson(url, data, callback) {
  if ( jQuery.isFunction( data ) ) {
    callback = data;
    data = null;
  }
  return jQuery.ajax({
    type: "POST",
    url: url,
    data: JSON.stringify(data),
    contentType: "application/json",
    dataType: "json",
    success: callback,
    error: function(xhr, text, error) {
      feedback({ success: false, message: "Error response from server: "+ error })
    }
  });
}

function ajaxGet(url, data, callback) {
  if ( jQuery.isFunction( data ) ) {
    callback = data;
    data = null;
  }
  return jQuery.ajax({
    url: url,
    data: data,
    success: callback,
    error: function(xhr, text, error) {
      feedback({ success: false, message: "Error response from server: "+ error })
    }
  });
}
function ajaxPost(url, data, callback) {
  if ( jQuery.isFunction( data ) ) {
    callback = data;
    data = null;
  }
  return jQuery.ajax({
    type: "POST",
    url: url,
    data: data,
    success: callback,
    error: function(xhr, text, error) {
      feedback({ success: false, message: "Error response from server: "+ error })
    }
  });
}

// ---- notifier
function feedback(data) {
  var msg = $('<div/>', {
    "class": (data.success) ? "alert alert-success" : "alert alert-danger"
  }).html(data.message);
  var div = $('#sb-notification');
  div.html(msg).animate({delay: 1}, 3500, function () {
    div.html("");
  })
}

// ---- Mustache helper
function render(templateId, context) {
  var templ = $('#'+templateId).html();
  Mustache.parse(templ);
  return Mustache.render(templ, context);
}

//---- theme switcher
$(function() {
  var themeModal = $('#sb-theme-modal');
  $('.sb-themes-btn').bind('click', function (e) {
    themeModal.modal('show');
    ajaxGet(settings.bootswatchApi, function (data) {
      data.themes.push({
        cssCdn: '//netdna.bootstrapcdn.com/bootstrap/latest/css/bootstrap.min.css',
        name: 'Boostrap Default',
        thumbnail: settings.uiPath("/static/bootstrap-default-preview.jpg")
      });
      data.themes.forEach(function (theme, i) {
        var context = $.extend({}, theme, {
          currentIcon: theme.cssCdn === settings.themeUrl,
          defaultIcon: theme.cssCdn === settings.defaultThemeUrl
        });
        themeModal.find('.modal-body').append(render('sbtpl-theme-element', context));
      });
      themeModal.find('.sb-theme-btn').bind('click', function (b) {
        var cssurl = $(this).attr('data-id');
        spin(themeModal);
        ajaxPost(settings.uiPath("/api/set-theme"), { "theme": cssurl }, function (resp) {
          feedback(resp);
          if (resp.success) {
            location.reload();
          } else {
            stopSpin(themeModal);
          }
        });
      });
    });
  });
});

// --- entry search form

(function($) {
  $.fn.fillSbSearchForm = function(dataString) {
    var form = $(this);
    if (!dataString) {
      dataString = location.hash;
    }
    if (!dataString) {
      dataString = "archived=false";
    }
    if (dataString.charAt(0) == '#') {
      dataString = dataString.substr(1);
    }
    form.find('input:checkbox').prop("checked", false);
    form.find('select').val('');
    $.each(dataString.split('&'), function(idx, el) {
      var pair = this.split('=');
      var n    = decodeURIComponent(pair[0]);
      var v    = pair.length > 1 ? decodeURIComponent(pair[1]) : null;
      if (n === "archived") {
        form.find('select').val(v);
      }
      if (n === "tag") {
        form.find('input[value="'+v+'"]').prop("checked", true);
      }
      if (n === "q") {
        form.find('input[name="q"]').val(v);
      }
    });
    return this;
  };
})(jQuery);

function createEntrySearchForm(callback) {
  function bindHandler() {
    var cnt = $('#sb-search-accordion');
    cnt.find('.sb-search-form').bind('change', function(f) {
      location.hash = '#' + $(this).serialize();
      loadEntries();
    });
    cnt.find('#sb-search-button').click(function(ev) {
      cnt.find('.sb-search-form').change();
      ev.preventDefault();
      return false;
    });
    cnt.find("input").keypress(function(ev) {
      if (ev.keyCode === 13) { // hits enter;
        $(this).parents("form").change();
        ev.preventDefault();
        return false;
      }
    });
  }

  $.get(settings.apiPath("/tags"), function(data) {
    if (data.success) {
      var cnt = $('#sb-search-tags');
      cnt.empty();
      var tags = [];
      data.value.tags.forEach(function(el, i) {
        tags.push({name: el, count: data.value.cloud[el] });
      });
      cnt.html(render('sbtpl-search-form', { "tags": tags }));
      bindHandler();
      if (callback) {
        callback();
      }
    } else {
      feedback(data);
    }
  });
}

// ---- bind page entry actions
function bindEntryActions(callback) {
  $('.sb-delete-entry').click(function(ev) {
    var id = $(this).parents("[data-id]").attr('data-id');
    if (id) {
      var confirmModal = $('#sb-delete-confirm-modal');
      confirmModal.find('.sb-confirm-btn').bind('click', function (b) {
        postJson(settings.apiPath("/entry/"+id), { delete: true }, function (data) {
          feedback(data);
          if (callback) {
            callback("delete", id);
          }
        });
        $(this).attr('data-id', '');
      });
      confirmModal.modal('show');
    }
    ev.preventDefault();
    return false;
  });
  $('.sb-togglearchived-entry').click(function(ev) {
    var id = $(this).parents("[data-id]").attr('data-id');
    if (id) {
      ajaxPost(settings.apiPath("/entry/"+id+"/togglearchived"), function(data) {
        feedback(data);
        if (callback) {
          callback("togglearchived", id);
        }
      });
    }
    ev.preventDefault();
    return false;
  });
  $('.sb-favour-entry').click(function(ev) {
    var action = $(this).attr('data-action');
    var id = $(this).parents("[data-id]").attr('data-id');
    if (id && action) {
      ajaxPost(settings.apiPath("/entry/"+id+"/"+ action), { tag: "favourite" }, function(data) {
        feedback(data);
        if (callback) {
          callback("favour", id);
        }
      });
    }
    ev.preventDefault();
    return false;
  });
  $('.sb-tag-entry').click(function(ev) {
    $('#sb-edit-tags-modal').remove();
    $('body').append(render('sbtpl-edit-tags-modal'));
    var modal = $('#sb-edit-tags-modal');
    var selectedTags = {};
    var tagin = $('#sb-tag-input').val("");
    var select = $('#sb-tag-select').empty();
    var id = $(this).parents("[data-id]").attr('data-id');
    var p1 = $.get(settings.apiPath('/entry/'+id+'/tags'));
    var p2 = $.get(settings.apiPath('/tags'));
    $.when(p1, p2).done(function(pageTags, allTags) {
      pageTags[0].value.forEach(function(el, i) {
        select.append('<option value="'+el+'">'+el+'</option>');
        selectedTags[el] = true;
      });
      if ($.inArray('favourite', allTags[0].value.tags) < 0) {
        allTags[0].value.tags.push('favourite');
      }
      function addTagHandler() {
        var name = tagin.val();
        if (name && !selectedTags[name]) {
          select.append('<option value="'+name+'">'+name+'</option>');
          selectedTags[name] = true;
          tagin.val("");
        }
      }
      function removeTagHandler(name) {
        if (name) {
          select.find('option[value="'+name+'"]').remove();
          delete selectedTags[name];
        }
      }
      tagin.typeahead({ source: allTags[0].value.tags }).keyup(function(ev) {
        if (ev.keyCode === 13) { // hits enter
          addTagHandler();
          ev.preventDefault();
          return false;
        }
      });
      modal.find('.sb-tag-edit-form').submit(function(ev) {
        ev.preventDefault();
        return false;
      });
      modal.find('.sb-add-btn').click(function(b) {
        addTagHandler()
      });
      select.dblclick(function(e) {
        if ($(e.target).parent().attr('id') === select.attr('id')) {
          var name = $(e.target).text();
          removeTagHandler(name);
        }
      });
      modal.find('.sb-remove-btn').click(function(b) {
        removeTagHandler(select.val());
      });
      $('#sb-edit-tags-modal').find('.sb-confirm-btn').click(function(b) {
        var tags = Object.keys(selectedTags);
        if (tags.length === 0) {
          postJson(settings.apiPath('/entry/'+id+'/untag'), { "tags": pageTags[0].value }, function(data) {
            feedback(data);
            if (callback) {
              callback("untagall", id);
            }
          });
        } else {
          postJson(settings.apiPath('/entry/'+id+'/tags'), { "tags": tags }, function(data) {
            feedback(data);
            if (callback) {
              callback("tagged", id);
            }
          });
        }
      });
    });
    modal.modal('show');
    tagin.focus();
    ev.preventDefault();
    return false;
  });
}

// ---- load entry list
function loadEntries(q, p) {
  var pageSize = 21;
  var form = $('.sb-search-form');
  var query =  q || form.serialize();
  var page = p || 1;
  query = query + "&size="+ pageSize +"&num="+ page;
  var displayAll = !form.find('select[name="archived"]').val() || form.find('select[name="archived"]').val() === "all";
  function appendRow(cnt, row) {
    cnt.append($("<div/>", {
      class: "row",
      html: row
    }));
  }
  var spinTarget = $('.sb-articles').parent();
  spin(spinTarget);
  $.getJSON(settings.apiPath("/entries/json"), query, function (response) {
    stopSpin(spinTarget);
    if (page == 1) {
      $('.sb-articles').empty();
    }
    if (!response.success) {
      feedback(data);
    }
    else if (response.value.length == 0) {
      $('.sb-articles').attr("data-id", page+"!");
    }
    else {
      var cnt = $('.sb-articles').attr("data-id", page);
      var row = "";
      response.value.forEach(function (entry, entryIndex) {
        if (!entry.shortText) {
          entry.shortText = "...";
        }
        var tags = "";
        entry.tags.forEach(function(el, i) {
          if (i > 0) { tags += ', '; }
          tags += el
        });
        var context = $.extend({}, entry, {
          tagString: tags,
          mute: displayAll && entry.archived,
          actions: render('sbtpl-entry-actions', {
            favourite: $.inArray('favourite', entry.tags) >= 0,
            archived: entry.archived
          })
        });

        row += render('sbtpl-entry', context);
        if ((entryIndex +1) % 3 == 0) {
          appendRow(cnt, row);
          row = "";
        }
      });
      if (row) {
        appendRow(cnt, row);
      }
      //bind actions
      bindEntryActions(function() {
        createEntrySearchForm();
        loadEntries();
      });
    }
  });
}
$(function() {
  //listen for scroll events to load next results
  $(window).scroll(function() {
    var pageId = $('.sb-articles').attr("data-id");
    pageId = pageId || "!";
    if (pageId.indexOf("!") < 0) { //otherwise we are on last page
      var page = 1;
      if (pageId) {
        page = parseInt(pageId); //strips last "!"
      }
      if ($(window).scrollTop() == $(document).height() - $(window).height()) {
        loadEntries(null, page+1);
      }
    }
  });
});

// --- add entry link
$(function() {
  $('#sb-add-entry-btn').bind('click', function(b) {
    var form = $(this).parent();
    var url = form.find('input[type="text"]');
    if (url.val()) {
      spin(form);
      ajaxPost(settings.apiPath("/entry"), { url: url.val() }, function(data) {
        stopSpin(form);
        url.val("");
        feedback(data);
        loadEntries();
      });
    }
  });
});
