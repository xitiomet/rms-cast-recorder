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
{"event":"dcs_detected","dcs":"073","polarity":"normal"}
```

Sent when a configured DCS gate opens.

Fields:
- `event`: always `dcs_detected`
- `dcs`: configured DCS code (octal, zero-padded)
- `polarity`: `normal`, `inverted`, or `unknown`

### DCS gone

```json
{"event":"dcs_gone","dcs":"073","polarity":"normal"}
```

Sent when a configured DCS gate closes.

### CTCSS detected

```json
{"event":"ctcss_detected","ctcss":"100.0"}
```

Sent when a configured CTCSS gate opens.

### CTCSS gone

```json
{"event":"ctcss_gone","ctcss":"100.0"}
```

Sent when a configured CTCSS gate closes.

### Gate closed

```json
{"event":"gate_closed","reason":"dcs"}
```

Sent when audio flow becomes blocked, or when the blocking reason changes.

Reason values:
- `dcs`
- `ctcss`
- `silence`

### Gate open

```json
{"event":"gate_open","reason":"dcs"}
```

Sent when audio flow is no longer blocked.

Field:
- `reason`: the blocker that was just cleared (`dcs`, `ctcss`, or `silence`)

### Audio detected

```json
{"event":"audio_detected"}
```

Sent when a new gated audio clip starts.

### Silence detected

```json
{"event":"silence_detected"}
```

Sent when silence duration threshold is reached and the active clip is closed.

### Hook executed

```json
{"event":"hook_executed","command":"/path/to/hook /path/to/file.wav"}
```

Sent when an `--on-write` hook command is launched.

Fields:
- `event`: always `hook_executed`
- `command`: full command line that is executed

### Hook result

```json
{"event":"hook_result","result":"exit_code=0"}
```

Sent for hook output lines and final status.

Examples:
- `{"event":"hook_result","result":"processing file..."}`
- `{"event":"hook_result","result":"exit_code=0"}`
- `{"event":"hook_result","result":"error: ..."}`

## Example sequence

Typical gated receive cycle (DCS gate configured):

```json
{"event":"dcs_detected","dcs":"073","polarity":"normal"}
{"event":"gate_open","reason":"dcs"}
{"event":"audio_detected"}
{"event":"silence_detected"}
{"event":"gate_closed","reason":"silence"}
{"event":"dcs_gone","dcs":"073","polarity":"normal"}
{"event":"gate_closed","reason":"dcs"}
```

Notes:
- `gate_closed` can occur multiple times in one receive cycle if the blocking reason changes.
- `gate_open` includes the previous blocker in `reason`.

## Notes

- Multiple clients can connect and all receive the same event stream.
- The server accepts incoming messages but currently ignores them.
- If `--api-websocket` is not set, no WebSocket server is started and no API events are published.
