"""Shared contract for human-detection backends.

Any object with a `detect_any(image_paths) -> DetectionOutcome` method and a
`requires_images` flag can be plugged into `DetectionPipeline` — see
yolo_detector.py, hog_detector.py, and ground_truth_detector.py for the three
backends that ship with dronecore.
"""

from dataclasses import dataclass
from typing import List, Optional, Protocol


@dataclass
class DetectionOutcome:
    confirmed: bool
    confidence: float = 0.0
    image_path: Optional[str] = None


class HumanDetector(Protocol):
    # Whether this backend needs real sample photos to run on. False for
    # backends (like the ground-truth stub) that don't look at images at all.
    requires_images: bool

    def detect_any(self, image_paths: List[str]) -> DetectionOutcome:
        ...
