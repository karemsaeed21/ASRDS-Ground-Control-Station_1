"""
ASRDS Ground Station Device Simulator
Simulates the drone-side serial device on a virtual COM port pair (e.g. com0com).

Usage:
    python groundStation.py            # uses default PORT below
    python groundStation.py --port COM9 --rate 2 --moving

Steps (Windows with com0com):
    1. Create a virtual pair in com0com, e.g. COM8 <-> COM9
    2. Run this script on COM9 (the "drone" side)
    3. Launch the GCS app — it will find COM8 and perform the handshake
"""

import argparse
import math
import random
import serial
import sys
import time

# ── defaults ──────────────────────────────────────────────────────────────────
DEFAULT_PORT     = "COM4"
BAUD             = 115200
DEVICE_ID        = "RPI001"
TIMEOUT          = 0.5

# Starting position (Alexandria area, Egypt)
DEFAULT_LAT      = 31.19342
DEFAULT_LON      = 29.90032
DEFAULT_ALT      = 100.0
DEFAULT_RATE     = 2.0          # Hz
SIM_RADIUS_DEG   = 0.002        # circular path radius


# ── handshake ─────────────────────────────────────────────────────────────────

def wait_for_handshake(ser: serial.Serial) -> int:
    """
    Block until the GCS sends HSH,<DEVICE_ID>.
    Replies HSHAC <session_id> and returns the session id.
    """
    print(f"[DEVICE] Waiting for handshake (HSH,{DEVICE_ID}) ...")
    buf = b""
    while True:
        if ser.in_waiting:
            buf += ser.read(ser.in_waiting)
        else:
            time.sleep(0.05)
            continue

        try:
            text = buf.decode("utf-8", errors="ignore")
        except Exception:
            continue

        if "HSH," in text:
            for line in text.splitlines():
                parts = line.strip().split(",")
                if len(parts) >= 2 and parts[0] == "HSH":
                    received_id = parts[1].strip()
                    if received_id == DEVICE_ID:
                        session_id = random.randint(1000, 9999)
                        reply = f"HSHAC {session_id}\n"
                        ser.write(reply.encode("utf-8"))
                        ser.flush()
                        print(f"[DEVICE] Session started — ID {session_id}")
                        return session_id
                    else:
                        ser.write(b"ID_MISMATCH\n")
                        ser.flush()
                        print(f"[DEVICE] ID mismatch: expected {DEVICE_ID}, got {received_id}")

        if len(buf) > 512:
            buf = buf[-256:]


# ── incoming command handler ───────────────────────────────────────────────────

def handle_incoming(ser: serial.Serial, session_id: int):
    """
    Non-blocking read: process any command the GCS sends while we stream.
    Currently handles: GET_STATUS, MISSION (prints receipt), ABORT.
    Returns False if ABORT received (caller should stop streaming).
    """
    if not ser.in_waiting:
        return True
    try:
        raw = ser.read(ser.in_waiting).decode("utf-8", errors="ignore")
    except Exception:
        return True

    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        print(f"[DEVICE] RX: {line}")
        if line.startswith("GET_STATUS"):
            ser.write(f"STATUS OK SID={session_id}\n".encode())
            ser.flush()
        elif line.startswith("MISSION"):
            # Acknowledge reception of mission area
            ser.write(f"MISSION_ACK SID={session_id}\n".encode())
            ser.flush()
            print("[DEVICE] Mission area received and acknowledged.")
        elif line.startswith("START_MISSION"):
            ser.write(f"MISSION_STARTED SID={session_id}\n".encode())
            ser.flush()
            print("[DEVICE] Mission started.")
        elif line.startswith("ABORT"):
            ser.write(f"DECISION_ACK ABORT SID={session_id}\n".encode())
            ser.flush()
            print("[DEVICE] ABORT received — stopping.")
            return False
        elif line.startswith(("RTL", "HOLD", "RESUME", "LAND")):
            cmd = line.split(",")[0]
            ser.write(f"DECISION_ACK {cmd} SID={session_id}\n".encode())
            ser.flush()
            print(f"[DEVICE] Decision {cmd} acknowledged.")
            if cmd in ("RTL", "LAND"):
                return False
    return True


# ── position streaming ────────────────────────────────────────────────────────

def stream_position(ser: serial.Serial, session_id: int,
                    lat: float, lon: float, alt: float,
                    rate: float, moving: bool):
    """
    Stream POS,lat,lon,alt,battery frames to the GCS at the given rate (Hz).
    Also listens for incoming commands on the same port.
    Press Ctrl+C to stop.
    """
    print(f"[DEVICE] Streaming POS at {rate} Hz  (moving={'yes' if moving else 'no'})")
    print("[DEVICE] Press Ctrl+C to stop.\n")

    interval = 1.0 / rate
    t0       = time.monotonic()
    battery  = 100

    try:
        while True:
            loop_start = time.monotonic()
            elapsed    = loop_start - t0

            # Update position
            if moving:
                cur_lat = lat + SIM_RADIUS_DEG * math.sin(elapsed * 0.5)
                cur_lon = lon + SIM_RADIUS_DEG * math.cos(elapsed * 0.5)
                cur_alt = alt + 5.0 * math.sin(elapsed * 0.3)
            else:
                cur_lat, cur_lon, cur_alt = lat, lon, alt

            # Drain battery ~1 % every 2 seconds
            battery = max(0, 100 - int(elapsed / 2))

            line = f"POS,{cur_lat:.6f},{cur_lon:.6f},{cur_alt:.1f},{battery}\n"
            ser.write(line.encode("utf-8"))
            ser.flush()

            # Handle any GCS commands
            if not handle_incoming(ser, session_id):
                break   # ABORT received

            # Sleep for remainder of interval
            sleep_dur = interval - (time.monotonic() - loop_start)
            if sleep_dur > 0:
                time.sleep(sleep_dur)

    except KeyboardInterrupt:
        print("\n[DEVICE] Stopped by user.")
    finally:
        ser.close()


# ── entry point ───────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(description="ASRDS drone-side serial simulator")
    p.add_argument("--port",   default=DEFAULT_PORT, help=f"COM port (default: {DEFAULT_PORT})")
    p.add_argument("--baud",   type=int,   default=BAUD,         help="Baud rate")
    p.add_argument("--rate",   type=float, default=DEFAULT_RATE,  help="POS stream rate in Hz")
    p.add_argument("--lat",    type=float, default=DEFAULT_LAT,   help="Start latitude")
    p.add_argument("--lon",    type=float, default=DEFAULT_LON,   help="Start longitude")
    p.add_argument("--alt",    type=float, default=DEFAULT_ALT,   help="Start altitude (m)")
    p.add_argument("--moving", action="store_true",               help="Simulate circular movement")
    return p.parse_args()


def main():
    args = parse_args()
    print(f"[DEVICE] Opening {args.port} at {args.baud} baud ...")
    try:
        ser = serial.Serial(port=args.port, baudrate=args.baud, timeout=TIMEOUT)
    except serial.SerialException as e:
        print(f"[DEVICE] ERROR: {e}", file=sys.stderr)
        print("[DEVICE] Tip: check com0com pair and that no other app holds the port.")
        sys.exit(1)

    session_id = wait_for_handshake(ser)
    stream_position(ser, session_id,
                    lat=args.lat, lon=args.lon, alt=args.alt,
                    rate=args.rate, moving=args.moving)


if __name__ == "__main__":
    main()
