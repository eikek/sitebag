/**
 * The bookmarklet adds the current browser location to sitebag.
 * It does this by creating a new <script/> element with a special
 * url that is handled by sitebag such that the browser current
 * url is added and the response is a simple javascript alert
 * message that is then displayed to the user.
 *
 * Use http://marijnhaverbeke.nl/uglifyjs to minify this but make
 * sure to use '(function(){...})();' instead of '!function(){...}();'.
 */
(function() {
  if (document.location.host != '{{host}}') {
    var r = '{{userApiUrl}}?add&url='+encodeURIComponent(location.href);
    var e = document.createElement('script');
    e.setAttribute("type", "text/javascript");
    e.setAttribute("charset", "utf-8");
    e.setAttribute("src", r);
    document.documentElement.appendChild(e);
  }
})();