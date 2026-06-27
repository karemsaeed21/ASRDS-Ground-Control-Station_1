"""Webots entry point for the mavic_mission controller.

This file is intentionally thin: all real logic lives in the `dronecore`
package alongside it. To change mission behavior, write a different script
against the `Drone` API below rather than editing dronecore's internals.
"""

from dronecore import Drone

drone = Drone()
drone.connect()
area = drone.wait_for_search_area()
drone.takeoff()
outcome = drone.search(area)
drone.return_home()
