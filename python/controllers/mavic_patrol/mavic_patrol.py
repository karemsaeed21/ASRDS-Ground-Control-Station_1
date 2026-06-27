"""Autonomous patrol controller for the Mavic 2 PRO over the desert UAV test environment.

Takes off from the helipad, climbs to a cruise altitude that clears the tallest
mesa, then loops through GPS waypoints covering the oasis, the rocky outcrops,
the dry valley and both plateaus before returning over the pad.

Stabilization (roll/pitch/yaw/vertical PID) and the waypoint-seeking logic are
adapted from the official Webots "mavic2pro_patrol" sample controller, ported
to use only the standard library (math) instead of numpy for fewer dependencies.
"""

import math

from controller import Robot


def clamp(value, value_min, value_max):
    return min(max(value, value_min), value_max)


class Mavic(Robot):
    K_VERTICAL_THRUST = 68.5
    K_VERTICAL_OFFSET = 0.6
    K_VERTICAL_P = 3.0
    K_ROLL_P = 50.0
    K_PITCH_P = 30.0

    MAX_YAW_DISTURBANCE = 0.4
    MAX_PITCH_DISTURBANCE = -1
    TARGET_PRECISION = 0.5

    def __init__(self):
        Robot.__init__(self)

        self.time_step = int(self.getBasicTimeStep())

        self.camera = self.getDevice("camera")
        self.camera.enable(self.time_step)
        self.imu = self.getDevice("inertial unit")
        self.imu.enable(self.time_step)
        self.gps = self.getDevice("gps")
        self.gps.enable(self.time_step)
        self.gyro = self.getDevice("gyro")
        self.gyro.enable(self.time_step)

        self.front_left_motor = self.getDevice("front left propeller")
        self.front_right_motor = self.getDevice("front right propeller")
        self.rear_left_motor = self.getDevice("rear left propeller")
        self.rear_right_motor = self.getDevice("rear right propeller")
        self.camera_pitch_motor = self.getDevice("camera pitch")
        self.camera_pitch_motor.setPosition(0.7)

        for motor in (self.front_left_motor, self.front_right_motor,
                      self.rear_left_motor, self.rear_right_motor):
            motor.setPosition(float("inf"))
            motor.setVelocity(1)

        self.current_pose = 6 * [0]
        self.target_position = [0, 0, 0]
        self.target_index = 0
        self.target_altitude = 0

    def set_position(self, pos):
        self.current_pose = pos

    def move_to_target(self, waypoints):
        if self.target_position[0:2] == [0, 0]:
            self.target_position[0:2] = waypoints[0]

        if all(abs(x1 - x2) < self.TARGET_PRECISION
               for x1, x2 in zip(self.target_position, self.current_pose[0:2])):
            self.target_index = (self.target_index + 1) % len(waypoints)
            self.target_position[0:2] = waypoints[self.target_index]

        self.target_position[2] = math.atan2(
            self.target_position[1] - self.current_pose[1],
            self.target_position[0] - self.current_pose[0])

        angle_left = self.target_position[2] - self.current_pose[5]
        angle_left = (angle_left + 2 * math.pi) % (2 * math.pi)
        if angle_left > math.pi:
            angle_left -= 2 * math.pi

        yaw_disturbance = self.MAX_YAW_DISTURBANCE * angle_left / (2 * math.pi)
        pitch_disturbance = clamp(
            math.log10(abs(angle_left)) if angle_left != 0 else self.MAX_PITCH_DISTURBANCE,
            self.MAX_PITCH_DISTURBANCE, 0.1)

        return yaw_disturbance, pitch_disturbance

    def run(self):
        t1 = self.getTime()

        roll_disturbance = 0
        pitch_disturbance = 0
        yaw_disturbance = 0

        # Tour of the desert landmarks: pad -> oasis -> rocky outcrop ->
        # dry valley -> the two mesas -> back over the pad.
        waypoints = [
            [-150, -200],
            [-220, -260],
            [-100, -150],
            [90, -150],
            [150, -320],
            [260, 180],
            [-300, 60],
            [0, 0],
        ]
        self.target_altitude = 30

        while self.step(self.time_step) != -1:
            roll, pitch, yaw = self.imu.getRollPitchYaw()
            x_pos, y_pos, altitude = self.gps.getValues()
            roll_acceleration, pitch_acceleration, _ = self.gyro.getValues()
            self.set_position([x_pos, y_pos, altitude, roll, pitch, yaw])

            if altitude > self.target_altitude - 1:
                if self.getTime() - t1 > 0.1:
                    yaw_disturbance, pitch_disturbance = self.move_to_target(waypoints)
                    t1 = self.getTime()

            roll_input = self.K_ROLL_P * clamp(roll, -1, 1) + roll_acceleration + roll_disturbance
            pitch_input = self.K_PITCH_P * clamp(pitch, -1, 1) + pitch_acceleration + pitch_disturbance
            yaw_input = yaw_disturbance
            clamped_difference_altitude = clamp(self.target_altitude - altitude + self.K_VERTICAL_OFFSET, -1, 1)
            vertical_input = self.K_VERTICAL_P * pow(clamped_difference_altitude, 3.0)

            front_left_motor_input = self.K_VERTICAL_THRUST + vertical_input - yaw_input + pitch_input - roll_input
            front_right_motor_input = self.K_VERTICAL_THRUST + vertical_input + yaw_input + pitch_input + roll_input
            rear_left_motor_input = self.K_VERTICAL_THRUST + vertical_input + yaw_input - pitch_input - roll_input
            rear_right_motor_input = self.K_VERTICAL_THRUST + vertical_input - yaw_input - pitch_input + roll_input

            self.front_left_motor.setVelocity(front_left_motor_input)
            self.front_right_motor.setVelocity(-front_right_motor_input)
            self.rear_left_motor.setVelocity(-rear_left_motor_input)
            self.rear_right_motor.setVelocity(rear_right_motor_input)


robot = Mavic()
robot.run()
