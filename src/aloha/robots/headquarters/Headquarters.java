package aloha.robots.headquarters;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;
import aloha.communication.*;
import static aloha.RobotPlayer.MY_TEAM;
import static aloha.RobotPlayer.OPPONENT;

public class Headquarters {
  private static HeadquartersState state = HeadquartersState.BUILD_CARRIER;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final Random rng = Utils.getRng();

  private static int buildAnchorCooldown = 0;
  private static int consectiveTurnsWithoutCarriers = 0;

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case BUILD_ANCHOR:    runBuildAnchor(rc);    break;
      case BUILD_CARRIER:   runBuildCarrier(rc);   break;
      case BUILD_LAUNCHER:  runBuildLauncher(rc);  break;
      case BUILD_AMPLIFIER: runBuildAmplifier(rc); break;
      default:      throw new RuntimeException("should not be here");
    }

    // at the end of each turn, communicate headquarters state
    Message message = Message.builder(MessageType.HQ_STATE)
      .hqState(state)
      .loc(rc.getLocation())
      .build();
    communicator.sendMessage(message, rc);
  }

  public static void runBuildAmplifier(RobotController rc) throws GameActionException {
    rc.setIndicatorString("building amplifier");
    // build an anchor, then move to BUILD_CARRIER state
    if (rc.canBuildAnchor(Anchor.STANDARD)) {
        rc.buildAnchor(Anchor.STANDARD);
        state = HeadquartersState.BUILD_CARRIER;
        buildAnchorCooldown = 100;
    }

    return;
  }

  public static void runBuildAnchor(RobotController rc) throws GameActionException {
    rc.setIndicatorString("building anchor");
    // If we haven't seen a carrier for a while, go to build carrier state
    RobotInfo[] robotInfos = rc.senseNearbyRobots(2, MY_TEAM);
    boolean isCarrierNextToUs = false;
    for (RobotInfo robotInfo : robotInfos) {
      if (robotInfo.type == RobotType.CARRIER) {
        isCarrierNextToUs = true;
        break;
      }
    }
    if (!isCarrierNextToUs) {
      consectiveTurnsWithoutCarriers++;
    } else {
      consectiveTurnsWithoutCarriers = 0;
    }
    if (consectiveTurnsWithoutCarriers >= 20) {
      state = HeadquartersState.BUILD_CARRIER;
      return;
    }

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
      RobotInfo[] robotInfos = rc.senseNearbyRobots(2, MY_TEAM);
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
