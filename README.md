## RMSCastRecorder

A Fun Program

To compile this project please run:
```bash
$ mvn package
```
from a terminal.

## Usage

Once the jar is built you can run the recorder against any Shoutcast/Icecast stream URL.  Recordings are broken
into WAV files whenever the stream goes silent and are placed in day‑based folders.

Example:
```bash
$ java -jar target/rms-cast-recorder-1.0.jar \
      -u http://example.com:8000/stream.mp3 \
      -o recordings
```

Options:

* `-u,--url <URL>` – stream to capture (required)
* `-o,--out <DIR>` – base directory for recordings (default `.`)
* `-t,--threshold <DB>` – silence threshold in dB (default -50)
* `-s,--silence <SECONDS>` – how long the signal must stay below threshold to
  end a clip (default 2)
* `-?,--help` – display help and exit
