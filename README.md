# Sitebag

Sitebag is a "read-it-later" REST server; a self-hostable web application
to store web pages. It saves the complete page (including images) as well
as the extracted main content. You can read the saved pages using a browser
or rss reader; or create another rest client.

Sitebag is compatible to [wallabag](https://github.com/wallabag/wallabag),
meaning that it responds to its url endpoints. This way you can use their
nice android and iOS clients as well as their browser extension with sitebag.

There is a basic web ui provided, too.

## Dependencies

* [jsoup](https://github.com/jhy/jsoup/) is used for html content
  extraction
* [spray](http://spray.io) provides the http stack
* [reactivemongo](http://reactivemongo.org/) connects the app to a
  mongo database and
* [porter](https://github.com/eikek/porter) provides authentication
  and user management

The ui templates are processed by [twirl](https://github.com/spray/twirl)
and [bootstrap](http://getbootstrap.com) is used to provide a responsive
site. You can choose from free [bootswatch](http://bootswatch.com/)
themes. Additionally, [mustache](https://github.com/janl/mustache.js) and
[spinjs](http://fgnass.github.io/spin.js/) are at work.

## Building

The build tool [sbt](http://scala-sbt.org) can compile the sources and
create deployable artifacts:

    sbt dist

will create a zip file, ready to start.

## Installation

#### 1. Install MongoDB and JRE

SiteBag needs a running MongoDB and Java 1.7.

#### 2. Download sitebag zip file

Unpack the zip file somewhere. You can have a look at the configuration
file `etc/sitebag.conf`. If you have mongodb running on some other port,
you'll need to update this file.

#### 3. Starting up

For linux, simply execute the script `bin/start.sh`. There is no windows
`.bat` version yet.

#### 4. Add user account

You'll need to setup an account with sitebag. The very first account
cannot be setup via the web interface. Please use the following steps to
add a admin user, with all permissions. In a terminal type:

    $ telnet localhost 9996
    > update realm
    name: default
    description:
    > update group
    name: admin
    rules: sitebag:*
    props:
    > update account
    name: admin
    groups: admin
    password: admin

Now you can login with username "admin" and password "admin" at <http://localhost:9995/ui/conf>.
Of course, you can choose different name and password. The rule `sitebag:*` means that this user
has all sitebag related permissions. You can then create new -- non-admin -- accounts via the web
interface. Non-admin accounts have only permissions to access their own data.

If the admin account should also be a standard sitebag account (not only for creating new users),
then you need to create a token for it which can be done on the web interface mentioned above.

## Usage

The web interface can be found at `http://localhost:9995/ui`.

The REST api is `http://localhost:9995/api/...` and can be queried with a `www-form-urlencoded`
parameter list or a corresponding json object.

Sitebag authenticates requests either with http basic, using provided json
values `account` and `password` (or `token`) or a cookie. There are two passwords
involved: the main password must be used for administrative tasks
(that modify data), and the "token" is used for read-only access.

Due to the many notices I found in forums that some browsers don't
support `PUT` or `DELETE` requests and the fact that the support in
jquery for this is limited, there will be a fallback to `POST`
requests with some action parameter (for example
`POST /entry?id=abc123&delete`).

All JSON responses will always return an object like:

    { sucess: true|false, message: "error or success message", value: ... }

If the request was for side-effects only, `value` is omitted.

The very first account must be created using porter's api or console.
Once an account exists, create a token to enable the feed urls.

In short, here are the relevant url endpoints:

    /api/<account> (PUT ?newaccount=&newpassword=)
    /api/<account>/newtoken
    /api/<account>/changepassword?newpassword=
    /api/<account>/entry (PUT|GET?id=)
    /api/<account>/entry/<id> (GET|DELETE|POST?delete=true)
    /api/<account>/entry/<id>/togglearchived
    /api/<account>/entry/<id>/setarchived ?flag=true|false
    /api/<account>/entry/<id>/tag ?tag=&tag=|tags=a,b,c
    /api/<account>/entry/<id>/untag ?tag=&tag=|tags=a,b,c
    /api/<account>/tags ?filter=pro.*ing
    /api/<account>/entries/rss ?tag=&tag=&tags=a,b,c&archived | q= (lucene)
    /api/<account>/entries/json   -"-
    /api/<account>/bin/<id> (GET) binary files
    /api/<account>/stats (GET) db stats

More details to each one can be found below.

#### create a new user account

Creates a new user account.

    PUT /<account> { newpassword: "" }
    -> JSON { success: true, message: "" }

You must have the permission `sitebag:createuser` to be able to create
new accounts.

Sitebag users must have a set of permissions that are checked on each
access. For example, to retrieve a page entry the permission
`sitebag:<user>:get:entry:<id>` is checked. You can easily grant a user
"john" all permissions for his own things, by adding `sitebag:john:*` to
his permission set. This is done automatically when creating new accounts
via this api.


#### create a new token

Creates a secondary password that must be supplied with all non-admin
urls.

    GET|POST /<account>/newtoken
    -> JSON { token: "random" }


#### change main password

    POST /<account>/changepassword?newpassword=
    POST /<account>/chanegpassword { newpassword: "" }
    -> JSON { success: true, message: "password changed" }


#### delete user account

This will remove the account and all its data.

    DELETE /<account>
    -> JSON { success: true, message: "" }

If you remove an account using porter's console, the data related to
this account is still there. You can manually drop the collection with
mongodb client.


#### add new site

    PUT|POST /<account>/entry { url: "", title: "", tags: [] }
    -> JSON{ id: "", title: "", url: "", content: "", read: false, tags:[] }

This will add the contents of the site at the given url to
sitebag. The `title` parameter is optional and will override
the automatic detection.


#### delete site

Deletes the page entry with the given id.

    DELETE /<account>/entry/<id>
    POST   /<account>/entry/<id> {delete:true}
    POST   /<account>/entry/<id> delete
    -> { success: true, message: "Deleted successfully." }


#### toggle archived

Toggles the archived flag on a page entry.

    POST /<account>/entry/<id>/[toggle|set]archived[?flag=true|false]
    POST /<account>/entry/<id>[toggle|set]archived JSON{ id: "", flag: }
    -> { value: true|false }

Instead of `toggle` you can use `set` and specify the flag. The response
contains the new archived value.


#### retrieve a site entry

Fetch a page entry from sitebag by its id.

    GET /<account>/entry?id=<hash>
    GET /<account>/entry/<id>
    > JSON{ hash, title, url, content, shortText, archived, created, tags: [] }

You can also get the cached original content with

    GET /<account>/entry/<id>/cache

This returns the complete original document that was fetched at
the time this entry was created.

If you just like to get the meta data (uri, archived, tags and
created date), then use

    GET /<account>/entry/<id>?meta


#### tag / untag a site entry

Adds or removes a tag from a page entry.

    POST /<account>/entry/<id>/[un]tag/?tag=a&tag=b&tag=c&tags=x,y,z&account=&password=
    POST /<account>/entry/<id>/[un]tag/ {  tags: "..." }
    -> JSON { success: true, message: "tags added/removed" }

You can specify multiple `tag` parameters or one `tags` parameter
with a comma separated list of tag names. Tag names must consist
of alphanumeric characters and `-` and `_`. The actions will either
add or remove the specified tags. If you want to do a update of a
complete list use the `tags` endpoint:

    POST /<account>/entry/<id>/tags?tag=a&tag=b&tags=c,d,e

This will remove all existing tags for entry `id` and then add the
given list.

#### get entries

Get a list of page entries as RSS feed or as JSON.

    GET /<account>/entries/rss|json|html?tag=&tag=&tags=&archived&complete
    -> xml rss, json or html

By default each entry is returned without its content. Only title, short-text
and other properties are transferred, because this is usually enough. If the
entries should be complete, specify this with the parameter `complete=true`.

The `archived` parameter controls whether to return archived page entries. Other
filtering is possible via tags. Append one or more `tag` query parameter and/or
one `tags` parameter. The latter may specify a comma separated list of tags.
There is one known tag called "favourite" that returns all "starred" entries.


#### list all tags

Returns a list of used tags or feed urls.

list tag names:

    GET /<account>/tags?filter=
    -> JSON { tags: [] }

The `filter` parameter is a regex string like `fav.*rite`

To get the tags of one entry, use

    GET /<account>/<id>/tags

There is no filter possible.

#### Binary files

Binary files are also stored in sitebag. Currently only images are supported. You can get
them using this url

    /api/<account>/bin/<id> (GET) binary files
    /bin/?url=http://original-image-url

Note that access to these are *not* protected. The `<id>` is the binary id, which is the
md5 checksum of the content.



## Configuration and more Detail

### Mongo DB

All data is pushed to a mongo database. Mongodb must be installed and
running. The data is organized as follows:

    <account>_entries
      _id (= md5 hash of url), title, url, content, read, created, content-id, binary-ids

    <account>_tags
      _id (= tag name), entries: [ hash1, hash2, ... ]

    binaries
      _id (= hash of content), urls, data, md5, contentType, created

Here `<account>` is replaced by the actual account name. The `created`
field is a timestamp denoting the creation of this record.

The collection `<account>_entries` stores all pages that the user
adds. The `content` field is the extracted main content. The original
page is stored in the `binaries` collection, that can be refered to
using the `content-id` field. The `binary-ids` array contains all
ids into the binary collection that this entry uses.

The collection `<account>_tags` contains all tags of an account. A
page is "tagged" by adding the entry `_id` to the `entries` array of
the corresponding tag. Thus, to look up all tags of an entry this
collection must be iterated through, checking the `entries` array for
containment of a given page id.

Images (and other binary content) is stored in a collection global to
all accounts. The id of a binary is the md5 checksum of its content. The
url is also stored for further reference. Binary resources are available
without authentication.

The `binaries` collection as written above is only for documenting the
meta fields. Mongo provides
[GridFS](http://docs.mongodb.org/manual/faq/developers/#faq-developers-when-to-use-gridfs)
for this already.

### sitebag.conf

The file `etc/sitebag.conf` contains all configuration settings. It
first includes all default values which can be overriden.

### Authentication

Authentication is provided by porter. There are two options: You
already have a porter instance running and want sitebag to connect to
it; or you can use an embedded porter instance that is running within
sitebag. With the latter option, sitebag manages its own set of user
accounts.

## Issues and Feedback

Please use the issue tracker for all sorts of things concerning sitebag.
Feedback is always most welcome.

## License

SiteBag is licensed under [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).
