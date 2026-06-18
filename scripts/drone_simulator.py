#!/usr/bin/env python3
"""
Simulates the drone side for the ASRDS Ground Control Station:
1. Responds to HandShake (HSH,RPI001 -> HSHAC <session_id>)
2. Streams position updates: POS,lat,lon,alt

Use with a virtual serial pair so the GCS and simulator talk to each other.

  Linux/macOS (create pair in one terminal):
    socat -d -d pty,raw,echo=0 pty,raw,echo=0
    # Use the second pty for this script, e.g. /dev/pts/2

  Windows:
    Use com0com or similar to create COM3<->COM4, then run script on one side.

  Run:
    python drone_simulator.py /dev/pts/2
    # or: python drone_simulator.py COM4
"""

import argparse
import math
import serial
import sys
import time


BAUD = 115200
HANDSHAKE_PREFIX = "HSH,"
HANDSHAKE_RESPONSE = "HSHAC"
DEFAULT_LAT = 30.417832665779233
DEFAULT_LON = 30.35480061331367
DEFAULT_ALT = 100.0


def main():
    parser = argparse.ArgumentParser(
        description="Simulate drone: handshake + POS stream for ASRDS GCS"
    )
    parser.add_argument(
        "port",
        help="Serial port (e.g. /dev/pts/2 or COM4). Use the 'drone' end of a virtual pair.",
    )
    parser.add_argument(
        "--baud",
        type=int,
        default=BAUD,
        help=f"Baud rate (default: {BAUD})",
    )
    parser.add_argument(
        "--rate",
        type=float,
        default=2.0,
        help="Position send rate in Hz (default: 2)",
    )
    parser.add_argument(
        "--moving",
        action="store_true",
        help="Simulate movement: small circular path so path is visible on map",
    )
    parser.add_argument(
        "--lat",
        type=float,
        default=DEFAULT_LAT,
        help=f"Initial latitude (default: {DEFAULT_LAT})",
    )
    parser.add_argument(
        "--lon",
        type=float,
        default=DEFAULT_LON,
        help=f"Initial longitude (default: {DEFAULT_LON})",
    )
    parser.add_argument(
        "--alt",
        type=float,
        default=DEFAULT_ALT,
        help=f"Altitude in m (default: {DEFAULT_ALT})",
    )
    args = parser.parse_args()

    try:
        ser = serial.Serial(args.port, args.baud, timeout=0.5)
    except serial.SerialException as e:
        print(f"Error opening {args.port}: {e}", file=sys.stderr)
        if "Permission denied" in str(e) or "Errno 13" in str(e):
            print("Tip: try the OTHER pty from socat (e.g. /dev/ttys001 instead of ttys002).", file=sys.stderr)
        else:
            print("Tip: use a virtual serial pair (socat on Linux/Mac, com0com on Windows).", file=sys.stderr)
        sys.exit(1)

    print(f"Opened {args.port} at {args.baud} baud. Waiting for handshake (HSH,RPI001)...")

    # Wait for handshake from GCS
    buffer = b""
    session_id = 1
    handshake_done = False
    while not handshake_done:
        if ser.in_waiting:
            buffer += ser.read(ser.in_waiting)
        else:
            time.sleep(0.05)
            continue
        try:
            text = buffer.decode("utf-8", errors="ignore")
        except Exception:
            continue
        if HANDSHAKE_PREFIX in text:
            # Respond so GCS accepts the connection
            response = f"{HANDSHAKE_RESPONSE} {session_id}\n"
            ser.write(response.encode("utf-8"))
            ser.flush()
            print(f"Handshake replied: {response.strip()}")
            handshake_done = True
        # Keep a small buffer to avoid growing forever
        if len(buffer) > 256:
            buffer = buffer[-128:]

    print("Streaming position (POS,lat,lon,alt). Press Ctrl+C to stop.")
    interval = 1.0 / args.rate
    t0 = time.monotonic()
    rx_buf = b""
    streaming = True

    def handle_commands(text: str) -> bool:
        nonlocal streaming
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith(HANDSHAKE_PREFIX):
                continue
            print(f"[DEVICE] RX: {line}")
            if line.startswith("GET_STATUS"):
                ser.write(f"STATUS OK SID={session_id}\n".encode())
                ser.flush()
            elif line.startswith("MISSION"):
                ser.write(f"MISSION_ACK SID={session_id}\n".encode())
                ser.flush()
            elif line.startswith("START_MISSION"):
                ser.write(f"MISSION_STARTED SID={session_id}\n".encode())
                ser.flush()
            elif line.startswith("ABORT"):
                ser.write(f"DECISION_ACK ABORT SID={session_id}\n".encode())
                ser.flush()
                streaming = False
                return False
            elif line.startswith(("RTL", "HOLD", "RESUME", "LAND")):
                cmd = line.split(",")[0]
                ser.write(f"DECISION_ACK {cmd} SID={session_id}\n".encode())
                ser.flush()
                if cmd in ("RTL", "LAND"):
                    streaming = False
                    return False
        return True

    try:
        while streaming:
            now = time.monotonic()
            elapsed = now - t0

            # Check for a re-handshake from the GCS (disconnect → reconnect)
            if ser.in_waiting:
                rx_buf += ser.read(ser.in_waiting)
                if len(rx_buf) > 256:
                    rx_buf = rx_buf[-128:]
                text = rx_buf.decode("utf-8", errors="ignore")
                if HANDSHAKE_PREFIX in text:
                    response = f"{HANDSHAKE_RESPONSE} {session_id}\n"
                    ser.write(response.encode("utf-8"))
                    ser.flush()
                    print(f"Re-handshake replied: {response.strip()}")
                    rx_buf = b""
                elif not handle_commands(text):
                    break

            if args.moving:
                # Small circle so the path is visible (~200m radius)
                radius_deg = 0.002
                lat = args.lat + radius_deg * math.sin(elapsed * 0.5)
                lon = args.lon + radius_deg * math.cos(elapsed * 0.5)
                alt = args.alt + 5.0 * math.sin(elapsed * 0.3)
            else:
                lat, lon, alt = args.lat, args.lon, args.alt

            line = f"POS,{lat:.6f},{lon:.6f},{alt:.1f}\n"
            ser.write(line.encode("utf-8"))
            ser.flush()

            next_until = t0 + (int(elapsed / interval) + 1) * interval
            sleep_time = next_until - time.monotonic()
            if sleep_time > 0:
                time.sleep(sleep_time)
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        ser.close()


if __name__ == "__main__":
    main()
