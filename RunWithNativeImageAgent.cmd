@echo off
java -agentlib:native-image-agent=config-merge-dir=src\main\resources\META-INF\native-image\ -jar target\rms-cast-recorder-1.0.jar %*
