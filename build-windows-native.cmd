@echo off
cd target
native-image --enable-url-protocols=http,https -H:+AllowDeprecatedBuilderClassesOnImageClasspath -jar radio-pipe-1.0.jar
cd ..\
