package aloha.robots.amplifier;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;
import aloha.pathing.*;
import aloha.communication.*;
import static aloha.RobotPlayer.OPPONENT;
import static aloha.RobotPlayer.MY_TEAM;

public class Amplifier {
  private static AmplifierState state = AmplifierState.SCOUT;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case SCOUT:   runScout(rc);    break;
      default:      throw new RuntimeException("should not be here");
    }
  }

  private static void runScout(RobotController rc) throws GameActionException {
    // TODO
  }
}
