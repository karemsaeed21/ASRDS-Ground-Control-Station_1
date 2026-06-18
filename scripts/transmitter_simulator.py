"""
ASRDS Transmitter Device Simulator
Simulates the transmission device on a virtual COM port (e.g. com0com pair).

Usage:
    python scripts/transmitter_simulator.py --port COM6

Steps (Windows with com0com):
    1. Create a virtual pair, e.g. COM5 <-> COM6
    2. Run this script on COM6 (transmitter side)
    3. In the GCS, click Connect next to Transmitter — it will find COM5
"""

import argparse
import random
import serial
import sys
import time

DEFAULT_PORT = "COM6"
BAUD         = 115200
DEVICE_ID    = "TX001"
TIMEOUT      = 0.5


def wait_for_handshake(ser: serial.Serial) -> int:
    print(f"[TX] Waiting for handshake (HSH,{DEVICE_ID}) ...")
    buf = b""
    while True:
        if ser.in_waiting:
            buf += ser.read(ser.in_waiting)
        else:
            time.sleep(0.05)
            continue

        text = buf.decode("utf-8", errors="ignore")
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
                        print(f"[TX] Session started — ID {session_id}")
                        return session_id
                    else:
                        ser.write(b"ID_MISMATCH\n")
                        ser.flush()
        if len(buf) > 512:
            buf = buf[-256:]


def main():
    parser = argparse.ArgumentParser(description="ASRDS transmitter serial simulator")
    parser.add_argument("--port", default=DEFAULT_PORT, help=f"COM port (default: {DEFAULT_PORT})")
    parser.add_argument("--baud", type=int, default=BAUD, help="Baud rate")
    args = parser.parse_args()

    print(f"[TX] Opening {args.port} at {args.baud} baud ...")
    try:
        ser = serial.Serial(port=args.port, baudrate=args.baud, timeout=TIMEOUT)
    except serial.SerialException as e:
        print(f"[TX] ERROR: {e}", file=sys.stderr)
        sys.exit(1)

    wait_for_handshake(ser)
    print("[TX] Transmitter link active. Press Ctrl+C to stop.\n")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n[TX] Stopped.")
    finally:
        ser.close()


if __name__ == "__main__":
    main()
