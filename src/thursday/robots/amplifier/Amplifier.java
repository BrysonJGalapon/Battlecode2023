package thursday.robots.amplifier;

import battlecode.common.*;
import java.util.*;
import thursday.utils.*;
import thursday.pathing.*;
import thursday.communication.*;
import static thursday.RobotPlayer.OPPONENT;
import static thursday.RobotPlayer.MY_TEAM;

public class Amplifier {
  private static AmplifierState state = AmplifierState.SCOUT;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case SCOUT:   runScout(rc);    break;
      default:      throw new RuntimeException("should not be here");
    }
  }

  private static void runScout(RobotController rc) throws GameActionException {
    MapLocation myLocation = rc.getLocation();
    RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.AMPLIFIER.visionRadiusSquared, OPPONENT);
  }
}
