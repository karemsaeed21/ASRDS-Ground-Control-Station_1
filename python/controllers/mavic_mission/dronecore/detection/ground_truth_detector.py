"""Zero-dependency detection backend: treats the FieldOfViewOracle's
geometric "a human is visible" check as sufficient confirmation on its own,
with no image-based analysis at all.

This is the right default when neither a trained YOLO model nor OpenCV is
available yet — the mission pipeline (search, pause-on-detection, the
socket protocol, the simulator) all work identically regardless of which
detector is plugged in, so you can develop/test the whole system now and
swap in YoloHumanDetector or HogHumanDetector later with a one-line config
change, no other code changes needed.
"""

from typing import List

from .base import DetectionOutcome


class GroundTruthHumanDetector:
    requires_images = False

    def detect_any(self, image_paths: List[str]) -> DetectionOutcome:
        return DetectionOutcome(confirmed=True, confidence=1.0, image_path=None)
