"""Transport layer between the drone and whoever is on the other end of the
socket — the real Java app, or the bundled simulator. Both are plain TCP
servers speaking the protocol in protocol.py; the drone is always the client
running this exact code, so swapping one for the other requires no branching
here at all (see dronecore/drone.py for the config-driven auto-spawn of the
simulator, which is the only place that cares which one it is).
"""

import queue
import socket
import threading
from abc import ABC, abstractmethod
from typing import Optional

from .protocol import Message
from ..logging_utils import get_logger


class MissionLink(ABC):
    @abstractmethod
    def connect(self, timeout: Optional[float] = None) -> None:
        raise NotImplementedError

    @abstractmethod
    def send(self, message: Message) -> None:
        raise NotImplementedError

    @abstractmethod
    def receive(self, timeout: Optional[float] = None) -> Optional[Message]:
        """Non-blocking-with-timeout receive of the next inbound message, or
        None if nothing arrived within `timeout` seconds (None = block
        forever, 0 = poll once and return immediately)."""
        raise NotImplementedError

    @abstractmethod
    def close(self) -> None:
        raise NotImplementedError

    @property
    @abstractmethod
    def is_connected(self) -> bool:
        raise NotImplementedError


class TcpMissionLink(MissionLink):
    """Drone-side TCP client. Runs a background reader thread so the
    Webots step() loop is never blocked waiting on socket.recv(); inbound
    messages land in a thread-safe queue that `receive()` drains."""

    def __init__(self, host: str, port: int):
        self._host = host
        self._port = port
        self._sock: Optional[socket.socket] = None
        self._reader_thread: Optional[threading.Thread] = None
        self._inbound: "queue.Queue[Message]" = queue.Queue()
        self._connected = False
        self._send_lock = threading.Lock()
        self._logger = get_logger("MissionLink")

    def connect(self, timeout: Optional[float] = None) -> None:
        self._sock = socket.create_connection((self._host, self._port), timeout=timeout)
        self._sock.settimeout(None)
        self._connected = True
        self._reader_thread = threading.Thread(target=self._read_loop, daemon=True)
        self._reader_thread.start()
        self._logger.info(f"Connected to {self._host}:{self._port}")

    def _read_loop(self):
        try:
            stream = self._sock.makefile("r", encoding="utf-8", newline="\n")
            for line in stream:
                line = line.strip()
                if not line:
                    continue
                try:
                    self._inbound.put(Message.from_json_line(line))
                except (ValueError, KeyError) as exc:
                    self._logger.error(f"Malformed inbound message ({exc!r}): {line!r}")
        except OSError:
            pass  # socket closed; fall through to mark disconnected
        finally:
            self._connected = False

    def send(self, message: Message) -> None:
        if not self._connected or self._sock is None:
            raise ConnectionError("MissionLink is not connected")
        data = (message.to_json_line() + "\n").encode("utf-8")
        with self._send_lock:
            self._sock.sendall(data)

    def receive(self, timeout: Optional[float] = None) -> Optional[Message]:
        try:
            return self._inbound.get(timeout=timeout)
        except queue.Empty:
            return None

    def close(self) -> None:
        self._connected = False
        if self._sock is not None:
            try:
                self._sock.close()
            except OSError:
                pass

    @property
    def is_connected(self) -> bool:
        return self._connected
