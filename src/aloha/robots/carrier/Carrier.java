package aloha.robots.carrier;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;
import aloha.pathing.*;
import aloha.communication.*;
import static aloha.RobotPlayer.OPPONENT;

public class Carrier {
  private static CarrierState state = CarrierState.COLLECT_RESOURCE;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final PathFinder randomPathFinder = new RandomPathFinder();
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();
  private static final Random rng = new Random(123298);

  // hqLoc is a cached data field of the HQ this robot belongs to.
  private static MapLocation hqLoc;
  // dst is a cached data field representing a destination location. The meaning
  //  of this field depends on the state this robot is in.
  private static MapLocation dst;
  // resourceType is a cached data field representing the resource type that
  //  this robot is collecting or depositing.
  private static ResourceType resourceType;
  // knownManaWells, knownAdmantiniumWells, and knownElixirWells are cached
  //  fields representing known locations of certain well-types. knownManaWells and knownAdmantiniumWells may be
  //  inaccurate over time, since Mn and Ad wells can be converted to Elixir wells.
  private static Set<MapLocation> knownManaWells = new HashSet<>();
  private static Set<MapLocation> knownAdmantiniumWells = new HashSet<>();
  private static Set<MapLocation> knownElixirWells = new HashSet<>();

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case TAKE_ANCHOR:       runTakeAnchor(rc);      break;
      case PLACE_ANCHOR:      runPlaceAnchor(rc);     break;
      case ATTACK_LOC:        runAttackLoc(rc);       break;
      case COLLECT_RESOURCE:  runCollectResource(rc); break;
      case DEPOSIT_RESOURCE:  runDepositResource(rc); break;
      case SURVIVE:           runSurvive(rc);         break;
      default:                throw new RuntimeException("should not be here");
    }
  }

  private static void runCollectResource(RobotController rc) throws GameActionException {
    rc.setIndicatorString("collecting resources");
    MapLocation myLocation = rc.getLocation();

    // Identify an HQ.
    if (hqLoc == null) {
      hqLoc = getHQLoc(rc);
    }

    // Do a coin-flip to determine which resource type to collect
    if (resourceType == null) {
      if (rng.nextBoolean()) {
        resourceType = ResourceType.ADAMANTIUM;
      } else {
        resourceType = ResourceType.MANA;
      }
    }

    rc.setIndicatorString("collecting resources: " + resourceType);

    // If we don't already have a resource location to path to, try to identify one
    if (dst == null) {
      // If we've cached any Mana or Ad wells, path to the closest one
      Set<MapLocation> knownWells = getKnownWellsFor(resourceType);
      for ( MapLocation loc : knownWells) {
        if (dst == null || myLocation.distanceSquaredTo(loc) < myLocation.distanceSquaredTo(dst)) {
          dst = loc;
        }
      }

      // No cached resource locations. If we see any wells of our resourceType in sight, path to the closest one
      if (dst == null) {
        WellInfo[] wellInfos = rc.senseNearbyWells();
        for (WellInfo wellInfo : wellInfos) {
          if ((wellInfo.getResourceType() == resourceType) && (dst == null || myLocation.distanceSquaredTo(wellInfo.getMapLocation()) < myLocation.distanceSquaredTo(dst))) {
            dst = wellInfo.getMapLocation();
          }

          // Cache seen well location, for faster lookup next time
          getKnownWellsFor(wellInfo.getResourceType()).add(wellInfo.getMapLocation());

          // If we see a resource well, try to communicate the well info even if
          //  we don't collect from it, since other robots might want to collect from
          //  it.
          boolean success = communicateWellInfo(wellInfo, rc);
          // TODO cache unsuccessful communications for retry later
        }
      }

      // No wells of our resourceType in sight. If we received some well messages for our resourceType, path to the closest one
      if (dst == null) {
        List<Message> messages = communicator.receiveMessages(getMessageTypeOf(resourceType), rc);
        for (Message message : messages) {
          if (dst == null || myLocation.distanceSquaredTo(message.loc) < myLocation.distanceSquaredTo(dst)) {
            dst = message.loc;
          }

          // Cache heard well location, for faster lookup next time
          knownWells.add(message.loc);
        }
      }

      // No wells of our resourceType identified. Search for the given resource type.
      if (dst == null) {
        Optional<Direction> dir = randomPathFinder.findPath(null, null, rc);
        if (dir.isPresent() && rc.canMove(dir.get())) {
          rc.move(dir.get());
        }
        return;
      }
    }

    // If we've collected enough of our resource, go to DEPOSIT_RESOURCE state.
    if (rc.getResourceAmount(resourceType) >= 39) {
      state = CarrierState.DEPOSIT_RESOURCE;
      return;
    }

    // If we can collect resources from the well (try to collect the maximum amount), do it
    //  and move to the DEPOSIT_RESOURCE state.
    if (rc.canCollectResource(dst, -1)) {
      rc.collectResource(dst, -1);
      return;
    }

    // If we're not close enough to collect resources from the well, path closer to it
    Optional<Direction> dir = fuzzyPathFinder.findPath(rc.getLocation(), dst, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
      return;
    }
  }

  private static void runDepositResource(RobotController rc) throws GameActionException {
    rc.setIndicatorString("depositing resources");
    // Identify an HQ.
    if (hqLoc == null) {
      hqLoc = getHQLoc(rc);
    }

    // Try to deposit resources to the HQ
    int resourceAmount = rc.getResourceAmount(resourceType);
    if (rc.canTransferResource(hqLoc, resourceType, resourceAmount)) {
      rc.transferResource(hqLoc, resourceType, resourceAmount);

      // If the HQ has an anchor, take it
      if (rc.canSenseRobotAtLocation(hqLoc)) {
        RobotInfo hqInfo = rc.senseRobotAtLocation(hqLoc);
        if (hqInfo.getNumAnchors(Anchor.STANDARD) > 0) {
          state = CarrierState.TAKE_ANCHOR;
          return;
        }
      }

      state = CarrierState.COLLECT_RESOURCE;
      // Reset resourceType and dst to collect different resourceTypes based
      //  on the next trip.
      resourceType = null;
      dst = null;
      return;
    }

    // If we can't yet deposit the resources, move in the direction of the HQ.
    Optional<Direction> dir = fuzzyPathFinder.findPath(rc.getLocation(), hqLoc, rc);
    if (dir.isPresent() && rc.canMove(dir.get())) {
      rc.move(dir.get());
      return;
    }
  }

  private static void runTakeAnchor(RobotController rc) throws GameActionException {
    // Identify an HQ.
    if (hqLoc == null) {
      hqLoc = getHQLoc(rc);
    }

    // If we're already holding an anchor, go to PLACE_ANCHOR state
    if (rc.getAnchor() != null) {
      state = CarrierState.PLACE_ANCHOR;
      // reset state used during PLACE_ANCHOR state
      dst = null;
      return;
    }

    // If the HQ does not have an anchor anymore, go to COLLECT_RESOURCES state
    if (rc.canSenseRobotAtLocation(hqLoc)) {
      RobotInfo hqInfo = rc.senseRobotAtLocation(hqLoc);
      if (hqInfo.getNumAnchors(Anchor.STANDARD) == 0) {
        state = CarrierState.COLLECT_RESOURCE;
        // Reset resourceType and dst to collect different resourceTypes based
        //  on the next trip.
        resourceType = null;
        dst = null;
        return;
      }
    }

    // Try to take an anchor from the HQ, and move to PLACE_ANCHOR state
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
      // Do not attack HQs, since they cannot be destroyed.
      if (enemy.getType() == RobotType.HEADQUARTERS) {
        continue;
      }

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

  // getHQLoc gets the HQ location to associate to this robot.
  private static MapLocation getHQLoc(RobotController rc) throws GameActionException {
    // Try to find HQs within the current vision
    RobotInfo[] robotInfos = rc.senseNearbyRobots();
    for (RobotInfo robotInfo : robotInfos) {
      if (robotInfo.type == RobotType.HEADQUARTERS) {
        return robotInfo.location;
      }
    }

    // No HQs within vision. Get HQ locations from messages.
    List<Message> messages = communicator.receiveMessages(MessageType.HQ_STATE, rc);
    return messages.get(0).loc;
  }

  private static void runSurvive(RobotController rc) throws GameActionException {
    // TODO
  }

  private static boolean communicateWellInfo(WellInfo wellInfo, RobotController rc) throws GameActionException {
    // TODO
    return false;
  }

  private static MessageType getMessageTypeOf(ResourceType resourceType)  {
    switch(resourceType) {
      case ADAMANTIUM:  return MessageType.AD_WELL_LOC;
      case MANA:        return MessageType.MN_WELL_LOC;
      case ELIXIR:      return MessageType.EX_WELL_LOC;
      default:          throw new RuntimeException("should not be here");
    }
  }

  private static Set<MapLocation> getKnownWellsFor(ResourceType resourceType) {
    Set<MapLocation> knownWells;
    switch(resourceType) {
      case ADAMANTIUM: knownWells = knownAdmantiniumWells; break;
      case MANA:       knownWells = knownManaWells;        break;
      case ELIXIR:     knownWells = knownElixirWells;      break;
      default: throw new RuntimeException("Should not be here");
    }
    return knownWells;
  }
}
