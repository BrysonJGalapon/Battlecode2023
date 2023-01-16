package aloha.robots.launcher;

import battlecode.common.*;

import java.util.*;
import aloha.pathing.*;
import aloha.utils.*;

public class Launcher {
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();
  private static final List<RobotType> include = Arrays.asList(new RobotType[] { RobotType.LAUNCHER });
  private static MapLocation lastFlockLocation;
  private static Set<MapLocation> seenIsland;
  private static Set<MapLocation> recentlyVisitedIsland;

public static void run(RobotController rc) throws GameActionException {
    boolean attacked = enemyAction(rc);

    // No enemies found. Explore.
    MapLocation localFlock = computeLocalWeightedFlock(rc).orElse(rc.getLocation());
    goInGeneralDirectionOfFlock(rc, localFlock);
    lastFlockLocation = localFlock;

    if(!attacked) {
        enemyAction(rc);
    }
  }

  public static void goInGeneralDirectionOfFlock(RobotController robotController, MapLocation localFlock) throws GameActionException {
    int actionRadius = (int) robotController.getType().visionRadiusSquared;
    int diffX = lastFlockLocation.x - localFlock.x;
    int random = actionRadius;
    if(diffX < 0) { //flock moved right 
        diffX = robotController.getLocation().x + random;
    } else {
        diffX = robotController.getLocation().x - random;
    }

    int diffY = lastFlockLocation.y - localFlock.y;
    if(diffY < 0) { // flock moved up
        diffY = robotController.getLocation().y + random;
    } else {
        diffY = robotController.getLocation().y - random;
    }
    MapLocation nextMapLocation = new MapLocation(diffX, diffY);
    Optional<Direction> dir = explorePathFinder.findPath(robotController.getLocation(), nextMapLocation, robotController);
    if (dir.isPresent() && robotController.canMove(dir.get())) {
        robotController.move(dir.get());
        robotController.setIndicatorString("Going general direction of flock!");
    }
  }

  //TODO read communication buffer (flock to a location)

  private static boolean enemyAction(RobotController robotController) throws GameActionException {
      boolean attacked = false;
    MapLocation myLocation = robotController.getLocation();

    Optional<MapLocation> enemyLocation = computeLocalWeightedFlockEnemy(robotController);

    if(enemyLocation.isPresent()) {
        if (robotController.canAttack(enemyLocation.get())) {
            robotController.setIndicatorString("Attacking");
            robotController.attack(enemyLocation.get());
            attacked = true;
        }

        Optional<MapLocation> avoidEnemy = avoidEnemy(robotController);
        if(avoidEnemy.isPresent()) {
            Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, avoidEnemy.get(), robotController);
            if (dir.isPresent() && robotController.canMove(dir.get())) {
                robotController.move(dir.get());
                robotController.setIndicatorString("Moving away from enemy!");
            }
            return attacked;
        }

        //If we can win move towards the enemy
        Optional<Direction> dir = fuzzyPathFinder.findPath(myLocation, enemyLocation.get(), robotController);
        if (dir.isPresent() && robotController.canMove(dir.get()) && robotController.canSenseRobotAtLocation(enemyLocation.get())) {
            robotController.move(dir.get());
            robotController.setIndicatorString("Moving towards enemy!");
        }
    }
    return attacked;
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

        if(lastFlockLocation == null) {
            lastFlockLocation = location;
        }

        int cohesionXPosition = 0;
        int cohesionYPosition = 0;
        int alignmentX = 0;
        int alignmentY = 0;
        int countedRobots = 0;
        for(int i = 0; i < robotInfos.length; ++i) {
            RobotInfo robot = robotInfos[i];
            MapLocation robotMapLocation = robot.getLocation();
            cohesionXPosition += robotMapLocation.x;
            cohesionYPosition += robotMapLocation.y;
            alignmentX += lastFlockLocation.x - robotMapLocation.x;
            alignmentY += lastFlockLocation.y - robotMapLocation.y;
            ++countedRobots;
        }
        cohesionXPosition /= countedRobots;
        cohesionYPosition /= countedRobots;
        
        alignmentX /= countedRobots;
        alignmentY /= countedRobots;

        MapLocation localWeight = new MapLocation(alignmentX + cohesionXPosition, alignmentY + cohesionYPosition);

        return Optional.of(localWeight);
  }

  private static Optional<MapLocation> avoidEnemy(RobotController robotController) throws GameActionException {
    int actionRadius = robotController.getType().actionRadiusSquared;
    Team team = robotController.getTeam();
    RobotInfo[] actionRadiusTeamInfo = robotController.senseNearbyRobots(actionRadius, team);

    MapLocation location = robotController.getLocation();
    MapLocation localWeightTeam = getWeightedLocation(location, actionRadiusTeamInfo).orElse(robotController.getLocation());

    RobotInfo[] actionRadiusEnemyInfo = robotController.senseNearbyRobots(actionRadius, team.opponent());
    int maxRadiusAway = robotController.getType().visionRadiusSquared;
    if(actionRadiusTeamInfo.length < actionRadiusEnemyInfo.length) {
        MapLocation localWeightEnemy = getWeightedLocation(location, actionRadiusEnemyInfo).orElse(location);
        int x = localWeightTeam.x - localWeightEnemy.x;
        int y = localWeightTeam.y - localWeightEnemy.y;
        if(x < 0) { // we are on the left side
            x = Math.max(0, location.x - maxRadiusAway);
        } else {
            x = location.x + maxRadiusAway;
        }

        if(y < 0) { // we are on the bottom
            y = Math.max(0, location.y - maxRadiusAway);
        } else {
            y = localWeightTeam.y + maxRadiusAway;
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
            if(RobotType.HEADQUARTERS == robot.getType()) {
                continue;
            }
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
