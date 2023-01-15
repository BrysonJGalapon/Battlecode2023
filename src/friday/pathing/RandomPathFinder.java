package aloha.pathing;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;

public class RandomPathFinder implements PathFinder {
  private static final Random rng = Utils.getRng();

  public Optional<Direction> findPath(MapLocation src, MapLocation dst, RobotController rc) throws GameActionException {
    Direction dir = Utils.directions[rng.nextInt(Utils.directions.length)];
    if (rc.canMove(dir)) {
        rc.move(dir);
    }
    return Optional.empty();
  }
}
