package aloha.robots.headquarters;

import battlecode.common.*;
import java.util.*;
import aloha.utils.Utils;

public class Headquarters {
  static final Random rng = new Random(6147);

  public static void run(RobotController rc) throws GameActionException {
    // Pick a direction to build in.
    Direction dir = Utils.directions[rng.nextInt(Utils.directions.length)];
    MapLocation newLoc = rc.getLocation().add(dir);
    if (rc.canBuildAnchor(Anchor.STANDARD)) {
        // If we can build an anchor do it!
        rc.buildAnchor(Anchor.STANDARD);
        rc.setIndicatorString("Building anchor! " + rc.getAnchor());
    }
    if (rng.nextBoolean()) {
        // Let's try to build a carrier.
        rc.setIndicatorString("Trying to build a carrier");
        if (rc.canBuildRobot(RobotType.CARRIER, newLoc)) {
            rc.buildRobot(RobotType.CARRIER, newLoc);
        }
    } else {
        // Let's try to build a launcher.
        rc.setIndicatorString("Trying to build a launcher");
        if (rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) {
            rc.buildRobot(RobotType.LAUNCHER, newLoc);
        }
    }
  }
}
