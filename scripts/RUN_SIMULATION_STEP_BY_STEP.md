# Run Drone Simulation — Step by Step

Do these steps in order. Use **3 terminals**.

---

## Step 1: Terminal 1 — Create the virtual serial pair

Open **Terminal 1** and run:

```bash
socat -d -d pty,raw,echo=0 pty,raw,echo=0
```

You will see two lines like:

```
2026/03/14 08:52:46 socat[29847] N PTY is /dev/ttys001
2026/03/14 08:52:46 socat[29847] N PTY is /dev/ttys029
```

**Leave this terminal open.** Do not press Ctrl+C. Note the two device names (e.g. `ttys001` and `ttys029`).

---

## Step 2: Terminal 2 — Start the drone simulator

Open **Terminal 2** and run these commands **one after the other**:

```bash
cd /Users/karem/Documents/GitHub/ASRDS-Ground-Control-Station_1
source scripts/venv/bin/activate
python3 scripts/drone_simulator.py /dev/ttys029 --moving
```

**Important:** Use the **second** device from Step 1 (e.g. `/dev/ttys029`), not the first. That way the GCS will open the first device and receive the handshake reply correctly.

If you get **Permission denied**, try the other device (e.g. `/dev/ttys001`).

When it works you will see:

```
Opened /dev/ttys029 at 115200 baud. Waiting for handshake (HSH,RPI001)...
```

**Leave this terminal open.**

---

## Step 3: Terminal 3 — Start the Ground Control Station

Open **Terminal 3** and run:

```bash
cd /Users/karem/Documents/GitHub/ASRDS-Ground-Control-Station_1
./mvnw clean javafx:run
```

Wait until the Ground Station window opens (map and connection panel).

---

## Step 4: Connect in the GCS

In the **Ground Station window**:

1. Click the red **Connect** button next to **Drone** (not Transmitter).
2. A small “Connecting to drone...” popup may appear and then close.

**You should see:**

- **Terminal 2 (simulator):**  
  `Handshake replied: HSHAC 1`  
  then: `Streaming position (POS,lat,lon,alt). Press Ctrl+C to stop.`

- **Terminal 3 (GCS):**  
  `[Drone] HandShake OK on fallback port /dev/ttys001` (or similar)  
  then: `[Drone] HandShake finished: connected`

- **GCS window:**  
  Drone status **Connected** (green), and on the map a **red dot** (drone) and **yellow path** that grows.

---

## Step 5: Stop when finished

- **Terminal 2:** Press **Ctrl+C** to stop the simulator.
- **GCS:** Click **Disconnect** next to Drone (red dot and path disappear).
- **GCS:** Close the app window.
- **Terminal 1:** Press **Ctrl+C** to stop socat.

---

## Quick reference

| Terminal | Command | When to stop |
|----------|---------|--------------|
| 1 | `socat -d -d pty,raw,echo=0 pty,raw,echo=0` | When done (Ctrl+C) |
| 2 | `cd ... && source scripts/venv/bin/activate && python3 scripts/drone_simulator.py /dev/ttys001 --moving` | When done (Ctrl+C) |
| 3 | `cd ... && ./mvnw clean javafx:run` | Close app window |

**If you don’t have a venv yet** (first time only), in the project folder run:

```bash
python3 -m venv scripts/venv
scripts/venv/bin/pip install -r scripts/requirements.txt
```

Then continue from Step 2.
