"""Classic computer-vision human detector: OpenCV's built-in HOG + SVM
pedestrian detector. No model file to train or download — the SVM weights
ship inside OpenCV itself (`cv2.HOGDescriptor_getDefaultPeopleDetector()`).

Drop-in alternative to YoloHumanDetector when you don't have a trained YOLO
model available: only needs `pip install opencv-python` in Webots' Python
environment. Detection quality is noticeably weaker than a trained YOLO
model (HOG/SVM is a ~15-year-old technique), but it's a real image-based
detector, not a stub.
"""

from typing import List

from .base import DetectionOutcome


class HogHumanDetector:
    requires_images = True

    def __init__(self, confidence_threshold: float = 0.5):
        # HOG's detectMultiScale returns SVM decision weights, not
        # probabilities; this threshold is on that raw weight, not 0..1.
        self.confidence_threshold = confidence_threshold
        self._hog = None

    def _ensure_loaded(self):
        if self._hog is not None:
            return
        try:
            import cv2
        except ImportError as exc:
            raise RuntimeError(
                "The 'hog' detection backend requires the 'opencv-python' package "
                "in Webots' Python environment. Install it with: pip install opencv-python. "
                f"(original import error: {exc})"
            ) from exc
        self._cv2 = cv2
        self._hog = cv2.HOGDescriptor()
        self._hog.setSVMDetector(cv2.HOGDescriptor_getDefaultPeopleDetector())

    def detect(self, image_path: str) -> DetectionOutcome:
        self._ensure_loaded()
        image = self._cv2.imread(image_path)
        if image is None:
            return DetectionOutcome(confirmed=False, image_path=image_path)

        boxes, weights = self._hog.detectMultiScale(image, winStride=(8, 8))
        best_weight = float(max(weights)) if len(weights) else 0.0
        confirmed = best_weight >= self.confidence_threshold
        return DetectionOutcome(confirmed=confirmed, confidence=best_weight, image_path=image_path)

    def detect_any(self, image_paths: List[str]) -> DetectionOutcome:
        outcome = DetectionOutcome(confirmed=False)
        for path in image_paths:
            outcome = self.detect(path)
            if outcome.confirmed:
                return outcome
        return outcome
