"""Standalone stand-in for the Java desktop application.

This is a plain TCP *server* that speaks exactly the protocol defined in
protocol.py — the same module the drone-side TcpMissionLink uses — so it is
indistinguishable from the real Java app as far as the drone is concerned.

Run directly for manual testing:
    python -m dronecore.comms.simulator --interactive
    python -m dronecore.comms.simulator --area 250 250 750 750 --decision return

Or let Drone.connect() auto-spawn it (config: connection.mode = "simulation",
connection.auto_start_simulator = true) — in that case it always runs in
scripted (non-interactive) mode, since a background subprocess has no usable
stdin to prompt on.

Coordinates on the wire (SEARCH_AREA, STATUS, HUMAN_DETECTED x/y) are all in
the Java app's 0..1000 map-coordinate space, NOT world meters - this
simulator must use that same space to be a faithful stand-in for the real
Java app. The drone-side Drone class is the only place that converts to/from
world meters (see geometry.CoordinateTransform and config.json -> world).
"""

import argparse
import socket
import sys
import time
from typing import Optional

from .protocol import Message, MessageType, DetectionDecision, search_area, detection_response
from ..logging_utils import get_logger

# Java-space (0..1000), not world meters - roughly the middle half of the map.
DEFAULT_AREA = {"x_min": 250.0, "y_min": 250.0, "x_max": 750.0, "y_max": 750.0}


class JavaAppSimulator:
    """Encapsulates the scripted/interactive decision logic. Kept separate
    from the socket plumbing in `serve_forever()` so the decision policy
    could later be swapped (e.g. for an automated test scenario file)
    without touching the networking code."""

    def __init__(self, host: str, port: int, default_area: dict,
                 default_altitude_m: Optional[float] = None,
                 decision_mode: str = "continue", interactive: bool = False,
                 response_delay_s: float = 1.0):
        self.host = host
        self.port = port
        self.default_area = default_area
        self.default_altitude_m = default_altitude_m
        self.decision_mode = decision_mode
        self.interactive = interactive
        self.response_delay_s = response_delay_s
        self._logger = get_logger("Simulator")

    def serve_forever(self):
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_sock.bind((self.host, self.port))
        server_sock.listen(1)
        self._logger.info(f"Listening on {self.host}:{self.port} "
                           f"({'interactive' if self.interactive else 'scripted'} mode)")

        while True:
            conn, addr = server_sock.accept()
            self._logger.info(f"Drone connected from {addr}")
            try:
                self._handle_connection(conn)
            finally:
                conn.close()
                self._logger.info("Drone disconnected; waiting for next connection")

    def _handle_connection(self, conn: socket.socket):
        stream = conn.makefile("r", encoding="utf-8", newline="\n")
        for line in stream:
            line = line.strip()
            if not line:
                continue
            message = Message.from_json_line(line)
            self._dispatch(message, conn)

    def _dispatch(self, message: Message, conn: socket.socket):
        if message.type == MessageType.HELLO:
            self._logger.info(f"HELLO from drone_id={message.payload.get('drone_id')} "
                               f"world={message.payload.get('world_name')}")
        elif message.type == MessageType.SEARCH_AREA_REQUEST:
            self._send_search_area(conn)
        elif message.type == MessageType.STATUS:
            pos = message.payload.get("position", {})
            self._logger.debug(f"STATUS state={message.payload.get('state')} pos={pos}")
        elif message.type == MessageType.HUMAN_DETECTED:
            self._handle_detection(message, conn)
        elif message.type == MessageType.SEARCH_COMPLETE:
            self._logger.info("Drone reports SEARCH_COMPLETE")
        else:
            self._logger.warning(f"Unhandled message type: {message.type_value()}")

    def _send_search_area(self, conn: socket.socket):
        area = self.default_area
        altitude = self.default_altitude_m
        if self.interactive:
            area, altitude = self._prompt_for_area() or (area, altitude)
        msg = search_area(area, altitude)
        self._send(conn, msg)
        self._logger.info(f"Sent SEARCH_AREA {area} altitude_m={altitude}")

    def _prompt_for_area(self):
        raw = input(
            "Enter search area as 'x_min y_min x_max y_max' in Java map "
            f"coordinates 0-1000 (blank = default {self.default_area}): "
        ).strip()
        if not raw:
            return None
        try:
            x_min, y_min, x_max, y_max = (float(v) for v in raw.split())
            return {"x_min": x_min, "y_min": y_min, "x_max": x_max, "y_max": y_max}, self.default_altitude_m
        except ValueError:
            print("Could not parse that area, using default.")
            return None

    def _handle_detection(self, message: Message, conn: socket.socket):
        self._logger.info(f"HUMAN_DETECTED: {message.payload}")
        if self.interactive:
            choice = input("  -> [c]ontinue search / [r]eturn home (default c): ").strip().lower()
            decision = DetectionDecision.RETURN_HOME if choice.startswith("r") else DetectionDecision.CONTINUE_SEARCH
        else:
            time.sleep(self.response_delay_s)
            decision = (DetectionDecision.RETURN_HOME if self.decision_mode == "return"
                        else DetectionDecision.CONTINUE_SEARCH)
        self._send(conn, detection_response(decision, in_reply_to=message.id))
        self._logger.info(f"Replied DETECTION_RESPONSE decision={decision.value}")

    @staticmethod
    def _send(conn: socket.socket, message: Message):
        conn.sendall((message.to_json_line() + "\n").encode("utf-8"))


def _parse_args(argv):
    parser = argparse.ArgumentParser(description="Java-app stand-in simulator for the Mavic mission protocol.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5050)
    parser.add_argument("--area", type=float, nargs=4, metavar=("X_MIN", "Y_MIN", "X_MAX", "Y_MAX"), default=None,
                         help="Java map coordinates (0-1000 on each axis), not world meters.")
    parser.add_argument("--altitude", type=float, default=None)
    parser.add_argument("--interactive", action="store_true", default=False)
    parser.add_argument("--decision", choices=["continue", "return"], default="continue",
                         help="Scripted-mode response to every detection (ignored in --interactive mode).")
    parser.add_argument("--response-delay", type=float, default=1.0,
                         help="Seconds to wait before auto-replying in scripted mode.")
    return parser.parse_args(argv)


def main(argv=None):
    args = _parse_args(argv if argv is not None else sys.argv[1:])
    area = DEFAULT_AREA
    if args.area:
        x_min, y_min, x_max, y_max = args.area
        area = {"x_min": x_min, "y_min": y_min, "x_max": x_max, "y_max": y_max}

    sim = JavaAppSimulator(
        host=args.host, port=args.port, default_area=area, default_altitude_m=args.altitude,
        decision_mode=args.decision, interactive=args.interactive, response_delay_s=args.response_delay,
    )
    try:
        sim.serve_forever()
    except KeyboardInterrupt:
        print("\nSimulator stopped.")


if __name__ == "__main__":
    main()
