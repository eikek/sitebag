# Sitebag

Sitebag is a [wallabag](https://github.com/wallabag/wallabag)
compatible REST server that is written in Scala.

The reason is, that I find php apps too hard to install and maintain,
because of all the dependencies you have to take care. It's nicer to
have a self-contained application, that you simply unzip and start.
The jvm is a great platform for this and it fits better in my
environment this way. To reuse the android and other great wallabag
clients, the rest api is compatible to that of wallabag.

While a web ui may follow later, the focus is to create a REST
client. This makes it very easy for everyone to integrate it in any
existing website.

## Dependencies

* [jsoup](https://github.com/jhy/jsoup/) is used for html content
  extraction
* [spray](http://spray.io) provides the http stack
* [casbah](https://github.com/mongodb/casbah) connects the app to a
  mongo database and
* [porter](https://github.com/eikek/porter) provides authentication
  things

## Building

The build tool [sbt](http://scala-sbt.org) can compile the sources and
create deployable artifacts:

    sbt dist

will create a zip file, ready to start.


## Usage

The REST api can be queried with a www-form-urlencoded parameter list
or a corresponding json object. Where applicable, there exists a
wallabag compatible url endpoint that responds to GET requests.
Sitebag will prefer POST requests and only accepts GET requests with
an `Authorization` header. Since token and passwords should be
protected by the SSL/TLS encryption, appending them to the query of an
url will defeat this. For compatibility reasons, this restriction does
not apply to the wallabag url endpoints.

Once an account exists, create a token to enable the feed urls.

The feed urls are then (the `archived` parameter is optional):

    http://sitebag/<account>/entry/rss/<tag>/?password=<token>&archived (POST)

or the wallabag compatible url:

    http://sitebag/<account>/?feed&type=[home|fav|archive]&user_id=1&token=<token> (GET)

The `user_id` parameter is ignored by sitebag. To be able to resolve
wallabag urls to tag feeds like

    http://sitebag/<account>/?feed&type=tag&user_id=99&tag_id=3&token=<token>

sitebag will lookup the tag name either using the `tag_id` parameter,
or if that fails, the `type` parameter. Thus for sitebag, the `tag_id`
parameter must be associated to the tag name and not an to a numeric
id.

A "token" here is just a second password string that is only used with
non-admin urls. With admin urls (like `/newtoken`, the first password
must be used) -- see below.

### Administration

The following urls expect the first (main) password when
authenticating.


#### create a new user account

Here the password is a special one that is set in the configuration
file. If it is not set, this will not work.

    POST /newuser?password=&newaccount=&newpassword=
    POST /newuser { password: "", newpccount: "", newpassword: "" }
    > JSON { success: true, message: "" }

Since user managementis left to
[porter](https://github.com/eikek/porter) you can simply use its
console to create user accounts.

#### create a new token

Creates a secondary password that must be supplied with all non-admin
urls.

    POST /<account>/newtoken?account=&password=
    POST /<account>/newtoken { account: "", password: "" }
    JSON { token: "random" }


#### get user config

Returns a JSON object containing all information about an account.

    GET /<account> (http-basic)
    POST /<account> { account: "", password: "" }
    > JSON { account: "eike", tags: [], feeds: [], token: "" }


#### change main password 

    POST /eike/changepassword?newpassword=&account=&password=
    POST /eike/chanegpassword { account: "", password: ""; newpassword: "" }
    > JSON { success: true, message: "password changed" }


#### delete user account

This will remove the account and all its data.

    DELETE /<account> ?account=&password=
    DELETE /<account> JSON{ account: "", password: "" }
    > JSON { success: true, message: "" }

If you remove an account using porter's console, the data related to
this account is still there. You can manually drop the collection with
mongodb client.

Deleting will work with the admin password and the user account
credentials.


#### Application

The following sites expect the secondary password (aka token) when
authenticating.


#### add new site

    POST /eike/entry/add?url=http...&account=&password=
    PUT /eike/entry { url: "", account: "", password: "" }
    > JSON{ hash: "", title: "", url: "", content: "", read: false, tags:[] }

TODO: add wallabag url

This will add the contents of the site at the given url to
sitebag. The page is fetched and an md5 checksum is created of the
content which will be used as its id.

If `download-images` is set to true, images in the page are downloaded
to the server. You can request image url rewriting when fetching the
page entry. Urls to local images are not access protected.


#### delete site

Deletes the page entry with the given id.

    POST /eike/entry/delete?hash=<hash>&account=&password=
    DELETE /eike/entry { hash: "", account: "", password: "" }
    > { title: "", hash: "", success: true, message: "Deleted successfully." }

TODO: Wallabag url


#### toggle read / unread

Toggles the read flag on a page entry.

    POST /eike/entry/[toggle|set|unset]read?hash=<hash>&account=&password=[&flag=true|false]
    POST /eike/entry/[toggle|set|unset] JSON{ hash: "", account: "", password: "", flag: }
    > { read: true|false }

TODO: wallabag url

Instead of `toggle` you can use `set`/`unset` and specify the flag.


#### retrieve a site entry

Fetch a page entry from sitebag by its id.

    GET /eike/entry/<hash> (http-basic)
    POST /eike/entry/<hash>/?account=&password=
    POST /eike/entry/<hash> { account: "", password: "" }
    > JSON{ hash, title, url, content, read, tags: [] }

Additional parameters can be supplied to enable some processing:

* `localimages` will produce a page with all image urls rewritten to
  the local downloaded once at sitebag


#### retrieve a feed

Get a list of page entries as RSS feed or as JSON object.

    GET /eike/entry/rss|json/<tag>/?archived (http-basic)
    POST /eike/entry/rss|json/<tag>/?archived&account=&password=
    POST /eike/entry/rss|json/<tag>/ { archived: true, account: "", password: "" }
    > xml rss or json

TODO: wallabag url

The `archived` parameter controls whether to return unread or read
page entries. Other filtering is only possible via tags. There is one
known tag called "favourite" that returns all favourite-marked
entries.


#### tag / untag a site entry

Adds or removes a tag from a page entry.

    POST /eike/entry/[un]tag/<hash>/?tag=a&tag=b&tag=c&tags=x,y,z&account=&password=
    POST /eike/entry/[un]tag/<hash>/ { account:"", password: "", tags: "..." }
    > JSON { success: true, message: "tags added/removed" }

TODO: wallabag url

You can either specify multiple `tag` parameters or one `tags`
parameter with a comma separated list of tag names. Tag names must
consist of alphanumeric characters and `-` and `_`.


#### list all tags or feeds

Returns a list of used tags or feed urls.

list tag names:

    POST /eike/tags?account=&password=
    POST /eike/tags JSON{ account: "", password: "" }
    > JSON { tags: [] }

The feed url list is similiar:

    POST /eike/feeds?account=&password=&format=json|rss
    POST /eike/feeds { account: "", password: "", format: "json|rss" }
    > JSON { feeds: [] }


