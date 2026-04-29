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

Common fields in every packet:
- `event`: event type
- `pid`: process ID of the running `radio-pipe` instance
- `apiPort`: websocket API port
- `timestamp`: Unix epoch timestamp in milliseconds when the packet is emitted

### Event Type Summary

- `dcsDetected`: configured DCS gate opened
- `dcsGone`: configured DCS gate closed
- `ctcssDetected`: configured CTCSS gate opened
- `ctcssGone`: configured CTCSS gate closed
- `audioDetected`: gated audio clip started
- `silenceDetected`: active clip ended due to silence
- `savedRecording`: clip file written to disk (`filename` included)
- `hookExecuted`: `--on-write` hook process started
- `hookResult`: output line and/or completion from hook process
- `status`: periodic snapshot event (every 250ms)
- `commandResponse`: reply to an incoming command sent by a client (see [Commands](#commands))

### DCS detected

```json
{"event":"dcsDetected","pid":12345,"apiPort":4040,"timestamp":1710938400001,"dcs":"073","polarity":"normal"}
```

Sent when a configured DCS gate opens.

Fields:
- `event`: always `dcsDetected`
- `dcs`: configured DCS code (octal, zero-padded)
- `polarity`: `normal`, `inverted`, or `unknown`

### DCS gone

```json
{"event":"dcsGone","pid":12345,"apiPort":4040,"timestamp":1710938401001,"dcs":"073","polarity":"normal"}
```

Sent when a configured DCS gate closes.

### CTCSS detected

```json
{"event":"ctcssDetected","pid":12345,"apiPort":4040,"timestamp":1710938400002,"ctcss":"100.0"}
```

Sent when a configured CTCSS gate opens.

### CTCSS gone

```json
{"event":"ctcssGone","pid":12345,"apiPort":4040,"timestamp":1710938401002,"ctcss":"100.0"}
```

Sent when a configured CTCSS gate closes.

### Audio detected

```json
{"event":"audioDetected","pid":12345,"apiPort":4040,"timestamp":1710938400004}
```

Sent when a new gated audio clip starts.

### Silence detected

```json
{"event":"silenceDetected","pid":12345,"apiPort":4040,"timestamp":1710938401004}
```

Sent when silence duration threshold is reached and the active clip is closed.

### Hook executed

```json
{"event":"hookExecuted","pid":12345,"apiPort":4040,"timestamp":1710938401005,"command":"/path/to/hook /path/to/file.wav"}
```

Sent when an `--on-write` hook command is launched.

Fields:
- `event`: always `hookExecuted`
- `command`: full command line that is executed

### Saved recording

```json
{"event":"savedRecording","pid":12345,"apiPort":4040,"timestamp":1710938400123,"filename":"/home/user/recordings/2026-03-20/2026-03-20_120000_dev0.wav"}
```

Sent when a clip is successfully written to disk.

Fields:
- `event`: always `savedRecording`
- `filename`: full absolute path to the saved recording file

### Hook result

```json
{"event":"hookResult","pid":12345,"apiPort":4040,"timestamp":1710938401006,"result":"exitCode=0"}
```

Sent for hook output lines and final status.

Examples:
- `{"event":"hookResult","pid":12345,"apiPort":4040,"timestamp":1710938401006,"result":"processing file..."}`
- `{"event":"hookResult","pid":12345,"apiPort":4040,"timestamp":1710938401007,"result":"exitCode=0"}`
- `{"event":"hookResult","pid":12345,"apiPort":4040,"timestamp":1710938401008,"result":"error: ..."}`

### Status (every 250ms)

```json
{"event":"status","streamName":"dev0","source":"stdin:44100,s16le,2","recordingEnabled":true,"recordingsPath":"/home/user/recordings","pid":12345,"apiPort":4040,"timestamp":1710938400123,"dcsGateEnabled":false,"dcs":"073","dcsPolarity":"normal","dcsGate":"open","ctcssGateEnabled":true,"ctcss":"100.0","ctcssGate":"open","rmsGate":"closed","rmsDb":-15.3,"outputDb":-100.0,"gate":"closed","gateReason":"silence","gateSeconds":1.002,"audioDetected":false,"audioDetectedSeconds":1.002,"audioReason":[]}
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
- `outputDb`: RMS level after all gates and post-gate gain (`--gain` / `--auto-gain`) on passing audio; otherwise `-100.0`
- `gate`: overall gate state (open only if all gates open)
- `gateReason`: blocker for overall gate (`dcs`, `ctcss`, `silence`, or `none` when open); precedence is `dcs -> ctcss -> silence`
- `gateSeconds`: seconds since the overall gate entered its current state
- `audioDetected`: `true` when current audio frames pass all gate conditions
- `audioDetectedSeconds`: seconds since `audioDetected` last changed
- `audioReason`: array of open-audio reasons. When the overall gate is open this contains `"dcs"`, `"ctcss"`, and `"rms"`; when the overall gate is closed this is an empty array
- `inputBytesPerSecond`: measured throughput of the raw input source in bytes per second, sampled before any de-jitter buffering, gating, gain, or silence padding

## Example sequence

Typical gated receive cycle (DCS gate configured):

```json
{"event":"dcsDetected","pid":12345,"apiPort":4040,"timestamp":1710938400001,"dcs":"073","polarity":"normal"}
{"event":"audioDetected","pid":12345,"apiPort":4040,"timestamp":1710938400004}
{"event":"silenceDetected","pid":12345,"apiPort":4040,"timestamp":1710938401004}
{"event":"savedRecording","pid":12345,"apiPort":4040,"timestamp":1710938401005,"filename":"/home/user/recordings/2026-03-20/2026-03-20_120000_dev0.wav"}
{"event":"dcsGone","pid":12345,"apiPort":4040,"timestamp":1710938401007,"dcs":"073","polarity":"normal"}
```

Notes:
- Overall gate state and blocker are still available in `status` (`gate`, `gateReason`, and `gateSeconds`).

## Ungated Detection Examples

Live detection continues to populate status packets even when DCS or CTCSS gating is not enabled.

Ungated DCS decode:

```json
{"event":"status","streamName":"dev0","source":"stdin:44100,s16le,2","recordingEnabled":true,"recordingsPath":"/home/user/recordings","pid":12345,"apiPort":4040,"timestamp":1710938400456,"dcsGateEnabled":false,"dcs":"073","dcsPolarity":"inverted","dcsGate":"open","ctcssGateEnabled":false,"ctcss":null,"ctcssGate":"open","rmsGate":"open","rmsDb":-14.8,"outputDb":-14.8,"gate":"open","gateReason":"none","gateSeconds":0.250,"audioDetected":true,"audioDetectedSeconds":0.250,"audioReason":["dcs","ctcss","rms"]}
```

Ungated CTCSS decode:

```json
{"event":"status","streamName":"dev0","source":"http://icecast.example.com:8000/stream.mp3","recordingEnabled":true,"recordingsPath":"/home/user/recordings","pid":12345,"apiPort":4040,"timestamp":1710938400789,"dcsGateEnabled":false,"dcs":null,"dcsPolarity":null,"dcsGate":"open","ctcssGateEnabled":false,"ctcss":"100.0","ctcssGate":"open","rmsGate":"open","rmsDb":-17.1,"outputDb":-17.1,"gate":"open","gateReason":"none","gateSeconds":0.500,"audioDetected":true,"audioDetectedSeconds":0.500,"audioReason":["dcs","ctcss","rms"]}
```

## Commands

Clients can send JSON command messages to change recorder settings while `radio-pipe` is running. Each command is a JSON object with at minimum a `command` field. Successful commands echo their applied value back as a `commandResponse` event broadcast to **all** connected clients.

General request shape:

```json
{"command":"<commandName>","value":<value>}
```

General response shape:

```json
{"event":"commandResponse","command":"<commandName>","value":<applied value>,"pid":12345,"apiPort":4040,"timestamp":1710938401234}
```

If the command fails (unknown command, missing/invalid field, out-of-range value), the response replaces `value` with an `error` field describing the problem:

```json
{"event":"commandResponse","command":"setGain","error":"setGain requires a numeric \"value\" field","pid":12345,"apiPort":4040,"timestamp":1710938401234}
```

Messages that are not valid JSON, or that omit `command`, are silently ignored.

### Command Summary

- `setAutoGain`: enable/disable automatic post-gate gain (`value` boolean)
- `setVoiceFilter`: enable/disable post-gate voice band-pass filter (`value` boolean)
- `setDeemphasis`: enable/disable FM de-emphasis filter (`value` boolean, optional `tau` in µs)
- `setGain`: set fixed post-gate gain in dB (`value` number, range -60..+60)

### setAutoGain

Request:

```json
{"command":"setAutoGain","value":true}
```

Response:

```json
{"event":"commandResponse","command":"setAutoGain","value":true,"pid":12345,"apiPort":4040,"timestamp":1710938401234}
```

### setVoiceFilter

Request:

```json
{"command":"setVoiceFilter","value":true}
```

Response:

```json
{"event":"commandResponse","command":"setVoiceFilter","value":true,"pid":12345,"apiPort":4040,"timestamp":1710938401234}
```

### setDeemphasis

Enable or disable the FM de-emphasis filter. The optional `tau` field overrides the de-emphasis time constant (in microseconds, must satisfy `0 < tau < 1000`); when omitted, the value supplied via `--deemphasis` at startup (default 75 µs) is used.

Request (no tau override):

```json
{"command":"setDeemphasis","value":true}
```

Response:

```json
{"event":"commandResponse","command":"setDeemphasis","value":true,"pid":12345,"apiPort":4040,"timestamp":1710938401234}
```

Request (with tau override, e.g. 50 µs for Europe/Asia):

```json
{"command":"setDeemphasis","value":true,"tau":50}
```

Response (the applied tau in µs is echoed back):

```json
{"event":"commandResponse","command":"setDeemphasis","value":true,"tau":50.0,"pid":12345,"apiPort":4040,"timestamp":1710938401234}
```

### setGain

Set the fixed post-gate gain in dB. `value` must be a finite number between -60 and +60.

Request:

```json
{"command":"setGain","value":-3.5}
```

Response:

```json
{"event":"commandResponse","command":"setGain","value":-3.5,"pid":12345,"apiPort":4040,"timestamp":1710938401234}
```

## Notes

- Multiple clients can connect and all receive the same event stream.
- `commandResponse` events are broadcast to all connected clients, not only the sender.
- Messages that are not valid JSON, or that lack a `command` field, are silently ignored.
- If `--api-websocket` is not set, no WebSocket server is started and no API events are published.
