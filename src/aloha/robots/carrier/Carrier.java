package aloha.robots.carrier;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;
import aloha.pathing.*;
import aloha.communication.*;
import static aloha.RobotPlayer.OPPONENT;

public class Carrier {
  private static CarrierState state = CarrierState.TAKE_ANCHOR;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final PathFinder randomPathFinder = new RandomPathFinder();
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();

  private static MapLocation hqLoc;
  private static MapLocation dst;

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case FIND_HQ:           runFindHQ(rc);          break;
      case TAKE_ANCHOR:       runTakeAnchor(rc);      break;
      case PLACE_ANCHOR:      runPlaceAnchor(rc);     break;
      case ATTACK_LOC:        runAttackLoc(rc);       break;
      default:      throw new RuntimeException("should not be here");
    }
  }

  private static void runFindHQ(RobotController rc) throws GameActionException {
    // TODO
  }

  private static void runTakeAnchor(RobotController rc) throws GameActionException {
    // Identify an HQ.
    if (hqLoc == null) {
      RobotInfo[] robotInfos = rc.senseNearbyRobots();
      for (RobotInfo robotInfo : robotInfos) {
        if (robotInfo.type == RobotType.HEADQUARTERS) {
          hqLoc = robotInfo.location;
          break;
        }
      }

      // No HQs in sight. Try to find one.
      if (hqLoc == null) {
        state = CarrierState.FIND_HQ;
        return;
      }
    }

    // If we're already holding an anchor, go to PLACE_ANCHOR state
    if (rc.getAnchor() != null) {
      state = CarrierState.PLACE_ANCHOR;
      // reset state used during PLACE_ANCHOR state
      dst = null;
      return;
    }

    // Try to take an anchor from the HQ.
    rc.setIndicatorString("trying to take an anchor from " + hqLoc);
    if (rc.canTakeAnchor(hqLoc, Anchor.STANDARD)) {
      rc.takeAnchor(hqLoc, Anchor.STANDARD);
      state = CarrierState.PLACE_ANCHOR;
      // reset state used during PLACE_ANCHOR state
      dst = null;
      return;
    }

    rc.setIndicatorLine(rc.getLocation(), hqLoc, 100, 0, 0);
    // If we can't yet take the anchor, move in the direction of the HQ.
    Optional<Direction> dir = fuzzyPathFinder.findPath(rc.getLocation(), hqLoc, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
    }

    // If we receive any enemy location messages, go attack that location
    List<Message> messages = communicator.receiveMessages(MessageType.ENEMY_LOC, rc);
    if (messages.size() != 0) {
      Message message = messages.get(0);
      state = CarrierState.ATTACK_LOC;
      dst = message.loc;
      return;
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

    rc.setIndicatorString("trying to place anchor at " + dst);
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

  private static void runAttackLoc(RobotController rc) throws GameActionException {
    MapLocation myLocation = rc.getLocation();

    // If we aren't close enough to the attack location, move towards it
    if (myLocation.distanceSquaredTo(dst) > RobotType.CARRIER.visionRadiusSquared) {
      Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, dst, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }
      return;
    }

    RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.CARRIER.visionRadiusSquared, OPPONENT);

    // If there are no enemies in sight, then try to get a bit closer to the
    //  attack location. If there are STILL no enemies in sight when we get close,
    //  go to TAKE_ANCHOR state.
    if (enemies.length == 0) {
      if (myLocation.distanceSquaredTo(dst) <= 8) {
        state = CarrierState.TAKE_ANCHOR;
        return;
      }

      Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, dst, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }
      return;
    }

    // Find the enemy in sight with the lowest health
    RobotInfo enemyWithLowestHealth = null;
    for (RobotInfo enemy : enemies) {
      if (enemyWithLowestHealth == null || enemy.getHealth() < enemyWithLowestHealth.getHealth()) {
        enemyWithLowestHealth = enemy;
      }
    }

    // Try to attack enemy with lowest health
    if (rc.canAttack(enemyWithLowestHealth.location)) {
      rc.attack(enemyWithLowestHealth.location);
      return;
    }

    // Can't attack enemy, get closer to the enemy
    Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemyWithLowestHealth.location, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
    }
  }
}
