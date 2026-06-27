"""Low-level flight stabilization and motor control.

The PID gains and the four-motor mixing equations are ported directly from
the working `controllers/mavic_patrol/mavic_patrol.py` reference controller
(in turn adapted from Webots' own `mavic2pro_patrol` sample) — this is the
proven control-theory part of that script, decomposed into a reusable class
instead of being inlined in a single run() loop.

Everything above this module talks to the drone only through
`set_target_altitude()`, `set_target_position()` / `clear_target_position()`,
`update()`, `reached_target_*()`, and `land()`/`cut_motors()` — no caller
needs to know a PID or a motor exists.
"""

import math
from typing import Optional

from .geometry import Vec2, normalize_angle


def clamp(value, low, high):
    return min(max(value, low), high)


class FlightController:
    # Constants, empirically found (see mavic_patrol.py / Webots mavic2pro_patrol sample).
    # K_VERTICAL_THRUST/OFFSET/ROLL_P/PITCH_P are the aircraft's own stabilization
    # gains and shouldn't need tuning. Cruise speed/climb rate are governed by
    # max_pitch_disturbance/vertical_p_gain/max_yaw_disturbance below, which ARE
    # meant to be tuned (via config.json -> mission.*, see Drone) without touching
    # this file.
    K_VERTICAL_THRUST = 68.5
    K_VERTICAL_OFFSET = 0.6
    K_ROLL_P = 50.0
    K_PITCH_P = 30.0

    CAMERA_TILT_RAD = 0.7  # fixed downward gimbal tilt for ground observation

    TOUCHDOWN_ALTITUDE_M = 0.3

    # Exponential smoothing factor applied to pitch/yaw disturbance each tick
    # (see update()). Without this, snapping onto a new lawnmower lane can
    # flip the target bearing ~180 degrees in a single tick, slamming the
    # disturbance from one extreme to the other and destabilizing the
    # aircraft. Lower = smoother/safer but slower to respond; 1.0 = no
    # smoothing at all (the old behavior).
    DISTURBANCE_SMOOTHING = 0.18

    def __init__(self, robot, sensors, target_precision_m: float = 1.2,
                 max_pitch_disturbance: float = -6.0, vertical_p_gain: float = 7.0,
                 max_yaw_disturbance: float = 0.7):
        self._sensors = sensors
        self.target_precision_m = target_precision_m
        # More negative = more aggressive forward tilt = faster cruise speed.
        self.max_pitch_disturbance = max_pitch_disturbance
        # Higher = faster climb/descent authority.
        self.vertical_p_gain = vertical_p_gain
        # Higher = faster turn-to-heading between legs.
        self.max_yaw_disturbance = max_yaw_disturbance

        self._smoothed_pitch_disturbance = 0.0
        self._smoothed_yaw_disturbance = 0.0

        self._front_left_motor = robot.getDevice("front left propeller")
        self._front_right_motor = robot.getDevice("front right propeller")
        self._rear_left_motor = robot.getDevice("rear left propeller")
        self._rear_right_motor = robot.getDevice("rear right propeller")
        self._motors = (self._front_left_motor, self._front_right_motor,
                         self._rear_left_motor, self._rear_right_motor)
        for motor in self._motors:
            motor.setPosition(float("inf"))
            motor.setVelocity(1.0)

        camera_pitch_motor = robot.getDevice("camera pitch")
        camera_pitch_motor.setPosition(self.CAMERA_TILT_RAD)

        self.target_altitude: float = 0.0
        self._target_xy: Optional[Vec2] = None

    # ---- target setters (the only "intent" a caller expresses) ----

    def set_target_altitude(self, altitude_m: float):
        self.target_altitude = altitude_m

    def set_target_position(self, x: float, y: float):
        self._target_xy = Vec2(x, y)

    def clear_target_position(self):
        """No active horizontal target: the drone self-levels in place
        (passive hover) rather than seeking a point."""
        self._target_xy = None

    # ---- state queries ----

    def reached_target_altitude(self, tolerance_m: float = 1.0) -> bool:
        return abs(self._sensors.position().z - self.target_altitude) <= tolerance_m

    def reached_target_position(self) -> bool:
        if self._target_xy is None:
            return True
        pos = self._sensors.position()
        return self._target_xy.distance_to(Vec2(pos.x, pos.y)) < self.target_precision_m

    def is_touched_down(self) -> bool:
        return self._sensors.position().z <= self.TOUCHDOWN_ALTITUDE_M

    # ---- convenience high-level intents built on the above ----

    def takeoff_to(self, altitude_m: float):
        self.clear_target_position()
        self.set_target_altitude(altitude_m)

    def hover_in_place(self):
        self.clear_target_position()

    def begin_landing(self):
        self.clear_target_position()
        self.set_target_altitude(0.0)

    def cut_motors(self):
        for motor in self._motors:
            motor.setVelocity(0.0)

    # ---- the per-tick control loop ----

    def update(self):
        """Call exactly once per Webots step(). Reads sensors, computes the
        roll/pitch/yaw/vertical disturbances toward the current target
        (if any), and writes the four propeller velocities."""
        roll, pitch, yaw = self._sensors.attitude()
        altitude = self._sensors.position().z
        roll_rate, pitch_rate, _yaw_rate = self._sensors.angular_velocity()

        raw_yaw_disturbance, raw_pitch_disturbance = self._seek_disturbances(yaw)

        # Smooth out sudden jumps (e.g. snapping onto a new lawnmower lane
        # with a near-opposite bearing) instead of applying them instantly.
        s = self.DISTURBANCE_SMOOTHING
        self._smoothed_pitch_disturbance += (raw_pitch_disturbance - self._smoothed_pitch_disturbance) * s
        self._smoothed_yaw_disturbance += (raw_yaw_disturbance - self._smoothed_yaw_disturbance) * s
        pitch_disturbance = self._smoothed_pitch_disturbance
        yaw_disturbance = self._smoothed_yaw_disturbance

        roll_input = self.K_ROLL_P * clamp(roll, -1.0, 1.0) + roll_rate
        pitch_input = self.K_PITCH_P * clamp(pitch, -1.0, 1.0) + pitch_rate + pitch_disturbance
        yaw_input = yaw_disturbance
        clamped_difference_altitude = clamp(self.target_altitude - altitude + self.K_VERTICAL_OFFSET, -1.0, 1.0)
        vertical_input = self.vertical_p_gain * (clamped_difference_altitude ** 3.0)

        front_left = self.K_VERTICAL_THRUST + vertical_input - yaw_input + pitch_input - roll_input
        front_right = self.K_VERTICAL_THRUST + vertical_input + yaw_input + pitch_input + roll_input
        rear_left = self.K_VERTICAL_THRUST + vertical_input + yaw_input - pitch_input - roll_input
        rear_right = self.K_VERTICAL_THRUST + vertical_input - yaw_input - pitch_input + roll_input

        self._front_left_motor.setVelocity(front_left)
        self._front_right_motor.setVelocity(-front_right)
        self._rear_left_motor.setVelocity(-rear_left)
        self._rear_right_motor.setVelocity(rear_right)

    def _seek_disturbances(self, current_yaw: float):
        """Yaw/pitch disturbances that turn the drone toward, and fly it
        forward along, the bearing to the current horizontal target.
        Ported from mavic_patrol.py's move_to_target()."""
        if self._target_xy is None:
            return 0.0, 0.0

        pos = self._sensors.position()
        bearing = Vec2(pos.x, pos.y).bearing_to(self._target_xy)
        angle_left = normalize_angle(bearing - current_yaw)

        yaw_disturbance = self.max_yaw_disturbance * angle_left / (2 * math.pi)
        magnitude = math.log10(abs(angle_left)) if angle_left != 0 else self.max_pitch_disturbance
        pitch_disturbance = clamp(magnitude, self.max_pitch_disturbance, 0.1)
        return yaw_disturbance, pitch_disturbance
