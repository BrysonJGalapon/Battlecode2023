package thursday.robots.launcher;

import battlecode.common.*;

import java.util.*;
import thursday.pathing.*;
import thursday.utils.*;
import static thursday.RobotPlayer.OPPONENT;

public class Launcher {
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();

public static void run(RobotController rc) throws GameActionException {
  MapLocation myLocation = rc.getLocation();
  RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.LAUNCHER.visionRadiusSquared, OPPONENT);
  RobotInfo enemyToAttack = null;
  // Attack the closest enemy
  for (RobotInfo enemy : enemies) {
    if (enemy.type != RobotType.HEADQUARTERS && (enemyToAttack == null ||  myLocation.distanceSquaredTo(enemy.location) <  myLocation.distanceSquaredTo(enemyToAttack.location))) {
      enemyToAttack = enemy;
    }
  }

  if (enemyToAttack != null) {
    if (rc.canAttack(enemyToAttack.location)) {
      rc.attack(enemyToAttack.location);
      return;
    }

    Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemyToAttack.location, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
      return;
    }

    return;
  }

  // Path to HQ and kill all newly-created robots
  if (enemies.length > 0) {
    Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemies[0].location, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
      return;
    }
  }

  // Search for something to attack
  Optional<Direction> dir = explorePathFinder.findPath(myLocation, null, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
      return;
    }
  }
}
