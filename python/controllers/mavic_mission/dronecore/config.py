"""Configuration loading for dronecore.

Plain JSON (standard library only) rather than YAML — the project keeps zero
mandatory third-party dependencies for its core flight/mission code, and YAML
would pull in PyYAML just to read a handful of nested settings.
"""

import json
import os
from dataclasses import dataclass, field
from typing import Optional

DEFAULT_CONFIG_FILENAME = "config.json"


@dataclass
class ConnectionConfig:
    mode: str = "simulation"            # "simulation" | "live"
    host: str = "127.0.0.1"
    port: int = 5050
    connect_timeout_s: float = 10.0
    detection_response_timeout_s: float = 15.0
    auto_start_simulator: bool = True    # only consulted when mode == "simulation"
    # How often STATUS telemetry is sent, in seconds of sim time. Lower =
    # smoother real-time position feedback on the Java side, at the cost of
    # more socket traffic. 0.2s (5Hz) is smooth for a map UI without flooding.
    status_interval_s: float = 0.2


@dataclass
class WorldConfig:
    """Defines the world-meters <-> Java-app-coordinate mapping (see
    geometry.CoordinateTransform). Must match the actual terrain extent in
    the .wbt file (desert.wbt's ElevationGrid is 960x960m centered on the
    origin, i.e. -480..480 on both axes)."""
    x_min: float = -480.0
    x_max: float = 480.0
    y_min: float = -480.0
    y_max: float = 480.0
    java_coord_max: float = 1000.0      # Java app coordinates run 0..java_coord_max


@dataclass
class MissionConfig:
    target_altitude_m: float = 35.0
    search_lane_overlap: float = 0.2
    detection_poll_interval_s: float = 0.5
    target_precision_m: float = 1.2
    # Cruise/climb tuning - see FlightController. More negative
    # max_pitch_disturbance / higher vertical_p_gain / higher
    # max_yaw_disturbance = faster mission, at some cost to smoothness.
    # FlightController also smooths these to avoid sudden jumps, so pushing
    # these further is safer than it was before that smoothing existed.
    max_pitch_disturbance: float = -6.0
    vertical_p_gain: float = 7.0
    max_yaw_disturbance: float = 0.7


@dataclass
class DetectionConfig:
    # "ground_truth" (zero deps, treats the FOV oracle's geometric sighting
    # as sufficient), "hog" (needs `pip install opencv-python`, no model
    # file), or "yolo" (needs ultralytics + a trained model at model_path).
    backend: str = "ground_truth"
    model_path: str = "models/human_detector.pt"
    confidence_threshold: float = 0.5
    sample_images_dir: str = "sample_humans"
    sample_batch_size: int = 3
    cooldown_s: float = 20.0
    max_range_m: float = 60.0


@dataclass
class LoggingConfig:
    level: str = "INFO"


@dataclass
class Config:
    connection: ConnectionConfig = field(default_factory=ConnectionConfig)
    mission: MissionConfig = field(default_factory=MissionConfig)
    detection: DetectionConfig = field(default_factory=DetectionConfig)
    world: WorldConfig = field(default_factory=WorldConfig)
    logging: LoggingConfig = field(default_factory=LoggingConfig)

    # Directory config paths are resolved relative to (set by load()).
    base_dir: str = "."

    def resolve_path(self, relative_path: str) -> str:
        if os.path.isabs(relative_path):
            return relative_path
        return os.path.normpath(os.path.join(self.base_dir, relative_path))


def _merge(defaults: dict, overrides: dict) -> dict:
    merged = dict(defaults)
    for key, value in overrides.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = _merge(merged[key], value)
        else:
            merged[key] = value
    return merged


def load(path: Optional[str] = None) -> Config:
    """Load configuration from a JSON file, falling back to defaults for any
    keys/sections that are missing. `path` defaults to config.json next to
    this package's controller folder."""
    config = Config()

    if path is None:
        controller_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        path = os.path.join(controller_dir, DEFAULT_CONFIG_FILENAME)

    config.base_dir = os.path.dirname(os.path.abspath(path))

    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as f:
            raw = json.load(f)
    else:
        raw = {}

    config.connection = ConnectionConfig(**_merge(config.connection.__dict__, raw.get("connection", {})))
    config.mission = MissionConfig(**_merge(config.mission.__dict__, raw.get("mission", {})))
    config.detection = DetectionConfig(**_merge(config.detection.__dict__, raw.get("detection", {})))
    config.world = WorldConfig(**_merge(config.world.__dict__, raw.get("world", {})))
    config.logging = LoggingConfig(**_merge(config.logging.__dict__, raw.get("logging", {})))
    return config
