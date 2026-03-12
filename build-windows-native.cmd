@echo off
cd target
native-image --enable-url-protocols=http,https -jar rms-cast-recorder-1.0.jar
