package aloha.robots.launcher;

import battlecode.common.*;

import java.util.*;
import aloha.pathing.*;
import aloha.utils.*;

public class Launcher {
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();

public static void run(RobotController rc) throws GameActionException {
    enemyAction(rc);

    // No enemies found. Explore.
    Optional<Direction> dir = explorePathFinder.findPath(rc.getLocation(), null, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
    }
  }

  //TODO read communication buffer (flock to a location)

  private static void enemyAction(RobotController robotController) throws GameActionException {
    MapLocation myLocation = robotController.getLocation();

    Optional<MapLocation> enemyLocation = computeLocalWeightedFlockEnemy(robotController);
    if(enemyLocation.isPresent()) {
        if (robotController.canAttack(enemyLocation.get())) {
            robotController.setIndicatorString("Attacking");
            robotController.attack(enemyLocation.get());
        }

        // Optional<MapLocation> location = avoidEnemy(robotController);
        // if(location.isPresent()) {
        //     Optional<Direction> dir = explorePathFinder.findPath(location.get(), null, robotController);
        //     if (dir.isPresent() && robotController.canMove(dir.get())) {
        //         robotController.move(dir.get());
        //         return;
        //     }
        // }

        // Flock when there are enemies around
        Optional<MapLocation> flockLocation = computeLocalWeightedFlock(robotController);
        if(flockLocation.isPresent()) {
            Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, flockLocation.get(), robotController);
            if (dir.isPresent() && robotController.canMove(dir.get())) {
                robotController.move(dir.get());
            }
        }
    }
  }

  private static Optional<MapLocation> computeLocalWeightedFlock(RobotController robotController) throws GameActionException {
      int visionRadius = robotController.getType().visionRadiusSquared;
      Team team = robotController.getTeam();
      RobotInfo[] visionRadiusTeamInfo = robotController.senseNearbyRobots(visionRadius, team);
      MapLocation location = robotController.getLocation();

      return getWeightedLocation(location, visionRadiusTeamInfo);
  }

  private static Optional<MapLocation> getWeightedLocation(MapLocation location,RobotInfo[] robotInfos) {
        if(robotInfos.length == 0) {
            return Optional.empty();
        }

        int cohesionXPosition = 0;
        int cohesionYPosition = 0;
        int seperationXPosition = 0;
        int seperationYPosition = 0;
        for(int i = 0; i < robotInfos.length; ++i) {
            RobotInfo robot = robotInfos[i];
            MapLocation robotMapLocation = robot.getLocation();
            cohesionXPosition += robotMapLocation.x;
            cohesionYPosition += robotMapLocation.y;
            seperationXPosition += (robotMapLocation.x - location.x);
            seperationYPosition += (robotMapLocation.y - location.y);
        }
        cohesionXPosition /= robotInfos.length;
        cohesionYPosition /= robotInfos.length;

        // We don't want the robots to be too close to each other add counter distance
        seperationXPosition /= robotInfos.length;
        seperationYPosition /= robotInfos.length;
        seperationXPosition *= -0.5;
        seperationYPosition *= -0.5;



        MapLocation localWeight = new MapLocation(cohesionXPosition + seperationXPosition, cohesionYPosition + seperationYPosition);

        return Optional.of(localWeight);
  }

  private static Optional<MapLocation> avoidEnemy(RobotController robotController) throws GameActionException {
    int actionRadius = robotController.getType().actionRadiusSquared;
    Team team = robotController.getTeam();
    RobotInfo[] actionRadiusTeamInfo = robotController.senseNearbyRobots(actionRadius, team);

    MapLocation location = robotController.getLocation();
    MapLocation localWeightTeam = getWeightedLocation(location, actionRadiusTeamInfo).orElse(robotController.getLocation());

    RobotInfo[] actionRadiusEnemyInfo = robotController.senseNearbyRobots(actionRadius, team.opponent());
    if(actionRadiusTeamInfo.length < actionRadiusEnemyInfo.length) {
        MapLocation localWeightEnemy = getWeightedLocation(location, actionRadiusEnemyInfo).orElse(location);
        int x = localWeightTeam.x - localWeightEnemy.x;
        int y = localWeightTeam.y - localWeightEnemy.y;
        if(x < 0) { // we are on the left side
            x = localWeightTeam.x / 2;
        } else {
            x = localWeightTeam.x * 2;
        }

        if(y < 0) { // we are on the bottom
            y = localWeightTeam.y / 2;
        } else {
            y = localWeightTeam.y * 2;
        }

        return Optional.of(new MapLocation(x, y));
    }

    return Optional.empty();
  }

  private static Optional<MapLocation> computeLocalWeightedFlockEnemy(RobotController robotController) throws GameActionException {
      int actionRadius = robotController.getType().actionRadiusSquared;
      Team team = robotController.getTeam();
      RobotInfo[] actionRadiusTeamInfo = robotController.senseNearbyRobots(actionRadius, team);

      MapLocation location = robotController.getLocation();
      MapLocation localWeightTeam = getWeightedLocation(location, actionRadiusTeamInfo).orElse(robotController.getLocation());

      RobotInfo[] actionRadiusEnemyInfo = robotController.senseNearbyRobots(actionRadius, team.opponent());

      if(actionRadiusEnemyInfo.length > 0) {
        // distance, robot index
        int[] minDistance = {Integer.MAX_VALUE, 0};
        // health, robot index, distance
        int[] minHealth = {Integer.MAX_VALUE, 0, 0};
        for(int i = 0; i < actionRadiusEnemyInfo.length; ++i) {
            RobotInfo robot = actionRadiusEnemyInfo[i];
            MapLocation robotMapLocation = robot.getLocation();
            // We try to focus on an enemy by hitting the closest enemy to the relative
            // action radius of the flock
            int distance = localWeightTeam.distanceSquaredTo(robotMapLocation);
            if(distance < minDistance[0]) {
                minDistance[0] = distance;
                minDistance[1] = i;
            }
            if(robot.health < minHealth[0]) {
                minHealth[0] = robot.health;
                minHealth[1] = i;
                minHealth[2] = distance;
            }
            //enemy carrier holding anchor go kill TODO
        }
        // If we found a robot with low health hit it even if the distance may not be the minimal distance
        // This way we can eliminate low health enemies
        if(minHealth[2] * .75 < minDistance[0]) {
              return Optional.of(actionRadiusEnemyInfo[minHealth[1]].getLocation());
        }

        return Optional.of(actionRadiusEnemyInfo[minDistance[1]].getLocation());
      }
      return Optional.empty();
  }

}
