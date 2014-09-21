$(function() {
    $('#sb-newtoken-btn').click(function (ev) {
        spin($(this).parent());
        $.post(settings.apiPath("/newtoken"), function (data) {
            stopSpin($(this).parent());
            feedback(data);
            if (data.success) {
                $('#sb-token-text').html(data.value);
            }
        });
        ev.preventDefault();
        return false;
    });
    $('#sb-reextract-btn').click(function(ev) {
        var progressCnt = $('.sb-reextract').find('.sb-progress-cnt');
        var pollStatus = function() {
            $.get(settings.apiPath("/reextract"), { "status": true },  function(data) {
                if (data.success && data.value.running) {
                    progressCnt.html(render('sbtpl-progress-bar', data.value));
                    setTimeout(pollStatus, 200);
                } else {
                    progressCnt.html("");
                }
            });
        };
        var entryid = $('#sb-reextract-entryid').val() || null;
        postJson(settings.apiPath("/reextract"), { "entryId": entryid }, function(data) {
            feedback(data);
            if (data.success) {
                pollStatus();
            }
        });
        ev.preventDefault();
        return false;
    });
    $('.sb-deleteaccount-btn').click(function(ev) {
        spin($(this).parent());
        var accountEl = $('input#sb-delete-account-text');
        var accountName = accountEl.val();
        if (accountName) {
            var api = settings.pathprefix + 'api/' + accountName;
            postJson(api, { "delete": true }, function(data) {
                stopSpin($(this).parent());
                if (settings.username === accountName) {
                    alert("Good bye.");
                } else {
                    $('input#sb-delete-account-text').val("");
                    feedback(data);
                }
            });
        } else {
            feedback({success: false, message: "No account name specified."});
        }
        ev.preventDefault();
        return false;
    });

    if(settings.enableChangePassword) {
        $('.sb-changepw-btn').click(function (ev) {
            var pw1 = $('#sbpw1').val();
            var pw2 = $('#sbpw2').val();
            var self = $(this);
            if (pw1 !== pw2 || pw1 == "" || pw2 == "") {
                self.parent().find('.form-group').toggleClass('has-error');
            } else {
                spin(self.parent());
                $.post(settings.apiPath("/changepassword"), { "newpassword": pw1 }, function (data) {
                    stopSpin(self.parent());
                    feedback(data);
                    self.parent().find('input').val("");
                });
            }
            ev.preventDefault();
            return false;
        });
    }

    if (settings.canCreateUser) {
        $('.sb-createuser-btn').click(function (ev) {
            var username = $('#sb-username').val();
            var pw1 = $('#sbpwcreate1').val();
            var pw2 = $('#sbpwcreate2').val();
            var self = $(this);
            if (username == "") {
                self.parent().find('[type="text"]').parent().addClass('has-error');
            }
            else if (pw1 != pw2 || pw1 == "" || pw2 == "") {
                self.parent().find('[type="text"]').parent().removeClass('has-error');
                self.parent().find('[type="password"]').parent().addClass('has-error');
            } else {
                spin(self.parent());
                postJson(settings.userPath(username), { "newpassword": pw1 }, function (data) {
                    stopSpin(self.parent());
                    feedback(data);
                    self.parent().find('input').val("");
                });
            }
            ev.preventDefault();
            return false;
        });
    }
});
