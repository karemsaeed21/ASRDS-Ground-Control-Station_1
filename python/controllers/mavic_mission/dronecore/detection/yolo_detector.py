"""YOLO inference wrapper.

`ultralytics`/`torch` are NOT required just to import dronecore — they're
imported lazily on first use so the rest of the package (comms, search,
mission state machine, simulator) works fine even before those (potentially
heavy) ML dependencies are installed in Webots' Python environment. The
error you get if they're missing tells you exactly what to install.
"""

import os
from typing import List

from .base import DetectionOutcome


class YoloHumanDetector:
    PERSON_LABEL = "person"
    requires_images = True

    def __init__(self, model_path: str, confidence_threshold: float = 0.5):
        self.model_path = model_path
        self.confidence_threshold = confidence_threshold
        self._model = None

    def _ensure_model_loaded(self):
        if self._model is not None:
            return
        try:
            from ultralytics import YOLO
        except ImportError as exc:
            raise RuntimeError(
                "YOLO inference requires the 'ultralytics' package in Webots' Python "
                "environment. Install it with: pip install ultralytics. "
                f"(original import error: {exc})"
            ) from exc
        if not os.path.exists(self.model_path):
            raise FileNotFoundError(
                f"YOLO model not found at '{self.model_path}' "
                "(see config.json -> detection.model_path)"
            )
        self._model = YOLO(self.model_path)

    def detect(self, image_path: str) -> DetectionOutcome:
        """Run inference on a single image, returning whether a person was
        confirmed at or above the configured confidence threshold."""
        self._ensure_model_loaded()
        results = self._model(image_path, verbose=False)

        best_confidence = 0.0
        confirmed = False
        for result in results:
            boxes = getattr(result, "boxes", None)
            if boxes is None:
                continue
            names = getattr(result, "names", {})
            for box in boxes:
                label = names.get(int(box.cls[0]), str(int(box.cls[0])))
                confidence = float(box.conf[0])
                if label == self.PERSON_LABEL and confidence >= self.confidence_threshold:
                    confirmed = True
                    best_confidence = max(best_confidence, confidence)

        return DetectionOutcome(confirmed=confirmed, confidence=best_confidence, image_path=image_path)

    def detect_any(self, image_paths: List[str]) -> DetectionOutcome:
        """Run inference over a batch of sample images, short-circuiting on
        the first confirmed person. Returns the last (unconfirmed) outcome
        if none of the batch confirms a detection."""
        outcome = DetectionOutcome(confirmed=False)
        for path in image_paths:
            outcome = self.detect(path)
            if outcome.confirmed:
                return outcome
        return outcome
