# Drone Mission Protocol — v1.0

This document is the **complete** wire-protocol spec for talking to the
`mavic_mission` Webots drone controller. You should be able to implement the
Java side from this file alone — no need to read any Python.

A Python reference implementation of both ends (the drone's client and a
drop-in server you can run instead of your Java app) lives in
`dronecore/comms/protocol.py`, `mission_link.py`, and `simulator.py`.

If you just want to see it work before writing any Java code, jump to
[Testing without your Java app](#testing-without-your-java-app).

---

## 1. Roles & Transport

- **Your Java application is the TCP server.** It listens on a host:port.
- **The drone is the TCP client.** It connects to you when the mission
  controller starts.
- Default host:port is `127.0.0.1:5050` (configurable on the drone side via
  `config.json` → `connection.host` / `connection.port`; agree on a value
  with whoever configures the drone — if you're both on the same machine,
  the default just works).

## 2. Framing

One UTF-8-encoded JSON object per line, each line terminated by `\n`.
No length prefixes, no binary framing — just read lines.

In Java:
```java
BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
String line = in.readLine();          // one full JSON message
// ... parse `line` with Jackson/Gson/org.json ...

PrintWriter out = new PrintWriter(socket.getOutputStream(), true /* auto-flush */);
out.println(jsonString);              // println adds the \n for you
```

## 3. Envelope

Every message in both directions has this shape:

```json
{
  "protocol_version": "1.0",
  "type": "HUMAN_DETECTED",
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "timestamp": "2026-06-26T10:15:30.123456+00:00",
  "payload": { }
}
```

| Field | Meaning |
|---|---|
| `protocol_version` | Currently always `"1.0"`. Bump only on breaking changes. |
| `type` | One of the message types in section 5/6 below. |
| `id` | A UUID, unique per message. Used to correlate responses — see `in_reply_to`. |
| `timestamp` | ISO-8601, informational only. Don't parse it for logic. |
| `payload` | Type-specific body — see the tables below for every shape. |

Reply messages additionally carry:
```json
"in_reply_to": "<id of the message being answered>"
```

**Unknown message types must be ignored, not treated as errors** — this is
how the protocol can grow later without breaking either side.

## 4. Coordinate system — read this before anything else

**You never need to think about world meters. Your Java app always works in
its own flat `0..1000` coordinate space, on both axes, in both directions.**

Internally, the Webots world is a 960m × 960m square (because that's the
literal size of the desert terrain). The drone controller is the *only*
thing that knows that — every coordinate that crosses the wire is already
converted for you:

- **Coordinates you send** (e.g. in `SEARCH_AREA`) are read as `0..1000` and
  converted to world meters on the drone's side.
- **Coordinates you receive** (e.g. in `STATUS`, `HUMAN_DETECTED`) are
  converted from world meters back to `0..1000` before being sent to you.

So: `(0, 0)` is one corner of the map, `(1000, 1000)` is the opposite
corner, and everything you send or receive about a 2D position on the map
uses that same `0..1000` space, regardless of how big the actual simulated
terrain is or whether it ever changes size. If you're drawing this on a
square map image (e.g. the reference terrain image you were given
separately), `(0,0)`–`(1000,1000)` maps to the image's top-left–bottom-right
corners with a simple linear stretch — no extra math needed on your side.

**The one exception is altitude (`z`).** Altitude is always real-world
meters (e.g. `35.0` means 35 meters above the ground), never `0..1000`,
because the Java app's 2D map doesn't represent altitude at all. Whenever
you see `{"x": ..., "y": ..., "z": ...}` in this doc: `x`/`y` are
`0..1000` map coordinates, `z` is meters.

`altitude_m` (a separate, non-position field used in `SEARCH_AREA`) is also
always meters, for the same reason.

> Implementation note for the curious: the conversion is a simple linear
> map per axis (`world = world_min + (java / 1000) * (world_max -
> world_min)`, and the inverse for the other direction). It lives in
> `dronecore/geometry.py`'s `CoordinateTransform`, configured by the
> `world` section of `config.json`. You don't need to reimplement it —
> just always use `0..1000`.

## 5. Messages: Drone → App

| `type` | When it's sent | Payload shape |
|---|---|---|
| `HELLO` | Immediately after the TCP connection is established. | `{"drone_id": str, "world_name": str}` |
| `SEARCH_AREA_REQUEST` | Right after `HELLO`. The drone is idle on the pad, waiting for you to assign it an area. | `{}` |
| `STATUS` | Periodically throughout the whole mission (every `connection.status_interval_s`, default **0.2s of sim time = 5 times/sec** — this is your real-time position feed; redraw the drone icon on every one of these). | `{"state": str, "position": {"x": float, "y": float, "z": float}, "heading_rad": float}` |
| `HUMAN_DETECTED` | A sighting was confirmed. The drone is now hovering in place and **blocked**, waiting for your `DETECTION_RESPONSE`. | `{"drone_position": {x,y,z}, "estimated_human_position": {x,y,z}, "confidence": float, "sample_image_used": str}` |
| `SEARCH_COMPLETE` | The assigned area was fully covered with no pending/unanswered detection. | `{"area": {"x_min": float, "y_min": float, "x_max": float, "y_max": float}}` |

### 5.1 `HELLO` — example
```json
{
  "protocol_version": "1.0", "type": "HELLO",
  "id": "11111111-1111-1111-1111-111111111111",
  "timestamp": "2026-06-27T09:00:00.000000+00:00",
  "payload": { "drone_id": "Mavic 2 PRO", "world_name": "desert" }
}
```

### 5.2 `SEARCH_AREA_REQUEST` — example
```json
{
  "protocol_version": "1.0", "type": "SEARCH_AREA_REQUEST",
  "id": "22222222-2222-2222-2222-222222222222",
  "timestamp": "2026-06-27T09:00:00.100000+00:00",
  "payload": {}
}
```
Your app should reply with `SEARCH_AREA` (section 6.1) once it has one.

### 5.3 `STATUS` — example
```json
{
  "protocol_version": "1.0", "type": "STATUS",
  "id": "33333333-3333-3333-3333-333333333333",
  "timestamp": "2026-06-27T09:01:12.400000+00:00",
  "payload": {
    "state": "SEARCHING",
    "position": { "x": 612.4, "y": 388.9, "z": 35.0 },
    "heading_rad": 1.02
  }
}
```
- `x`/`y` are `0..1000` map coordinates (see section 4). `z` is meters of
  altitude.
- `state` is one of the drone's mission states (`CONNECTED`,
  `AWAITING_SEARCH_AREA`, `TAKING_OFF`, `AIRBORNE_READY`, `SEARCHING`,
  `AWAITING_DETECTION_DECISION`, `RETURNING_HOME`, `LANDED`, `ABORTED`) —
  purely informational, don't gate logic on it.
- `heading_rad` is yaw in radians, standard math convention (counter-clockwise
  from +X), not compass bearing.
- There is **no acknowledgement** of `STATUS` — it's fire-and-forget. Don't
  reply to it.

### 5.4 `HUMAN_DETECTED` — example
```json
{
  "protocol_version": "1.0", "type": "HUMAN_DETECTED",
  "id": "44444444-4444-4444-4444-444444444444",
  "timestamp": "2026-06-27T09:02:30.000000+00:00",
  "payload": {
    "drone_position": { "x": 530.1, "y": 612.7, "z": 35.0 },
    "estimated_human_position": { "x": 528.4, "y": 615.2, "z": 0.0 },
    "confidence": 0.87,
    "sample_image_used": "person_014.jpg"
  }
}
```
- The drone is now **paused and waiting** — it will not move or continue the
  search until you reply with `DETECTION_RESPONSE` carrying `in_reply_to`
  set to this message's `id` (or until `detection_response_timeout_s`
  elapses, see section 8).
- `confidence` is `0.0`–`1.0` from whichever detection backend is configured
  (ground-truth always reports `1.0`; image-based backends report a real
  score).
- `sample_image_used` is the filename of the photo that confirmed the
  sighting, or `""` if the active backend doesn't use sample photos.

### 5.5 `SEARCH_COMPLETE` — example
```json
{
  "protocol_version": "1.0", "type": "SEARCH_COMPLETE",
  "id": "55555555-5555-5555-5555-555555555555",
  "timestamp": "2026-06-27T09:05:00.000000+00:00",
  "payload": {
    "area": { "x_min": 250.0, "y_min": 250.0, "x_max": 750.0, "y_max": 750.0 }
  }
}
```
`area` simply echoes back (in `0..1000` coordinates) the area you assigned.
No reply needed. The drone is now hovering at `AIRBORNE_READY`; it's up to
your mission script whether/when it calls `return_home()` next.

## 6. Messages: App → Drone

| `type` | When to send it | Payload shape |
|---|---|---|
| `SEARCH_AREA` | In reply to `SEARCH_AREA_REQUEST`. | `{"x_min": float, "y_min": float, "x_max": float, "y_max": float, "altitude_m": float (optional)}` |
| `DETECTION_RESPONSE` | In reply to `HUMAN_DETECTED`. | `{"decision": "CONTINUE_SEARCH" \| "RETURN_HOME"}` + `in_reply_to` set |
| `ABORT` | (Reserved, optional for v1.0.) Force a return-to-home at any point. | `{}` |

### 6.1 `SEARCH_AREA` — example
```json
{
  "protocol_version": "1.0", "type": "SEARCH_AREA",
  "id": "66666666-6666-6666-6666-666666666666",
  "timestamp": "2026-06-27T09:00:01.000000+00:00",
  "payload": {
    "x_min": 250.0, "y_min": 250.0,
    "x_max": 750.0, "y_max": 750.0,
    "altitude_m": 35.0
  }
}
```
- `x_min/y_min/x_max/y_max` are `0..1000` map coordinates — pick any
  rectangle you like, the drone converts it internally.
- `altitude_m` is **optional** — omit the key entirely to let the drone use
  its own configured default cruise altitude.

### 6.2 `DETECTION_RESPONSE` — example
```json
{
  "protocol_version": "1.0", "type": "DETECTION_RESPONSE",
  "id": "77777777-7777-7777-7777-777777777777",
  "timestamp": "2026-06-27T09:02:32.000000+00:00",
  "in_reply_to": "44444444-4444-4444-4444-444444444444",
  "payload": { "decision": "CONTINUE_SEARCH" }
}
```
`in_reply_to` **must** equal the `id` of the `HUMAN_DETECTED` you're
answering — the drone ignores `DETECTION_RESPONSE` messages that don't match
the detection it's currently blocked on.

### 6.3 `ABORT` — example
```json
{
  "protocol_version": "1.0", "type": "ABORT",
  "id": "88888888-8888-8888-8888-888888888888",
  "timestamp": "2026-06-27T09:03:00.000000+00:00",
  "payload": {}
}
```
Optional for v1.0 — only implement this if/when you need a manual "abort
now" button in your UI.

## 7. Sequence of a normal mission

```
Drone                                       App (your Java code)
  | --- TCP connect -------------------->   |
  | --- HELLO --------------------------->  |
  | --- SEARCH_AREA_REQUEST ------------->  |
  |                                         |
  |   <-------------- SEARCH_AREA --------  |   (0..1000 coords + optional altitude_m)
  |                                         |
  | (takes off, begins search)             |
  | --- STATUS (5x/sec) ----------------->  |   <- redraw drone icon every time
  | --- STATUS (5x/sec) ----------------->  |
  |                                         |
  | --- HUMAN_DETECTED ------------------->  |
  |        (drone hovers, blocked)          |
  |   <----------- DETECTION_RESPONSE ----  |   decision: CONTINUE_SEARCH
  | (resumes search)                       |
  | --- STATUS (5x/sec) ----------------->  |
  |                                         |
  | --- SEARCH_COMPLETE ------------------>  |
  | (your script decides whether/when to   |
  |  call return_home(); drone flies home  |
  |  and lands autonomously)               |
```

If a `DETECTION_RESPONSE` has `decision: "RETURN_HOME"`, the drone
immediately flies back to the launch pad and lands — no further `STATUS` or
`SEARCH_COMPLETE` should be expected for that mission.

## 8. Timeouts & defaults you should know about

| Behavior | Default | Notes |
|---|---|---|
| No `DETECTION_RESPONSE` received | 15s of sim time (`connection.detection_response_timeout_s`) | Drone logs a warning and **defaults to `CONTINUE_SEARCH`** rather than hanging forever. Don't rely on this — always reply. |
| `STATUS` cadence | every 0.2s of sim time (`connection.status_interval_s`, 5×/sec) | Fire-and-forget, no reply expected. Tune this in `config.json` if you want it faster/slower. |
| Unknown `type` value | ignored | Lets the protocol grow without breaking either side. |
| Initial connect | drone retries for up to 10s (`connection.connect_timeout_s`) | Have your TCP server listening *before* starting the Webots simulation. |

## 9. Testing without your Java app

Run the bundled Python stand-in server, which speaks exactly this protocol
(including the same `0..1000` coordinate convention):

```
python -m dronecore.comms.simulator --interactive
```

Or non-interactively with a custom default search area (still `0..1000`
coordinates):

```
python -m dronecore.comms.simulator --area 250 250 750 750 --decision return
```

This lets you validate your Java implementation's understanding of the
protocol (point it at this simulator instead of the real drone controller,
or vice versa — point the drone controller at your in-progress Java server)
before both ends exist at once. See `dronecore/comms/simulator.py --help`
for all CLI flags (custom area, scripted decision, response delay, etc).

## 10. Quick checklist for implementing the Java side

- [ ] TCP server listening on the agreed host:port *before* the drone connects.
- [ ] Read loop: `BufferedReader.readLine()` → parse JSON → dispatch on `type`.
- [ ] Write helper: build envelope dict/object → serialize → `println()` (adds `\n`).
- [ ] Handle `HELLO` (log it) and `SEARCH_AREA_REQUEST` (reply with `SEARCH_AREA`, `0..1000` coords).
- [ ] Handle `STATUS` — update a drone icon's `(x, y)` on your map, 5×/sec.
- [ ] Handle `HUMAN_DETECTED` — show the alert, then reply `DETECTION_RESPONSE` with the same `id` in `in_reply_to`.
- [ ] Handle `SEARCH_COMPLETE` — purely informational.
- [ ] Everything coordinate-related stays in `0..1000` on your side. Always. You never compute world meters.
