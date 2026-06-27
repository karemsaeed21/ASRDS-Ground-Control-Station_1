"""Pluggable human-detection backends. See base.py for the shared contract
that DetectionPipeline depends on, and build_detector() below to construct
whichever one is named in config.json -> detection.backend."""

from .base import DetectionOutcome, HumanDetector
from .pipeline import DetectionPipeline, DetectionEvent
from .fov_oracle import FieldOfViewOracle

_BACKENDS = ("ground_truth", "hog", "yolo")


def build_detector(config, resolved_model_path: str):
    """Builds the HumanDetector named by config.detection.backend.
    `resolved_model_path` should already be resolved against the
    controller's base_dir (only used by the "yolo" backend)."""
    backend = config.detection.backend
    if backend == "ground_truth":
        from .ground_truth_detector import GroundTruthHumanDetector
        return GroundTruthHumanDetector()
    if backend == "hog":
        from .hog_detector import HogHumanDetector
        return HogHumanDetector(config.detection.confidence_threshold)
    if backend == "yolo":
        from .yolo_detector import YoloHumanDetector
        return YoloHumanDetector(resolved_model_path, config.detection.confidence_threshold)
    raise ValueError(
        f"Unknown detection.backend '{backend}' in config.json; expected one of {_BACKENDS}."
    )


__all__ = [
    "DetectionOutcome", "HumanDetector", "DetectionPipeline", "DetectionEvent",
    "FieldOfViewOracle", "build_detector",
]
