"""Minimal leveled logger built on print(), matching this project's existing
zero-dependency style (see controllers/mavic_patrol/mavic_patrol.py) while
giving structured, filterable output instead of bare print() calls."""

import sys
import time

_LEVELS = {"DEBUG": 10, "INFO": 20, "WARNING": 30, "ERROR": 40}


class Logger:
    def __init__(self, name: str, level: str = "INFO"):
        self.name = name
        self.threshold = _LEVELS.get(level.upper(), _LEVELS["INFO"])

    def _emit(self, level: str, message: str):
        if _LEVELS[level] < self.threshold:
            return
        ts = time.strftime("%H:%M:%S")
        stream = sys.stderr if level in ("WARNING", "ERROR") else sys.stdout
        print(f"[{ts}] {level:7s} {self.name}: {message}", file=stream)

    def debug(self, message: str):
        self._emit("DEBUG", message)

    def info(self, message: str):
        self._emit("INFO", message)

    def warning(self, message: str):
        self._emit("WARNING", message)

    def error(self, message: str):
        self._emit("ERROR", message)


_default_level = "INFO"


def configure(level: str):
    """Set the default level for any logger created after this call."""
    global _default_level
    _default_level = level


def get_logger(name: str, level: str = None) -> Logger:
    return Logger(name, level or _default_level)
