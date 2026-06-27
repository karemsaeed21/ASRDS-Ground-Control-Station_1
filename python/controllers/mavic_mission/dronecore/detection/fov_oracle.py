"""Ground-truth "is a human visible to the camera right now?" check.

Deliberately does NOT look at camera pixels. Webots' synthetic frames don't
reliably trigger a real-photo-trained YOLO model, so instead of analyzing
the live frame we use Supervisor access to ask the simulation directly
whether any Pedestrian node falls inside the camera's view frustum. That
boolean is the *trigger*; the actual "detection" is then confirmed by
running YOLO on real curated photos (see yolo_detector.py) — see
detection/pipeline.py for how the two are combined.

Requires the drone's `supervisor` field to be TRUE in the .wbt file.
"""

import math
from dataclasses import dataclass
from typing import List, Optional

from ..geometry import Vec3, normalize_angle


@dataclass(frozen=True)
class HumanSighting:
    node: object            # the Webots Pedestrian Node handle
    name: str
    position: Vec3
    distance_m: float
    bearing_rad: float      # absolute world-frame bearing from drone to human


class FieldOfViewOracle:
    PEDESTRIAN_TYPE_NAME = "Pedestrian"

    def __init__(self, supervisor, sensors, max_range_m: float, camera_tilt_rad: float = 0.0):
        """
        camera_tilt_rad: the camera gimbal's fixed downward tilt (see
        FlightController.CAMERA_TILT_RAD) — needed to know where the
        camera's vertical boresight actually points, since the propeller
        body's pitch alone isn't the camera's pitch.
        """
        self._supervisor = supervisor
        self._sensors = sensors
        self.max_range_m = max_range_m
        self.camera_tilt_rad = camera_tilt_rad
        self._pedestrian_nodes = self._discover_pedestrians()

    def _discover_pedestrians(self) -> List[object]:
        """One-time scan of the world tree for Pedestrian nodes. Generic by
        node type rather than by DEF name, so the .wbt doesn't need every
        pedestrian individually DEF-named."""
        children_field = self._supervisor.getRoot().getField("children")
        nodes = []
        for i in range(children_field.getCount()):
            node = children_field.getMFNode(i)
            if node.getTypeName() == self.PEDESTRIAN_TYPE_NAME:
                nodes.append(node)
        return nodes

    def humans_in_view(self) -> List[HumanSighting]:
        drone_pos = self._sensors.position()
        drone_yaw = self._sensors.yaw()
        half_h_fov = self._sensors.horizontal_fov() / 2.0
        half_v_fov = self._sensors.vertical_fov() / 2.0

        sightings: List[HumanSighting] = []
        for node in self._pedestrian_nodes:
            px, py, pz = node.getPosition()
            dx, dy, dz = px - drone_pos.x, py - drone_pos.y, pz - drone_pos.z
            distance = math.sqrt(dx * dx + dy * dy + dz * dz)
            if distance < 1e-6 or distance > self.max_range_m:
                continue

            bearing = math.atan2(dy, dx)
            horizontal_angle_off = normalize_angle(bearing - drone_yaw)
            if abs(horizontal_angle_off) > half_h_fov:
                continue

            horizontal_distance = math.hypot(dx, dy)
            target_elevation = math.atan2(dz, horizontal_distance) if horizontal_distance > 1e-6 else 0.0
            # Camera boresight elevation = -camera_tilt_rad (tilted down from forward-level).
            vertical_angle_off = normalize_angle(target_elevation + self.camera_tilt_rad)
            if abs(vertical_angle_off) > half_v_fov:
                continue

            sightings.append(HumanSighting(
                node=node, name=node.getField("name").getSFString() if node.getField("name") else "human",
                position=Vec3(px, py, pz), distance_m=distance, bearing_rad=bearing,
            ))
        return sightings

    @staticmethod
    def closest(sightings: List[HumanSighting]) -> Optional[HumanSighting]:
        if not sightings:
            return None
        return min(sightings, key=lambda s: s.distance_m)
