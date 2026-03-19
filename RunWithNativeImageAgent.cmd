@echo off
java -agentlib:native-image-agent=config-merge-dir=src\main\resources\META-INF\native-image\ -jar target\radio-pipe-1.0.jar %*
