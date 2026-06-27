"""The public API. This is the only class a mission script should need:

    drone = Drone()
    drone.connect()
    area = drone.wait_for_search_area()
    drone.takeoff()
    drone.search(area)
    drone.return_home()

Every other module in dronecore exists to be wired together and hidden
behind this facade. Nothing here is a Webots device call, a socket, or a
PID gain — those all live one layer down.
"""

import os
import subprocess
import sys
import time
from enum import Enum
from typing import Optional

from . import config as config_module
from .geometry import Area, CoordinateTransform, Vec2, Vec3
from .logging_utils import get_logger, configure as configure_logging
from .sensors import SensorSuite
from .flight_controller import FlightController
from .search import BoustrophedonSearchPattern
from .mission import MissionStateMachine, MissionState
from .detection.fov_oracle import FieldOfViewOracle
from .detection import build_detector
from .detection.pipeline import DetectionPipeline
from .comms import protocol
from .comms.protocol import MessageType, DetectionDecision
from .comms.mission_link import TcpMissionLink


class SearchOutcome(Enum):
    COMPLETED = "COMPLETED"
    ABORTED_RETURN_HOME = "ABORTED_RETURN_HOME"


class Drone:
    def __init__(self, config_path: Optional[str] = None,
                 connection_mode: Optional[str] = None, robot=None):
        self._config = config_module.load(config_path)
        if connection_mode is not None:
            self._config.connection.mode = connection_mode
        configure_logging(self._config.logging.level)
        self._logger = get_logger("Drone")

        # Imported lazily (not at module scope) because this module is also
        # reachable, via the dronecore package's __init__, from code running
        # outside Webots (e.g. the bundled simulator subprocess) where the
        # `controller` module doesn't exist. Only constructing a Drone
        # actually needs it.
        if robot is None:
            from controller import Supervisor
            robot = Supervisor()

        # A Supervisor (not a plain Robot) is required: FieldOfViewOracle
        # needs Supervisor access to read other nodes' world positions.
        self._robot = robot
        self._timestep_ms = int(self._robot.getBasicTimeStep())

        self._sensors = SensorSuite(self._robot, self._timestep_ms)
        self._flight = FlightController(
            self._robot, self._sensors,
            target_precision_m=self._config.mission.target_precision_m,
            max_pitch_disturbance=self._config.mission.max_pitch_disturbance,
            vertical_p_gain=self._config.mission.vertical_p_gain,
            max_yaw_disturbance=self._config.mission.max_yaw_disturbance,
        )
        self._mission = MissionStateMachine()
        self._search_pattern = BoustrophedonSearchPattern()

        # The Java app speaks in 0..java_coord_max map coordinates; this
        # world's flight/search/detection code speaks exclusively in world
        # meters. This is the only object that knows both - everything sent
        # to / received from the link is converted right at the boundary
        # (see wait_for_search_area, _maybe_send_status,
        # _handle_detection_event, and the SEARCH_COMPLETE send in search()).
        self._coords = CoordinateTransform(
            self._config.world.x_min, self._config.world.x_max,
            self._config.world.y_min, self._config.world.y_max,
            self._config.world.java_coord_max,
        )

        self._fov_oracle = FieldOfViewOracle(
            self._robot, self._sensors,
            max_range_m=self._config.detection.max_range_m,
            camera_tilt_rad=FlightController.CAMERA_TILT_RAD,
        )
        self._detector = build_detector(
            self._config, self._config.resolve_path(self._config.detection.model_path)
        )
        self._detection_pipeline = DetectionPipeline(
            self._fov_oracle, self._detector, self._sensors,
            self._config.resolve_path(self._config.detection.sample_images_dir),
            sample_batch_size=self._config.detection.sample_batch_size,
            cooldown_s=self._config.detection.cooldown_s,
        )

        home_x, home_y, _ = self._robot.getSelf().getPosition()
        self._home_position = Vec2(home_x, home_y)
        self._drone_id = self._robot.getName()

        self._link: Optional[TcpMissionLink] = None
        self._simulator_process: Optional[subprocess.Popen] = None
        self._last_detection_poll_time = float("-inf")
        self._last_status_time = float("-inf")

    # ================= public mission API =================

    def connect(self, timeout: Optional[float] = None):
        """Establish the mission link (real Java app, or the bundled
        simulator per config/connection_mode) and send the initial HELLO."""
        self._mission.require(MissionState.DISCONNECTED)
        self._mission.transition_to(MissionState.CONNECTING)

        if self._config.connection.mode == "simulation" and self._config.connection.auto_start_simulator:
            self._spawn_bundled_simulator()

        self._link = self._connect_with_retry(timeout or self._config.connection.connect_timeout_s)
        self._link.send(protocol.hello(self._drone_id, "desert"))
        self._mission.transition_to(MissionState.CONNECTED)
        self._logger.info(f"Connected ({self._config.connection.mode} mode); HELLO sent.")

    def wait_for_search_area(self, timeout: Optional[float] = None) -> Area:
        """Blocks (while keeping the simulation stepping) until the app
        sends a SEARCH_AREA message, and returns it."""
        self._mission.require(MissionState.CONNECTED)
        self._mission.transition_to(MissionState.AWAITING_SEARCH_AREA)
        self._link.send(protocol.search_area_request())

        received = {"area": None}

        def on_tick():
            msg = self._link.receive(timeout=0)
            if msg and msg.type == MessageType.SEARCH_AREA:
                received["area"] = self._java_area_to_world(msg.payload)
                altitude = msg.payload.get("altitude_m")
                if altitude is not None:
                    self._config.mission.target_altitude_m = float(altitude)
            return False

        self._spin_until(lambda: received["area"] is not None, timeout=timeout, on_tick=on_tick)
        self._logger.info(f"Received search area: {received['area']}")
        return received["area"]

    def takeoff(self, altitude_m: Optional[float] = None):
        """Climbs to the target altitude (config default, or override) and
        holds there."""
        self._mission.require(MissionState.AWAITING_SEARCH_AREA)
        self._mission.transition_to(MissionState.TAKING_OFF)
        target = altitude_m if altitude_m is not None else self._config.mission.target_altitude_m
        self._flight.takeoff_to(target)
        self._spin_until(lambda: self._flight.reached_target_altitude(1.0), on_tick=self._flight.update)
        self._mission.transition_to(MissionState.AIRBORNE_READY)
        self._logger.info(f"Airborne, holding ~{target:.1f}m.")

    def search(self, area: Area) -> SearchOutcome:
        """Systematically covers `area` with a lawnmower pattern, pausing
        for app input on any confirmed human detection. Returns COMPLETED if
        the whole area was covered, or ABORTED_RETURN_HOME if a detection
        decision ended the mission early (in which case the drone has
        already landed by the time this returns)."""
        self._mission.require(MissionState.AIRBORNE_READY)
        self._mission.transition_to(MissionState.SEARCHING)

        waypoints = self._search_pattern.generate_waypoints(
            area, self._config.mission.target_altitude_m,
            self._sensors.horizontal_fov(), self._config.mission.search_lane_overlap,
        )
        self._logger.info(f"Searching {area} via a {len(waypoints)}-point lawnmower pattern.")

        for waypoint in waypoints:
            self._flight.set_target_position(waypoint.x, waypoint.y)
            if self._fly_current_leg_with_detection():
                return SearchOutcome.ABORTED_RETURN_HOME

        self._link.send(protocol.search_complete(self._world_area_to_java_dict(area)))
        self._mission.transition_to(MissionState.AIRBORNE_READY)
        self._logger.info("Search area fully covered.")
        return SearchOutcome.COMPLETED

    def return_home(self):
        """Flies back to the launch pad and lands. Idempotent: a no-op if
        the drone is already landed (e.g. because search() already
        triggered a return-home internally)."""
        if self._mission.state == MissionState.LANDED:
            self._logger.info("Already landed; return_home() is a no-op.")
            return
        self._mission.require(MissionState.AIRBORNE_READY, MissionState.SEARCHING,
                               MissionState.AWAITING_DETECTION_DECISION)
        self._execute_return_home()

    def shutdown(self):
        """Closes the mission link and stops the auto-spawned simulator, if
        any. Safe to call multiple times."""
        if self._link is not None:
            self._link.close()
        if self._simulator_process is not None:
            try:
                self._simulator_process.terminate()
            except OSError:
                pass
            self._simulator_process = None

    # ================= read-only properties =================

    @property
    def state(self) -> MissionState:
        return self._mission.state

    @property
    def position(self) -> Vec3:
        return self._sensors.position()

    # ================= internals =================

    def _spin_until(self, condition_fn, timeout: Optional[float] = None, on_tick=None) -> bool:
        """Steps the Webots simulation until `condition_fn()` is true.
        Every tick: advances the sim, pumps a periodic STATUS message, then
        calls `on_tick()` if provided. If `on_tick()` returns a truthy
        value, stops immediately and returns True (used to let a tick
        handler — e.g. a RETURN_HOME decision — interrupt the wait).
        Raises TimeoutError if `timeout` (sim-time seconds) elapses first.
        """
        start = self._robot.getTime()
        while True:
            if self._robot.step(self._timestep_ms) == -1:
                raise ConnectionError("Webots simulation ended while the controller was waiting.")
            self._maybe_send_status()
            if on_tick is not None and on_tick():
                return True
            if condition_fn():
                return False
            if timeout is not None and (self._robot.getTime() - start) > timeout:
                raise TimeoutError("Timed out waiting for condition.")

    def _maybe_send_status(self):
        if self._link is None or not self._link.is_connected:
            return
        now = self._robot.getTime()
        if now - self._last_status_time < self._config.connection.status_interval_s:
            return
        self._last_status_time = now
        pos = self._sensors.position()
        self._link.send(protocol.status(self._mission.state.value, self._world_position_to_java_dict(pos),
                                         self._sensors.yaw()))

    def _java_area_to_world(self, payload: dict) -> Area:
        """SEARCH_AREA payloads arrive in Java-space (0..java_coord_max on
        each axis); convert both corners independently into world meters."""
        corner_min = self._coords.to_world(float(payload["x_min"]), float(payload["y_min"]))
        corner_max = self._coords.to_world(float(payload["x_max"]), float(payload["y_max"]))
        return Area(corner_min.x, corner_min.y, corner_max.x, corner_max.y)

    def _world_area_to_java_dict(self, area: Area) -> dict:
        corner_min = self._coords.to_java(area.x_min, area.y_min)
        corner_max = self._coords.to_java(area.x_max, area.y_max)
        return {"x_min": corner_min.x, "y_min": corner_min.y, "x_max": corner_max.x, "y_max": corner_max.y}

    def _world_position_to_java_dict(self, position: Vec3) -> dict:
        """x/y go out in Java-space map coordinates; z (altitude) is always
        real-world meters - the Java app's 0..java_coord_max axes only cover
        the flat map, they don't represent altitude."""
        java_xy = self._coords.to_java(position.x, position.y)
        return {"x": java_xy.x, "y": java_xy.y, "z": position.z}

    def _connect_with_retry(self, timeout_s: float) -> TcpMissionLink:
        deadline = time.time() + timeout_s
        last_error = None
        while time.time() < deadline:
            link = TcpMissionLink(self._config.connection.host, self._config.connection.port)
            try:
                link.connect(timeout=2.0)
                return link
            except OSError as exc:
                last_error = exc
                time.sleep(0.5)
        raise ConnectionError(
            f"Could not connect to {self._config.connection.host}:{self._config.connection.port} "
            f"within {timeout_s}s"
        ) from last_error

    def _spawn_bundled_simulator(self):
        controller_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        cmd = [
            sys.executable, "-m", "dronecore.comms.simulator",
            "--host", self._config.connection.host, "--port", str(self._config.connection.port),
        ]
        self._logger.info(f"Auto-starting bundled simulator: {' '.join(cmd)}")
        self._simulator_process = subprocess.Popen(cmd, cwd=controller_dir)

    def _fly_current_leg_with_detection(self) -> bool:
        """Flies toward the flight controller's current target, polling for
        confirmed detections. Returns True if a RETURN_HOME decision aborted
        the mission (the drone will already be landed by the time this
        returns)."""
        def on_tick():
            self._flight.update()
            return self._maybe_poll_detection() == "RETURN_HOME"

        return self._spin_until(lambda: self._flight.reached_target_position(), on_tick=on_tick)

    def _maybe_poll_detection(self) -> Optional[str]:
        now = self._robot.getTime()
        if now - self._last_detection_poll_time < self._config.mission.detection_poll_interval_s:
            return None
        self._last_detection_poll_time = now

        event = self._detection_pipeline.poll(now)
        if event is None:
            return None
        return self._handle_detection_event(event)

    def _handle_detection_event(self, event) -> str:
        self._mission.transition_to(MissionState.AWAITING_DETECTION_DECISION)
        self._flight.hover_in_place()
        self._logger.info(
            f"Human detected near {event.human_position} (confidence={event.confidence:.2f}); "
            "pausing search and notifying the app."
        )
        detection_msg = protocol.human_detected(
            drone_position=self._world_position_to_java_dict(event.drone_position),
            estimated_human_position=self._world_position_to_java_dict(event.human_position),
            confidence=event.confidence,
            sample_image_used=event.sample_image_used,
        )
        self._link.send(detection_msg)

        decision = {"value": None}

        def on_tick():
            self._flight.update()
            msg = self._link.receive(timeout=0)
            if msg and msg.type == MessageType.DETECTION_RESPONSE and msg.in_reply_to == detection_msg.id:
                decision["value"] = msg.payload.get("decision")
            return False

        try:
            self._spin_until(lambda: decision["value"] is not None,
                              timeout=self._config.connection.detection_response_timeout_s, on_tick=on_tick)
        except TimeoutError:
            self._logger.warning("No DETECTION_RESPONSE within timeout; defaulting to CONTINUE_SEARCH.")
            decision["value"] = DetectionDecision.CONTINUE_SEARCH.value

        if decision["value"] == DetectionDecision.RETURN_HOME.value:
            self._execute_return_home()
            return "RETURN_HOME"

        self._mission.transition_to(MissionState.SEARCHING)
        self._logger.info("Resuming search.")
        return "CONTINUE"

    def _execute_return_home(self):
        self._mission.transition_to(MissionState.RETURNING_HOME)
        self._logger.info("Returning to the launch pad...")

        self._flight.set_target_position(self._home_position.x, self._home_position.y)
        self._spin_until(lambda: self._flight.reached_target_position(), on_tick=self._flight.update)

        self._flight.begin_landing()
        self._spin_until(lambda: self._flight.is_touched_down(), on_tick=self._flight.update)
        self._flight.cut_motors()

        self._mission.transition_to(MissionState.LANDED)
        self._logger.info("Landed on the pad.")
