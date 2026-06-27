"""Explicit mission state machine.

This is what makes the public `Drone` API safe to script against without
reading its internals: every method asserts the states it's legal to call
from, and every transition is logged and validated against a fixed table —
calling `drone.search(area)` before `drone.connect()` raises a clear error
instead of doing something undefined.
"""

from enum import Enum
from typing import Optional

from .logging_utils import get_logger


class MissionState(Enum):
    DISCONNECTED = "DISCONNECTED"
    CONNECTING = "CONNECTING"
    CONNECTED = "CONNECTED"
    AWAITING_SEARCH_AREA = "AWAITING_SEARCH_AREA"
    TAKING_OFF = "TAKING_OFF"
    AIRBORNE_READY = "AIRBORNE_READY"      # airborne, idle, ready for the next instruction
    SEARCHING = "SEARCHING"
    AWAITING_DETECTION_DECISION = "AWAITING_DETECTION_DECISION"
    RETURNING_HOME = "RETURNING_HOME"
    LANDED = "LANDED"
    ABORTED = "ABORTED"


class MissionStateError(Exception):
    """Raised when a Drone method is called while the mission is in a state
    that doesn't permit it."""


_ALLOWED_TRANSITIONS = {
    MissionState.DISCONNECTED: {MissionState.CONNECTING},
    MissionState.CONNECTING: {MissionState.CONNECTED, MissionState.ABORTED},
    MissionState.CONNECTED: {MissionState.AWAITING_SEARCH_AREA, MissionState.ABORTED},
    MissionState.AWAITING_SEARCH_AREA: {MissionState.TAKING_OFF, MissionState.ABORTED},
    MissionState.TAKING_OFF: {MissionState.AIRBORNE_READY, MissionState.ABORTED},
    MissionState.AIRBORNE_READY: {
        MissionState.SEARCHING, MissionState.RETURNING_HOME, MissionState.ABORTED,
    },
    MissionState.SEARCHING: {
        MissionState.AWAITING_DETECTION_DECISION, MissionState.AIRBORNE_READY,
        MissionState.RETURNING_HOME, MissionState.ABORTED,
    },
    MissionState.AWAITING_DETECTION_DECISION: {
        MissionState.SEARCHING, MissionState.RETURNING_HOME, MissionState.ABORTED,
    },
    MissionState.RETURNING_HOME: {MissionState.LANDED, MissionState.ABORTED},
    MissionState.LANDED: set(),
    MissionState.ABORTED: set(),
}


class MissionStateMachine:
    def __init__(self, logger=None):
        self._state = MissionState.DISCONNECTED
        self._logger = logger or get_logger("Mission")

    @property
    def state(self) -> MissionState:
        return self._state

    def require(self, *allowed_states: MissionState):
        """Assert the current state is one of `allowed_states`, raising a
        clear MissionStateError naming both the actual and expected states
        if not."""
        if self._state not in allowed_states:
            expected = ", ".join(s.value for s in allowed_states)
            raise MissionStateError(
                f"Operation not allowed in state {self._state.value} (expected one of: {expected})"
            )

    def transition_to(self, new_state: MissionState):
        if new_state == self._state:
            return
        allowed = _ALLOWED_TRANSITIONS.get(self._state, set())
        if new_state not in allowed:
            raise MissionStateError(f"Illegal transition {self._state.value} -> {new_state.value}")
        self._logger.info(f"{self._state.value} -> {new_state.value}")
        self._state = new_state

    def is_terminal(self) -> bool:
        return self._state in (MissionState.LANDED, MissionState.ABORTED)
