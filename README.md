# ASRDS Ground Control Station

A JavaFX-based ground control station for the Autonomous Search and Rescue Drone System (ASRDS).  
It connects to a drone over serial, displays live telemetry (GPS position, altitude, battery), tracks the flight path on an interactive map, and transmits the search-area mission polygon.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java JDK | 21 | Must be JDK 21, not just JRE |
| Maven | 3.8+ | Or use the included `mvnw` / `mvnw.cmd` wrapper — no install needed |
| Python | 3.8+ | Only for the simulator mode |
| pyserial | ≥ 3.5 | `pip install pyserial` or see below |
| com0com | any | Windows only — creates a virtual COM pair for the simulator |

> **Java 21 download:** https://adoptium.net  
> **com0com download:** https://sourceforge.net/projects/com0com

---

## Build

```bash
# Windows
.\mvnw.cmd clean compile

# Linux / macOS
./mvnw clean compile
```

Maven downloads all dependencies automatically on the first run (Gluon Maps, jSerialComm, org.json).

---

## Run Modes

There are two ways to run the GCS — pick whichever suits your situation.

---

### Mode 1 — Built-in Simulate (no hardware, no Python)

The GCS has a built-in drone simulator. No serial port, no Python script, no extra tools required.

```bash
# Windows
.\mvnw.cmd clean javafx:run

# Linux / macOS
./mvnw clean javafx:run
```

1. The app opens maximised.
2. Click **Simulate** next to **Drone** in the Connection State panel.
3. The status turns orange (`Simulating`), a red dot appears on the map and traces a circular path.
4. The right panel shows live **Latitude / Longitude / Altitude** and a **battery bar** draining from 100 % to 0 % over ~3 minutes.
5. Click **Stop Simulate** to end the session.

![Built-in Simulate demo](resources/Built-in%20Simulate.gif)

---

### Mode 2 — Python Simulator + com0com (realistic serial link)

This mode uses a virtual COM port pair so the Python simulator and the GCS talk over a real serial driver — the same path the physical drone will use.

#### Step 1 — Create a virtual COM pair (Windows, one time)

Install **com0com** and create a pair, e.g. **COM3 ↔ COM4**.  
The GCS will connect on COM3; the simulator runs on COM4.

#### Step 2 — Install Python dependencies (one time)

```bash
pip install -r scripts/requirements.txt
```

Or with a virtual environment:

```bash
python -m venv scripts/venv

# Windows
scripts\venv\Scripts\activate

# Linux / macOS
source scripts/venv/bin/activate

pip install -r scripts/requirements.txt
```

#### Step 3 — Start the simulator (Terminal 1)

```bash
# Windows
python scripts/drone_simulator.py COM4 --moving

# Linux / macOS  (replace /dev/ttys029 with your socat pty)
python3 scripts/drone_simulator.py /dev/ttys029 --moving
```

You should see:

```
Opened COM4 at 115200 baud. Waiting for handshake (HSH,RPI001)...
```

Leave this terminal open.

> **Linux / macOS — create the pty pair first:**
> ```bash
> socat -d -d pty,raw,echo=0 pty,raw,echo=0
> ```
> Note the two device names printed (e.g. `/dev/ttys001` and `/dev/ttys029`).  
> Pass the **second** one to the simulator; the GCS will find the first automatically.

#### Step 4 — Start the GCS (Terminal 2)

```bash
# Windows
.\mvnw.cmd clean javafx:run

# Linux / macOS
./mvnw clean javafx:run
```

#### Step 5 — Connect

1. Click **Connect** next to **Drone** in the Connection State panel.
2. The GCS scans all COM ports and performs the handshake.

**Simulator terminal** will print:
```
Handshake replied: HSHAC 1
Streaming position (POS,lat,lon,alt). Press Ctrl+C to stop.
```

**GCS window** will show:
- Drone status turns **green** (`Connected`)
- A **red dot** appears on the map and moves along a circular path
- The right panel updates with live **Lat / Lon / Alt** values

You can click **Disconnect** and then **Connect** again at any time — the simulator handles re-handshaking automatically.

![Python simulator + com0com demo](resources/script%20python%20+%20com0com.gif)

---

## Sending a Mission Area

1. Enter the four corner coordinates of the search polygon in the **Search Area Coordinates** panel (Latitude / Longitude for each point X1–X4), or click **Listen** next to any point and click on the map.
2. Click **Set Search Area** to draw the polygon on the map.
3. Once connected (or simulating), click **SEND MISSION AREA** in the right panel.  
   The status label changes to `SENT ✓` on success.

---

## Serial Protocol Reference

| Direction | Message | Description |
|-----------|---------|-------------|
| GCS → Drone | `HSH,RPI001` | Handshake initiation |
| Drone → GCS | `HSHAC <id>` | Handshake acknowledge, `<id>` = session integer |
| Drone → GCS | `POS,lat,lon,alt[,battery]` | Position frame at 115200 baud, newline-terminated |
| GCS → Drone | `MISSION,<id>,lat1,lon1,...,lat4,lon4` | 4-corner search polygon |
| GCS → Drone | `GET_STATUS,<id>` | One-shot status ping |

---

