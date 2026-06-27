"""Glues the ground-truth FOV trigger to a confirmation step.

This is the one place that knows both `FieldOfViewOracle` and the chosen
`HumanDetector` backend (YoloHumanDetector, HogHumanDetector,
GroundTruthHumanDetector, or any future one - see detection/base.py) exist;
everything above `DetectionPipeline.poll()` just gets an
`Optional[DetectionEvent]` back each tick. Swapping the detector backend, or
the oracle, means changing what gets passed into this constructor, not this
class.
"""

import os
import random
import time
from dataclasses import dataclass
from typing import List, Optional

from ..geometry import Vec3
from ..logging_utils import get_logger

SAMPLE_IMAGE_EXTENSIONS = (".jpg", ".jpeg", ".png", ".bmp")


@dataclass(frozen=True)
class DetectionEvent:
    drone_position: Vec3
    human_position: Vec3
    confidence: float
    sample_image_used: str
    timestamp: float


class DetectionPipeline:
    def __init__(self, fov_oracle, detector, sensors, sample_images_dir: str,
                 sample_batch_size: int = 3, cooldown_s: float = 20.0,
                 rng: Optional[random.Random] = None):
        self._fov_oracle = fov_oracle
        self._detector = detector
        self._sensors = sensors
        self._sample_batch_size = sample_batch_size
        self._cooldown_s = cooldown_s
        self._rng = rng or random.Random()
        self._cooldown_until = 0.0
        self._logger = get_logger("DetectionPipeline")
        self._requires_images = getattr(detector, "requires_images", True)
        self._sample_image_paths = self._discover_sample_images(sample_images_dir)

        if self._requires_images and not self._sample_image_paths:
            self._logger.warning(
                f"No sample images found in '{sample_images_dir}'. Detections will "
                "never confirm until you add photos there (see sample_humans/README.md)."
            )

    @staticmethod
    def _discover_sample_images(directory: str) -> List[str]:
        if not os.path.isdir(directory):
            return []
        return sorted(
            os.path.join(directory, name)
            for name in os.listdir(directory)
            if name.lower().endswith(SAMPLE_IMAGE_EXTENSIONS)
        )

    def poll(self, now: Optional[float] = None) -> Optional[DetectionEvent]:
        """Call periodically (the Drone facade throttles this to
        mission.detection_poll_interval_s). Returns a DetectionEvent only
        when a human is both geometrically in view AND confirmed by the
        configured detector backend (see detection/base.py)."""
        now = now if now is not None else time.time()
        if now < self._cooldown_until:
            return None

        sightings = self._fov_oracle.humans_in_view()
        if not sightings:
            return None
        if self._requires_images and not self._sample_image_paths:
            return None

        batch = self._pick_batch()
        outcome = self._detector.detect_any(batch)
        if not outcome.confirmed:
            return None

        self._cooldown_until = now + self._cooldown_s
        closest = min(sightings, key=lambda s: s.distance_m)
        return DetectionEvent(
            drone_position=self._sensors.position(),
            human_position=closest.position,
            confidence=outcome.confidence,
            sample_image_used=os.path.basename(outcome.image_path) if outcome.image_path else "",
            timestamp=now,
        )

    def _pick_batch(self) -> List[str]:
        if len(self._sample_image_paths) <= self._sample_batch_size:
            return list(self._sample_image_paths)
        return self._rng.sample(self._sample_image_paths, self._sample_batch_size)
