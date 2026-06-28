"""The wire protocol: the single source of truth for message shapes, shared
by the drone-side TCP client (mission_link.py) and the bundled simulator
(simulator.py) so the two can never silently drift apart. This is also the
basis for PROTOCOL.md, which documents the same thing for the Java side.

Framing: one UTF-8 JSON object per line ("\\n"-terminated). Encoding/decoding
of individual messages lives here; mission_link.py owns the socket and the
line-splitting.
"""

import json
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Dict, Optional

PROTOCOL_VERSION = "1.0"


class MessageType(str, Enum):
    # Drone -> App
    HELLO = "HELLO"
    SEARCH_AREA_REQUEST = "SEARCH_AREA_REQUEST"
    STATUS = "STATUS"
    HUMAN_DETECTED = "HUMAN_DETECTED"
    SEARCH_COMPLETE = "SEARCH_COMPLETE"
    # App -> Drone
    SEARCH_AREA = "SEARCH_AREA"
    START_MISSION = "START_MISSION"
    DETECTION_RESPONSE = "DETECTION_RESPONSE"
    ABORT = "ABORT"


class DetectionDecision(str, Enum):
    CONTINUE_SEARCH = "CONTINUE_SEARCH"
    RETURN_HOME = "RETURN_HOME"


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass
class Message:
    type: MessageType
    payload: Dict[str, Any] = field(default_factory=dict)
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    timestamp: str = field(default_factory=_now_iso)
    in_reply_to: Optional[str] = None
    protocol_version: str = PROTOCOL_VERSION

    def type_value(self) -> str:
        return self.type.value if isinstance(self.type, MessageType) else str(self.type)

    def to_json_line(self) -> str:
        envelope = {
            "protocol_version": self.protocol_version,
            "type": self.type_value(),
            "id": self.id,
            "timestamp": self.timestamp,
            "payload": self.payload,
        }
        if self.in_reply_to is not None:
            envelope["in_reply_to"] = self.in_reply_to
        return json.dumps(envelope, separators=(",", ":"))

    @staticmethod
    def from_json_line(line: str) -> "Message":
        data = json.loads(line)
        raw_type = data["type"]
        try:
            msg_type = MessageType(raw_type)
        except ValueError:
            msg_type = raw_type  # forward-compatible: unknown future type kept verbatim
        return Message(
            type=msg_type,
            payload=data.get("payload", {}) or {},
            id=data.get("id", str(uuid.uuid4())),
            timestamp=data.get("timestamp", _now_iso()),
            in_reply_to=data.get("in_reply_to"),
            protocol_version=data.get("protocol_version", PROTOCOL_VERSION),
        )


# ---- Convenience constructors (keep call sites readable & typo-proof) ----

def hello(drone_id: str, world_name: str) -> Message:
    return Message(MessageType.HELLO, {"drone_id": drone_id, "world_name": world_name})


def search_area_request() -> Message:
    return Message(MessageType.SEARCH_AREA_REQUEST, {})


def status(state: str, position: dict, heading_rad: float) -> Message:
    return Message(MessageType.STATUS, {"state": state, "position": position, "heading_rad": heading_rad})


def human_detected(drone_position: dict, estimated_human_position: dict,
                    confidence: float, sample_image_used: str) -> Message:
    return Message(MessageType.HUMAN_DETECTED, {
        "drone_position": drone_position,
        "estimated_human_position": estimated_human_position,
        "confidence": confidence,
        "sample_image_used": sample_image_used,
    })


def search_complete(area: dict) -> Message:
    return Message(MessageType.SEARCH_COMPLETE, {"area": area})


def search_area(area: dict, altitude_m: Optional[float] = None) -> Message:
    payload = dict(area)
    if altitude_m is not None:
        payload["altitude_m"] = altitude_m
    return Message(MessageType.SEARCH_AREA, payload)


def detection_response(decision: DetectionDecision, in_reply_to: str) -> Message:
    value = decision.value if isinstance(decision, DetectionDecision) else decision
    return Message(MessageType.DETECTION_RESPONSE, {"decision": value}, in_reply_to=in_reply_to)


def abort() -> Message:
    return Message(MessageType.ABORT, {})
