"""Search-area coverage strategies.

`SearchPattern` is the extension point: adding a new coverage shape (spiral,
expanding square, ...) means writing one new subclass and pointing the
`Drone`/mission code at it — nothing else in the package needs to change.
"""

import math
from abc import ABC, abstractmethod
from typing import List

from .geometry import Area, Vec2, horizontal_footprint_width


class SearchPattern(ABC):
    @abstractmethod
    def generate_waypoints(self, area: Area, altitude_m: float,
                            horizontal_fov_rad: float, overlap_ratio: float) -> List[Vec2]:
        """Return an ordered list of waypoints that, when flown in sequence
        at the given altitude, cover `area` using a camera with the given
        horizontal field of view."""
        raise NotImplementedError


class BoustrophedonSearchPattern(SearchPattern):
    """Classic "lawnmower" coverage: parallel lanes spaced by the camera's
    ground footprint at the search altitude, alternating direction each lane
    so the drone never has to backtrack across uncovered ground."""

    MIN_LANE_WIDTH_M = 1.0

    def generate_waypoints(self, area: Area, altitude_m: float,
                            horizontal_fov_rad: float, overlap_ratio: float) -> List[Vec2]:
        footprint = horizontal_footprint_width(altitude_m, horizontal_fov_rad)
        lane_width = max(footprint * (1.0 - overlap_ratio), self.MIN_LANE_WIDTH_M)

        num_lanes = max(1, math.ceil(area.height / lane_width))
        lane_spacing = area.height / num_lanes

        waypoints: List[Vec2] = []
        y = area.y_min + lane_spacing / 2.0
        heading_right = True
        for _ in range(num_lanes):
            if heading_right:
                waypoints.append(Vec2(area.x_min, y))
                waypoints.append(Vec2(area.x_max, y))
            else:
                waypoints.append(Vec2(area.x_max, y))
                waypoints.append(Vec2(area.x_min, y))
            y += lane_spacing
            heading_right = not heading_right
        return waypoints
