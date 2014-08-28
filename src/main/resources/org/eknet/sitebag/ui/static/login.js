$(function() {
    var form = $('#sb-login-form');
    form.submit(function(ev) {
        var data = form.serialize();
        var username = form.find('#username').val();
        settings.username = username;
        var url = settings.apiPath("/login");
        var goto = settings.uiPath();
        if (location.search) {
            var ref = location.search;
            if (ref.charAt(0) === '?') {
                ref = ref.substr(1);
            }
            $.each(ref.split('&'), function(idx, el) {
                var pair = this.split('=');
                var n    = decodeURIComponent(pair[0]);
                var v    = pair.length > 1 ? decodeURIComponent(pair[1]) : null;
                if (n === "r") {
                    goto = v;
                }
            });
        }
        spin(form);
        $.post(url, data, function(result) {
            stopSpin(form);
            if (result.success) {
                window.location = goto;
            } else {
                feedback(result);
                form.find('input').val('');
            }
        });

        ev.preventDefault();
        return false;
    });
});
