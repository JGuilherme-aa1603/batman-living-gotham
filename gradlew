#!/bin/sh

# Minimal POSIX launcher from the Gradle 8.8 wrapper distribution.
APP_HOME=$(cd "${0%/*}" >/dev/null 2>&1 && pwd -P)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi

if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: JAVA_HOME does not point to an executable Java: $JAVACMD" >&2
    exit 1
fi

exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS -Dorg.gradle.appname=gradlew \
    -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
