"""Geometry primitives shared across the dronecore package.

Kept dependency-free (standard library only) so importing dronecore never
requires numpy/etc. just to reason about positions and areas.
"""

import math
from dataclasses import dataclass


@dataclass(frozen=True)
class Vec2:
    x: float
    y: float

    def distance_to(self, other: "Vec2") -> float:
        return math.hypot(other.x - self.x, other.y - self.y)

    def bearing_to(self, other: "Vec2") -> float:
        """Angle in radians, in ]-pi, pi], from this point to other."""
        return math.atan2(other.y - self.y, other.x - self.x)


@dataclass(frozen=True)
class Vec3:
    x: float
    y: float
    z: float

    def to_dict(self) -> dict:
        return {"x": self.x, "y": self.y, "z": self.z}

    @staticmethod
    def from_dict(data: dict) -> "Vec3":
        return Vec3(float(data["x"]), float(data["y"]), float(data["z"]))


@dataclass(frozen=True)
class Area:
    """A rectangular search region in world XY coordinates (meters)."""

    x_min: float
    y_min: float
    x_max: float
    y_max: float

    def __post_init__(self):
        if self.x_min > self.x_max or self.y_min > self.y_max:
            raise ValueError(
                f"Invalid area: min must not exceed max (got x:[{self.x_min},{self.x_max}] "
                f"y:[{self.y_min},{self.y_max}])"
            )

    @property
    def width(self) -> float:
        return self.x_max - self.x_min

    @property
    def height(self) -> float:
        return self.y_max - self.y_min

    @property
    def center(self) -> Vec2:
        return Vec2((self.x_min + self.x_max) / 2.0, (self.y_min + self.y_max) / 2.0)

    def contains(self, point: Vec2) -> bool:
        return self.x_min <= point.x <= self.x_max and self.y_min <= point.y <= self.y_max

    def to_dict(self) -> dict:
        return {"x_min": self.x_min, "y_min": self.y_min, "x_max": self.x_max, "y_max": self.y_max}

    @staticmethod
    def from_dict(data: dict) -> "Area":
        return Area(float(data["x_min"]), float(data["y_min"]),
                     float(data["x_max"]), float(data["y_max"]))


@dataclass(frozen=True)
class CoordinateTransform:
    """Converts between the Java app's normalized map coordinates
    (0..java_max on each axis, independent of world size) and this world's
    actual XY meters. This is the ONLY place that conversion happens —
    everything else in dronecore (Area, Vec2/Vec3, FlightController,
    SearchPattern) works exclusively in world meters.

    (0, 0) in Java-space maps to (world_x_min, world_y_min);
    (java_max, java_max) maps to (world_x_max, world_y_max). Both axes are
    independent, so a non-square world is fine too.
    """

    world_x_min: float
    world_x_max: float
    world_y_min: float
    world_y_max: float
    java_max: float = 1000.0

    def to_world(self, java_x: float, java_y: float) -> Vec2:
        wx = self.world_x_min + (java_x / self.java_max) * (self.world_x_max - self.world_x_min)
        wy = self.world_y_min + (java_y / self.java_max) * (self.world_y_max - self.world_y_min)
        return Vec2(wx, wy)

    def to_java(self, world_x: float, world_y: float) -> Vec2:
        jx = (world_x - self.world_x_min) / (self.world_x_max - self.world_x_min) * self.java_max
        jy = (world_y - self.world_y_min) / (self.world_y_max - self.world_y_min) * self.java_max
        return Vec2(jx, jy)


def normalize_angle(angle_rad: float) -> float:
    """Wrap an angle in radians to ]-pi, pi]."""
    wrapped = (angle_rad + math.pi) % (2 * math.pi) - math.pi
    if wrapped == -math.pi:
        wrapped = math.pi
    return wrapped


def horizontal_footprint_width(altitude_m: float, horizontal_fov_rad: float) -> float:
    """Width (meters) of the ground footprint a downward/forward camera covers
    at a given altitude, used to size search-pattern lane spacing."""
    return 2.0 * altitude_m * math.tan(horizontal_fov_rad / 2.0)


def is_within_frustum(forward_angle_to_target: float, half_fov_rad: float) -> bool:
    """True if a target's angle off the camera boresight is within the half-FOV."""
    return abs(normalize_angle(forward_angle_to_target)) <= half_fov_rad
