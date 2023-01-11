package aloha.robots.headquarters;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;

public class Headquarters {
  private static HeadquartersState state = HeadquartersState.BUILD_ANCHOR;
  private static final Random rng = new Random(6147);

  private static int buildAnchorCooldown = 0;

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case BUILD_ANCHOR:   runBuildAnchor(rc);   break;
      case BUILD_CARRIER:  runBuildCarrier(rc);   break;
      default:      throw new RuntimeException("should not be here");
    }
  }

  public static void runBuildAnchor(RobotController rc) throws GameActionException {
      rc.setIndicatorString("building anchor");
      // build an anchor, then move to BUILD_CARRIER state
      if (rc.canBuildAnchor(Anchor.STANDARD)) {
          rc.buildAnchor(Anchor.STANDARD);
          state = HeadquartersState.BUILD_CARRIER;
          buildAnchorCooldown = 10;
      }

      return;
  }

  public static void runBuildCarrier(RobotController rc) throws GameActionException {
    rc.setIndicatorString("building carrier");

    // wait for some time before building an anchor again
    if (buildAnchorCooldown == 0) {
      // if there are carriers next to us after cooling down, we assume they are waiting for an anchor. Move to BUILD_ANCHOR state.
      RobotInfo[] robotInfos = rc.senseNearbyRobots(2);
      for (RobotInfo robotInfo : robotInfos) {
        if (robotInfo.type == RobotType.CARRIER) {
          state = HeadquartersState.BUILD_ANCHOR;
          return;
        }
      }
    } else if (buildAnchorCooldown > 0) {
      buildAnchorCooldown -= 1;
    }

    // no carriers next to us, try to build some carriers
    Direction dir = Utils.directions[rng.nextInt(Utils.directions.length)];
    MapLocation newLoc = rc.getLocation().add(dir);
    if (rc.canBuildRobot(RobotType.CARRIER, newLoc)) {
        rc.buildRobot(RobotType.CARRIER, newLoc);
        return;
    }
  }
}
