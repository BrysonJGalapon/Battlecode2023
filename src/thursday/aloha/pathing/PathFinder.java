package aloha.pathing;

import battlecode.common.*;
import java.util.*;

public interface PathFinder {
  // returns the direction to move in for a robot starting at src that wants to move to dst
  Optional<Direction> findPath(MapLocation src, MapLocation dst, RobotController rc) throws GameActionException;
}
