package aloha.robots.headquarters;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;

public class Headquarters {
  private static HeadquartersState state = HeadquartersState.BUILD_ANCHOR;
  private static final Random rng = new Random(6147);

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case BUILD_ANCHOR:   runBuildAnchor(rc);   break;
      case BUILD_CARRIER:  runBuildCarrier(rc);   break;
      default:      throw new RuntimeException("should not be here");
    }
  }

  public static void runBuildAnchor(RobotController rc) throws GameActionException {
      // build an anchor, then move to BUILD_CARRIER state
      if (rc.canBuildAnchor(Anchor.STANDARD)) {
          rc.buildAnchor(Anchor.STANDARD);
          state = HeadquartersState.BUILD_CARRIER;
      }

      return;
  }

  public static void runBuildCarrier(RobotController rc) throws GameActionException {
    // if there are carriers next to us, we assume they are waiting for an anchor. Move to BUILD_ANCHOR state.
    RobotInfo[] robotInfos = rc.senseNearbyRobots(2);
    for (RobotInfo robotInfo : robotInfos) {
      if (robotInfo.type == RobotType.CARRIER) {
        state = HeadquartersState.BUILD_ANCHOR;
        return;
      }
    }

    // no carriers next to us, try to build some
    Direction dir = Utils.directions[rng.nextInt(Utils.directions.length)];
    MapLocation newLoc = rc.getLocation().add(dir);
    if (rc.canBuildRobot(RobotType.CARRIER, newLoc)) {
        rc.buildRobot(RobotType.CARRIER, newLoc);
        return;
    }
  }
}
