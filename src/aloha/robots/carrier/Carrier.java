package aloha.robots.carrier;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;
import aloha.pathing.*;

public class Carrier {
  private static CarrierState state = CarrierState.TAKE_ANCHOR;
  private static final PathFinder randomPathFinder = new RandomPathFinder();
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();

  private static MapLocation hqLoc;
  private static MapLocation dst;

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case TAKE_ANCHOR:       runTakeAnchor(rc);      break;
      case PLACE_ANCHOR:      runPlaceAnchor(rc);     break;
      default:      throw new RuntimeException("should not be here");
    }
  }

  private static void runTakeAnchor(RobotController rc) throws GameActionException {
    if (hqLoc == null) {
      RobotInfo[] robotInfos = rc.senseNearbyRobots();
      for (RobotInfo robotInfo : robotInfos) {
        if (robotInfo.type == RobotType.HEADQUARTERS) {
          hqLoc = robotInfo.location;
          break;
        }
      }
    }

    rc.setIndicatorString("trying to take an anchor from " + hqLoc);
    if (rc.canTakeAnchor(hqLoc, Anchor.STANDARD)) {
      rc.takeAnchor(hqLoc, Anchor.STANDARD);
      state = CarrierState.PLACE_ANCHOR;
      // reset state used during PLACE_ANCHOR state
      dst = null;
    }

    rc.setIndicatorLine(rc.getLocation(), hqLoc, 100, 0, 0);
    // if we can't yet take the anchor, move in the direction of the hq
    Optional<Direction> dir = fuzzyPathFinder.findPath(rc.getLocation(), hqLoc, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
    }
  }

  private static void runPlaceAnchor(RobotController rc) throws GameActionException {
    rc.setIndicatorString("trying to place anchor");
    // find sky islands in view
    if (dst == null) {
      int[] nearbyIslands = rc.senseNearbyIslands();
      for (int nearbyIsland : nearbyIslands) {
        if (rc.senseAnchor(nearbyIsland) == null) {
          dst = rc.senseNearbyIslandLocations(nearbyIsland)[0];
        }
      }
    }

    // if no sky islands in view, move randomly and end turn
    if (dst == null) {
      Optional<Direction> dir = randomPathFinder.findPath(null, null, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }

      return;
    }

    // if we can place an anchor, place it and go to TAKE_ANCHOR state
    if (rc.canPlaceAnchor()) {
      rc.placeAnchor();
      state = CarrierState.TAKE_ANCHOR;
      return;
    }

    rc.setIndicatorLine(rc.getLocation(), dst, 100, 0, 0);
    // if we have a dst but can't yet place the anchor, move in the direction of the sky-island
    Optional<Direction> dir = fuzzyPathFinder.findPath(rc.getLocation(), dst, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
    }
  }
}
