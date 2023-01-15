package aloha.robots.headquarters;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;
import aloha.communication.*;

public class Headquarters {
  private static HeadquartersState state = HeadquartersState.BUILD_CARRIER;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final Random rng = Utils.getRng();

  private static int buildAnchorCooldown = 0;

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case BUILD_ANCHOR:   runBuildAnchor(rc);    break;
      case BUILD_CARRIER:  runBuildCarrier(rc);   break;
      case BUILD_LAUNCHER: runBuildLauncher(rc);  break;
      default:      throw new RuntimeException("should not be here");
    }
  }

  public static void runBuildAnchor(RobotController rc) throws GameActionException {
      // build an anchor, then move to BUILD_CARRIER state
      if (rc.canBuildAnchor(Anchor.STANDARD)) {
          rc.buildAnchor(Anchor.STANDARD);
          state = HeadquartersState.BUILD_CARRIER;
          buildAnchorCooldown = 100;
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

    // Try to build some carriers
    Direction dir = Utils.directions[rng.nextInt(Utils.directions.length)];
    MapLocation newLoc = rc.getLocation().add(dir);
    if (rc.canBuildRobot(RobotType.CARRIER, newLoc)) {
        rc.buildRobot(RobotType.CARRIER, newLoc);

        // If we have enough mana, build launchers
        if (rc.getResourceAmount(ResourceType.MANA) >= 60) {
          state = HeadquartersState.BUILD_LAUNCHER;
          return;
        }

        return;
    }
  }

  public static void runBuildLauncher(RobotController rc) throws GameActionException {
    rc.setIndicatorString("building launcher");

    // Rry to build some launchers
    Direction dir = Utils.directions[rng.nextInt(Utils.directions.length)];
    MapLocation newLoc = rc.getLocation().add(dir);
    if (rc.canBuildRobot(RobotType.LAUNCHER, newLoc)) {
        rc.buildRobot(RobotType.LAUNCHER, newLoc);

        // If we have enough admantinium, build carriers
        if (rc.getResourceAmount(ResourceType.ADAMANTIUM) >= 50) {
          state = HeadquartersState.BUILD_CARRIER;
          return;
        }

        return;
    }
  }
}
