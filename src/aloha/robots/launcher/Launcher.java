package aloha.robots.launcher;

import battlecode.common.*;

import java.util.*;
import aloha.pathing.*;
import aloha.utils.*;
import aloha.communication.*;
import static aloha.RobotPlayer.MY_TEAM;
import static aloha.RobotPlayer.OPPONENT;

public class Launcher {
  private static LauncherState state = LauncherState.PROTECT_WELL;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();

  private static MapLocation dst = null;

  private static Set<MapLocation> knownWellLocations = new HashSet<>();

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case FOLLOWER:            runFollower(rc);    break;
      case OCCUPY_SKY_ISLAND:   runOccupySkyIsland(rc);   break;
      case PROTECT_WELL:        runProtectWell(rc);  break;
      case CROWD_HQ:            runCrowdHQ(rc); break;
      default:                  throw new RuntimeException("should not be here");
    }
  }

  private static void runFollower(RobotController rc) throws GameActionException {
    rc.setIndicatorString("following");
    MapLocation myLocation = rc.getLocation();

    // Find a friendly robot in sight with the lowest ID -- call it the leader
    RobotInfo[] friendlyRobots = rc.senseNearbyRobots(RobotType.LAUNCHER.visionRadiusSquared, MY_TEAM);
    RobotInfo robotToFollow = null;
    for (RobotInfo friendlyRobot : friendlyRobots) {
      if (friendlyRobot.type != RobotType.LAUNCHER) {
        continue; // Ignore non-launcher robots
      }

      if (robotToFollow == null || friendlyRobot.ID < robotToFollow.ID) {
        robotToFollow = friendlyRobot;
      }
    }

    // Find the enemy closest to us
    RobotInfo[] enemyRobots = rc.senseNearbyRobots(RobotType.LAUNCHER.visionRadiusSquared, OPPONENT);
    RobotInfo enemyToAttack = null;
    for (RobotInfo enemy : enemyRobots) {
      if (enemy.type == RobotType.HEADQUARTERS) {
        continue; // No point in attacking HEADQUARTERS
      }

      if (enemyToAttack == null || myLocation.distanceSquaredTo(enemy.location) < myLocation.distanceSquaredTo(enemyToAttack.location)) {
        enemyToAttack = enemy;
      }
    }

    // No leader to follow, so attack if we can, and then move to a different state
    if (robotToFollow == null) {
      // Attack the closest enemy
      if (enemyToAttack != null && rc.canAttack(enemyToAttack.location)) {
        rc.attack(enemyToAttack.location);
      }

      state = LauncherState.PROTECT_WELL;
      return;
    }

    // Get out of the follower state if there are too little resources, or the herd
    //  is too big.
    if (!enoughResources(rc) || herdIsTooBig(rc)) {
      // Attack the closest enemy
      if (enemyToAttack != null && rc.canAttack(enemyToAttack.location)) {
        rc.attack(enemyToAttack.location);
      }

      state = LauncherState.PROTECT_WELL;
      return;
    }

    // If the leader we are following is getting too far away, or we don't see any enemies to attack, path closer to it
    if (myLocation.distanceSquaredTo(robotToFollow.location) > 2 || enemyToAttack == null) {
      Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, robotToFollow.location, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }
    } else { // Otherwise, path towards the enemy
      Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemyToAttack.location, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }
    }

    // Attack the closest enemy
    if (enemyToAttack != null && rc.canAttack(enemyToAttack.location)) {
      rc.attack(enemyToAttack.location);
    }
  }

  private static void runProtectWell(RobotController rc) throws GameActionException {
    rc.setIndicatorString("protecting");
    MapLocation myLocation = rc.getLocation();

    // Attack the enemy closest to us
    RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.LAUNCHER.visionRadiusSquared, OPPONENT);
    RobotInfo enemyToAttack = null;
    for (RobotInfo enemy : enemies) {
      if (enemy.type != RobotType.HEADQUARTERS && (enemyToAttack == null ||  myLocation.distanceSquaredTo(enemy.location) <  myLocation.distanceSquaredTo(enemyToAttack.location))) {
        enemyToAttack = enemy;
      }
    }
    if (enemyToAttack != null) {
      if (rc.canAttack(enemyToAttack.location)) {
        rc.attack(enemyToAttack.location);
      }
    }

    // If we don't already have a well location set, try to find a well location
    if (dst == null) {
      // Try to use the closest known cached well location.
      for (MapLocation loc : knownWellLocations) {
        if (dst == null || myLocation.distanceSquaredTo(loc) < myLocation.distanceSquaredTo(dst)) {
          dst = loc;
        }
      }

      // No cached well locations. Find nearby wells in sight, and path to the closest one.
      WellInfo[] wells = rc.senseNearbyWells();
      for (WellInfo well : wells) {
        if (dst == null || myLocation.distanceSquaredTo(well.getMapLocation()) < myLocation.distanceSquaredTo(dst)) {
          dst = well.getMapLocation();
        }

        knownWellLocations.add(well.getMapLocation());
      }

      // No wells in sight. Get messages for wells and find the closest one.
      if (dst == null) {
        for (Message mnWellMessage : communicator.receiveMessages(MessageType.MN_WELL_LOC, rc) ) {
          knownWellLocations.add(mnWellMessage.loc);
        }
        for (Message adWellMessage : communicator.receiveMessages(MessageType.AD_WELL_LOC, rc) ) {
        knownWellLocations.add(adWellMessage.loc);
        }
        for (MapLocation wellLocation : knownWellLocations) {
          if (dst == null || myLocation.distanceSquaredTo(wellLocation) < myLocation.distanceSquaredTo(wellLocation)) {
            dst = wellLocation;
          }
        }
      }

      // Could not find any well locations. Explore, or follow the enemy we attacked.
      if (dst == null) {
        if (enemyToAttack != null) {
          Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemyToAttack.location, rc);
          if (dir.isPresent() && rc.canMove(dir.get())) {
            rc.move(dir.get());
          }
        } else {
          Optional<Direction> dir = explorePathFinder.findPath(myLocation, null, rc);
          if (dir.isPresent() && rc.canMove(dir.get())) {
            rc.move(dir.get());
          }
        }
        return;
      }
    }

    // If we're too far from our dst, path to it.
    if (myLocation.distanceSquaredTo(dst) > 5) {
      Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, dst, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }
    }

    // Find a friendly robot in sight with the lowest ID, it can be a possible leader
    RobotInfo[] friendlyRobots = rc.senseNearbyRobots(RobotType.LAUNCHER.visionRadiusSquared, MY_TEAM);
    RobotInfo robotToFollow = null;
    for (RobotInfo friendlyRobot : friendlyRobots) {
      if (friendlyRobot.type != RobotType.LAUNCHER) {
        continue; // Ignore non-launcher robots
      }

      if (friendlyRobot.ID < rc.getID() && (robotToFollow == null || friendlyRobot.ID < robotToFollow.ID)) {
        robotToFollow = friendlyRobot;
      }
    }

    // Found a different leader, follow that robot instead, but only if we have
    //  enough resources to justify losing the resposibility this robot had.
    if (robotToFollow != null && enoughResources(rc)) {
      if (myLocation.distanceSquaredTo(robotToFollow.location) > 2 || enemyToAttack == null) {
        Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, robotToFollow.location, rc);
        if (dir.isPresent() && rc.canMove(dir.get())) {
          rc.move(dir.get());
        }
      } else { // Otherwise, path towards the enemy
        Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemyToAttack.location, rc);
        if (dir.isPresent() && rc.canMove(dir.get())) {
          rc.move(dir.get());
        }
      }

      state = LauncherState.FOLLOWER;
      return;
    }

    // No new leader found, or not enough resources to justify herding at this point

    if (enemyToAttack != null) {
      Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemyToAttack.location, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }

      return;
    }

    // No enemies in sight. Path to the carrier closest to the dst.
    RobotInfo targetCarrier = null;
    for (RobotInfo friendlyRobot : friendlyRobots) {
      if (friendlyRobot.type != RobotType.CARRIER) {
        continue;
      }

      if (targetCarrier == null || friendlyRobot.location.distanceSquaredTo(dst) < targetCarrier.location.distanceSquaredTo(dst)) {
        targetCarrier = friendlyRobot;
      }
    }
    if (targetCarrier != null) {
      Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, targetCarrier.location, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }

      return;
    }


    // Search for something to attack
    Optional<Direction> dir = explorePathFinder.findPath(myLocation, null, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
    }
  }

  private static void runOccupySkyIsland(RobotController rc) throws GameActionException {
    // TODO
  }

  private static void runCrowdHQ(RobotController rc) throws GameActionException {
    // TODO
  }

  private static boolean enoughResources(RobotController rc) throws GameActionException {
    // TODO tune the magic number. Smaller means we herd more loosely, larger means
    //  we herd more strongly
    int magicNumber = 80;
    return rc.getRobotCount() * magicNumber > rc.getMapHeight() * rc.getMapWidth();
  }

  private static boolean herdIsTooBig(RobotController rc) throws GameActionException {
    RobotInfo[] friendlyRobots = rc.senseNearbyRobots(RobotType.LAUNCHER.visionRadiusSquared, MY_TEAM);
    // TODO tune the magic number. Smaller means larger herds, larger means smaller herds.
    int magicNumber = 3;
    return friendlyRobots.length * magicNumber > RobotType.LAUNCHER.visionRadiusSquared;
  }
}
