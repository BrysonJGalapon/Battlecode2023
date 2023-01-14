package aloha.robots.launcher;

import battlecode.common.*;
import java.util.*;
import aloha.pathing.*;
import aloha.utils.Utils;

public class Launcher {
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();

  public static void run(RobotController rc) throws GameActionException {
    MapLocation myLocation = rc.getLocation();
    // Try to attack someone
    int radius = rc.getType().actionRadiusSquared;
    Team opponent = rc.getTeam().opponent();
    RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
    if (enemies.length > 0) {
      MapLocation toAttack = enemies[0].location;

      if (rc.canAttack(toAttack)) {
          rc.setIndicatorString("Attacking");
          rc.attack(toAttack);
      }

      // Try to move towards the enemy.
      Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemies[0].location, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
        return;
      }
    }

    // No enemies found. Explore.
    Optional<Direction> dir = explorePathFinder.findPath(myLocation, null, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
    }
  }
}
