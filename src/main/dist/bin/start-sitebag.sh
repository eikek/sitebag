#!/bin/sh
usage() {
    echo "Usage: $0 [OPTIONS]

  Options
    -c <file>    The configuration file, default is etc/sitebag.conf.
    -l <file>    The logback configuration file. Default is etc/logback.xml.
    -a           Create an initial admin account (username, password: admin).
"
}

# find utf8 locale and set it
LOC=$(locale -a | grep -i utf8 | head -n1)
if [ -n "$LOC" ]; then
  export LC_ALL=${LOC}
fi

# find working dir and cd into it
cd $(dirname $0)/..

# create classpath param
CPATH=""
SCALALIB=""
for f in lib/*; do
  if [[ ${f} =~ scala-library.* ]]; then
    SCALALIB="$f"
  else
    CPATH=${CPATH}:$f
  fi
done
JCLASSPATH=${SCALALIB}:${CPATH#?}:plugins/*

if [ -z "$SITEBAG_JVM_OPTS" ]; then
    SITEBAG_JVM_OPTS="-server -Xmx512M -Djava.awt.headless=true "
fi

while getopts :c:l:ah flag; do
    case $flag in
        c) SITEBAG_JVM_OPTS="$SITEBAG_JVM_OPTS -Dconfig.file=$OPTARG";;
        l) SITEBAG_JVM_OPTS="$SITEBAG_JVM_OPTS -Dlogback.configurationFile=$OPTARG";;
        a) SITEBAG_JVM_OPTS="$SITEBAG_JVM_OPTS -Dsitebag.create-admin-account=true";;
        h) usage; exit 0;;
    esac
done

if [[ ! $SITEBAG_JVM_OPTS =~ config.file.* ]] && [ -r etc/sitebag.conf ]; then
    SITEBAG_JVM_OPTS="$SITEBAG_JVM_OPTS -Dconfig.file=etc/sitebag.conf"
fi
if [[ ! $SITEBAG_JVM_OPTS =~ logback.* ]] && [ -r etc/logback.xml ]; then
    SITEBAG_JVM_OPTS="$SITEBAG_JVM_OPTS -Dlogback.configurationFile=etc/logback.xml"
fi

# use env for java command
JAVA=java
if [ -n "$JAVA_HOME" ]; then
    JAVA=$JAVA_HOME/bin/java
elif [ -n "$JDK_HOME" ]; then
    JAVA=$JDK_HOME/bin/java
fi

$JAVA ${SITEBAG_JVM_OPTS} -Xbootclasspath/a:${JCLASSPATH} -jar lib/sitebag.jar
