# Heroku Deployment

Sitebag can be deployed to heroku with a little configuration
changes. It follows
[getting started with scala](https://devcenter.heroku.com/articles/getting-started-with-scala)
guide quite closely.

See the commit
[7d0ae9af](https://github.com/eikek/sitebag/commit/7d0ae9af33cd9e8260e7cd2e0157b4ecd5162a46)
(on branch _heroku_) for an example. It is summarized below.

## Prerequisites

A few changes to the source repository are necessary. In short, the
steps are:

1. Create a file `Procfile` in source root with one line `web: target/start`
2. Create a file `system.properties` in source root with the line
   `java.runtime.version=1.7`
3. Add the [sbt-start-script](https://github.com/sbt/sbt-start-script)
   plugin to `project/plugins.sbt`:

       addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")

   and add its settings in `project/Sitebag.scala`
4. Disable the `gitRevKey` in `project/Sitebag.scala` that adds the
   current revision for `sbt-buildinfo` plugin. There was an error
   while deploying when invoking git inside the deploy process.

## Configure App on heroku

You need first to create the app via `heroku create`. Then in the
dashboard, you need to select a mongo db addon. Which one doesn't
matter, for this example I used _MongoSoup_.

MongoSoup adds a new environment variable `MONGOSOUP_URL`. For
sitebag, the db name must be set, too. So simply create another
environment variable `MONGO_DBNAME` and set it to the db name that was
assigned to you (the last part in `MONGOSOUP_URL`).

Update the environment variabel `JAVA_OPTS` and add the following
properties:

    -Dconfig.file=./heroku-sitebag.conf -Dlogback.configurationFile=./src/main/dist/etc/logback.xml

Now the only missing piece is the file `heroku-sitebag.conf` which
overrides sitebag's default configuration. The important parts are the
mongo url that need to be changed at two locations in this file. It
could look like this:


    sitebag {
    ## these settings are adopted for deployment on heroku
    ## The MONGOSOUP_URL is provided by the addon "MongoSoup"
    ## The MONGO_DBNAME variable was created manually at heroku's dashboard
    ##

      mongodb-url = ${MONGOSOUP_URL}
      dbname = ${MONGO_DBNAME}

      bind-port = ${PORT}
      url-base = "http://sitebag.herokuapp.com/"
      create-admin-account=true

      webui {
        brandname = "SiteBag Demo"
        enable-change-password = false
      }
      porter {
        # remote or embedded
        mode = "embedded"
        embedded {
          # mutable and read-only store implementations can be specified here.
          stores: [
            {
              class: "porter.app.MongoStore",
              params: {
                uri = ${MONGOSOUP_URL}
                dbname = ${MONGO_DBNAME}
              },
              realms: []
            }
          ]
          telnet {
            enabled = false
          }
        }
      }
    }

You need to change (at least) `url-base`.
