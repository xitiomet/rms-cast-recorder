# Radio Pipe WebSocket API

When `--api-websocket host:port` is provided, Radio Pipe starts an embedded WebSocket server and publishes real-time JSON event messages.

## Enable

```bash
radio-pipe --stdin --api-websocket 0.0.0.0:9000
```

- WebSocket URL format: `ws://host:port`
- Example: `ws://127.0.0.1:9000`

## Event Messages

All messages are JSON objects.

### DCS detected

```json
{"event":"dcsDetected","dcs":"073","polarity":"normal"}
```

Sent when a configured DCS gate opens.

Fields:
- `event`: always `dcsDetected`
- `dcs`: configured DCS code (octal, zero-padded)
- `polarity`: `normal`, `inverted`, or `unknown`

### DCS gone

```json
{"event":"dcsGone","dcs":"073","polarity":"normal"}
```

Sent when a configured DCS gate closes.

### CTCSS detected

```json
{"event":"ctcssDetected","ctcss":"100.0"}
```

Sent when a configured CTCSS gate opens.

### CTCSS gone

```json
{"event":"ctcssGone","ctcss":"100.0"}
```

Sent when a configured CTCSS gate closes.

### Gate closed

```json
{"event":"gateClosed","reason":"dcs"}
```

Sent when audio flow becomes blocked, or when the blocking reason changes.

Reason values:
- `dcs`
- `ctcss`
- `silence`

### Gate open

```json
{"event":"gateOpen","reason":"dcs"}
```

Sent when audio flow is no longer blocked.

Field:
- `reason`: the blocker that was just cleared (`dcs`, `ctcss`, or `silence`)

### Audio detected

```json
{"event":"audioDetected"}
```

Sent when a new gated audio clip starts.

### Silence detected

```json
{"event":"silenceDetected"}
```

Sent when silence duration threshold is reached and the active clip is closed.

### Hook executed

```json
{"event":"hookExecuted","command":"/path/to/hook /path/to/file.wav"}
```

Sent when an `--on-write` hook command is launched.

Fields:
- `event`: always `hookExecuted`
- `command`: full command line that is executed

### Hook result

```json
{"event":"hookResult","result":"exitCode=0"}
```

Sent for hook output lines and final status.

Examples:
- `{"event":"hookResult","result":"processing file..."}`
- `{"event":"hookResult","result":"exitCode=0"}`
- `{"event":"hookResult","result":"error: ..."}`

### Status (every 250ms)

```json
{"event":"status","streamName":"dev0","source":"stdin:44100,s16le,2","recordingEnabled":true,"recordingsPath":"/home/user/recordings","pid":12345,"apiPort":4040,"dcsGateEnabled":false,"dcs":"073","dcsPolarity":"normal","dcsGate":"open","ctcssGateEnabled":true,"ctcss":"100.0","ctcssGate":"open","rmsGate":"closed","rmsDb":-15.3,"gate":"closed","gateReason":"silence","gateSeconds":1.002,"audioDetected":false,"audioDetectedSeconds":1.002}
```

Sent every 250 milliseconds while the recorder is running.

Fields:
- `event`: always `status`
- `streamName`: stream title (from ICY headers or `--name` override)
- `source`: source URL or `stdin:RATE,ENCODING,CHANNELS` (e.g., `stdin:44100,s16le,2` or `http://icecast.example.com:8000/stream.mp3`)
- `recordingEnabled`: `true` when clip files are being written to a recordings directory, `false` in stdout-only mode without disk recording
- `recordingsPath`: absolute path to the recordings directory, or `null` if not recording to disk
- `pid`: process ID of the radio-pipe instance
- `apiPort`: WebSocket server port
- `dcsGateEnabled`: `true` when `--dcs` is configured
- `dcs`: currently detected DCS code, or `null` if none is currently detected. This can be populated even when the DCS gate is disabled.
- `dcsPolarity`: currently detected DCS polarity (`normal`, `inverted`, or `unknown`), or `null` if no DCS is currently detected
- `dcsGate`: `open` or `closed`
- `ctcssGateEnabled`: `true` when `--ctcss` is configured
- `ctcss`: currently detected CTCSS tone, or `null` if none is currently detected. This can be populated even when the CTCSS gate is disabled.
- `ctcssGate`: `open` or `closed`
- `rmsGate`: RMS/silence gate (`open` when audio is loud enough, `closed` when silent)
- `rmsDb`: current RMS level in decibels (dB)
- `gate`: overall gate state (open only if all gates open)
- `gateReason`: blocker for overall gate (`dcs`, `ctcss`, `silence`, or `none` when open)
- `gateSeconds`: seconds since the overall gate entered its current state
- `audioDetected`: `true` when current audio frames pass all gate conditions
- `audioDetectedSeconds`: seconds since `audioDetected` last changed

## Example sequence

Typical gated receive cycle (DCS gate configured):

```json
{"event":"dcsDetected","dcs":"073","polarity":"normal"}
{"event":"gateOpen","reason":"dcs"}
{"event":"audioDetected"}
{"event":"silenceDetected"}
{"event":"gateClosed","reason":"silence"}
{"event":"dcsGone","dcs":"073","polarity":"normal"}
{"event":"gateClosed","reason":"dcs"}
```

Notes:
- `gateClosed` can occur multiple times in one receive cycle if the blocking reason changes.
- `gateOpen` includes the previous blocker in `reason`.

## Ungated Detection Examples

Live detection continues to populate status packets even when DCS or CTCSS gating is not enabled.

Ungated DCS decode:

```json
{"event":"status","streamName":"dev0","source":"stdin:44100,s16le,2","recordingEnabled":true,"recordingsPath":"/home/user/recordings","pid":12345,"apiPort":4040,"dcsGateEnabled":false,"dcs":"073","dcsPolarity":"inverted","dcsGate":"open","ctcssGateEnabled":false,"ctcss":null,"ctcssGate":"open","rmsGate":"open","rmsDb":-14.8,"gate":"open","gateReason":"none","gateSeconds":0.250,"audioDetected":true,"audioDetectedSeconds":0.250}
```

Ungated CTCSS decode:

```json
{"event":"status","streamName":"dev0","source":"http://icecast.example.com:8000/stream.mp3","recordingEnabled":true,"recordingsPath":"/home/user/recordings","pid":12345,"apiPort":4040,"dcsGateEnabled":false,"dcs":null,"dcsPolarity":null,"dcsGate":"open","ctcssGateEnabled":false,"ctcss":"100.0","ctcssGate":"open","rmsGate":"open","rmsDb":-17.1,"gate":"open","gateReason":"none","gateSeconds":0.500,"audioDetected":true,"audioDetectedSeconds":0.500}
```

## Notes

- Multiple clients can connect and all receive the same event stream.
- The server accepts incoming messages but currently ignores them.
- If `--api-websocket` is not set, no WebSocket server is started and no API events are published.
