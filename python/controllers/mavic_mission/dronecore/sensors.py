"""Thin, encapsulated wrapper around the Mavic2Pro's Webots sensor devices.

Nothing above this module should call `robot.getDevice(...)` directly for a
sensor — this is the single seam between "Webots device API" and the rest of
dronecore, so a future hardware-in-the-loop swap or a different drone PROTO
only requires changes here.
"""

import math

from .geometry import Vec3


class SensorSuite:
    def __init__(self, robot, time_step_ms: int):
        self._gps = robot.getDevice("gps")
        self._imu = robot.getDevice("inertial unit")
        self._gyro = robot.getDevice("gyro")
        self._camera = robot.getDevice("camera")

        self._gps.enable(time_step_ms)
        self._imu.enable(time_step_ms)
        self._gyro.enable(time_step_ms)
        self._camera.enable(time_step_ms)

    @property
    def camera(self):
        """Exposed directly for code that needs raw camera frames (e.g. a
        future live-frame detector) or its FOV/resolution for geometry."""
        return self._camera

    def position(self) -> Vec3:
        x, y, z = self._gps.getValues()
        return Vec3(x, y, z)

    def attitude(self):
        """Returns (roll, pitch, yaw) in radians."""
        roll, pitch, yaw = self._imu.getRollPitchYaw()
        return roll, pitch, yaw

    def yaw(self) -> float:
        return self._imu.getRollPitchYaw()[2]

    def angular_velocity(self):
        """Returns (roll_velocity, pitch_velocity, yaw_velocity) in rad/s."""
        return self._gyro.getValues()

    def horizontal_fov(self) -> float:
        return self._camera.getFov()

    def vertical_fov(self) -> float:
        aspect = self._camera.getWidth() / float(self._camera.getHeight())
        return 2.0 * math.atan(math.tan(self._camera.getFov() / 2.0) / aspect)
