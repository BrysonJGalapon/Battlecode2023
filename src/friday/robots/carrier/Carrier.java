package friday.robots.carrier;

import battlecode.common.*;
import java.util.*;
import friday.utils.*;
import friday.pathing.*;
import friday.communication.*;
import static friday.RobotPlayer.OPPONENT;

public class Carrier {
  private static CarrierState state = CarrierState.COLLECT_RESOURCE;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();
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
  //  fields representing known locations of certain well-types, mapping to a boolean
  //  describing if the well was discovered via communication or not.
  //  knownManaWells and knownAdmantiniumWells may be
  //  inaccurate over time, since Mn and Ad wells can be converted to Elixir wells.
  private static Map<MapLocation, Boolean> knownManaWells = new HashMap<>();
  private static Map<MapLocation, Boolean> knownAdmantiniumWells = new HashMap<>();
  private static Map<MapLocation, Boolean> knownElixirWells = new HashMap<>();
  // uncommunicatedWellInfoMessages contains the set of well info messages we were unable to
  //  communicate, but would like to communicate.
  private static Set<Message> uncommunicatedWellInfoMessages = new HashSet<>();
  // uncommunicatedWellInfoMessages contains the set of well sky-island messages we were unable to
  //  communicate, but would like to communicate.
  private static Set<Message> uncommunicatedSkyIslandMessages = new HashSet<>();

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case TAKE_ANCHOR:       runTakeAnchor(rc);      break;
      case PLACE_ANCHOR:      runPlaceAnchor(rc);     break;
      case COLLECT_RESOURCE:  runCollectResource(rc); break;
      case DEPOSIT_RESOURCE:  runDepositResource(rc); break;
      default:                throw new RuntimeException("should not be here");
    }

    // At the end of our turn, try to communicate all of the messages we were not
    //  able to send before. This is important because the local information we know
    //  about could be valuable to the rest of the robots.
    tryCommunicateAllUncommunicatedMessages(rc);
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
      Map<MapLocation, Boolean> knownWells = getKnownWellsFor(resourceType);
      for (MapLocation loc: knownWells.keySet()) {
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
          //  value is false because we discovered this well via sight, not by communication.
          getKnownWellsFor(wellInfo.getResourceType()).put(wellInfo.getMapLocation(), false);

          // If we see a resource well, try to communicate the well info even if
          //  we don't collect from it, since other robots might want to collect from
          //  it.
          Message wellInfoMessage = Message.builder(getMessageTypeOf(wellInfo.getResourceType()))
            .recipient(Entity.CARRIERS)
            .loc(wellInfo.getMapLocation())
            .build();

          // If we couldn't communicate the message (possibly due to not being in range of HQ, or amplifier, or sky-island)
          //  add it to a cached set of uncommunicated messages for retry later on.
          boolean success = communicateWellInfoMessage(wellInfoMessage, rc);
          if (!success) {
            uncommunicatedWellInfoMessages.add(wellInfoMessage);
          }
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
          //  value is true because we discovered this well via communication.
          knownWells.put(message.loc, true);
        }
      }

      // No wells of our resourceType identified. Search for the given resource type.
      if (dst == null) {
        Optional<Direction> dir = explorePathFinder.findPath(myLocation, null, rc);
        if (dir.isPresent() && rc.canMove(dir.get())) {
          rc.move(dir.get());
        }
        return;
      }
    }

    // If we're collecting from our well and we see an enemy within range, attack it
    if (myLocation.distanceSquaredTo(dst) <= 2 && rc.getResourceAmount(resourceType) >= 20) {
      RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.CARRIER.actionRadiusSquared, OPPONENT);
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
      if (enemyWithLowestHealth != null && rc.canAttack(enemyWithLowestHealth.location)) {
        rc.attack(enemyWithLowestHealth.location);
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
  }

  private static void runPlaceAnchor(RobotController rc) throws GameActionException {
    MapLocation myLocation = rc.getLocation();

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

    // if no sky islands in view, explore for other islands and end turn
    if (dst == null) {
      Optional<Direction> dir = explorePathFinder.findPath(myLocation, null, rc);
      if (dir.isPresent() && rc.canMove(dir.get())) {
        rc.move(dir.get());
      }

      return;
    }

    rc.setIndicatorString("trying to place anchor at " + dst);
    // if we can place an anchor, place it and go to COLLECT_RESOURCES state
    if (rc.canPlaceAnchor()) {
      rc.placeAnchor();
      state = CarrierState.COLLECT_RESOURCE;
      // Reset resourceType and dst to collect different resourceTypes based
      //  on the next trip.
      resourceType = null;
      dst = null;
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

  private static void tryCommunicateAllUncommunicatedMessages(RobotController rc) throws GameActionException {
    // Try to communicate well info and sky-island messages
    tryCommunicateAllUncommunicatedWellInfoMessages(rc);
    tryCommunicateAllUncommunicatedSkyIslandMessages(rc);
  }

  private static void tryCommunicateAllUncommunicatedSkyIslandMessages(RobotController rc) throws GameActionException {
    // TODO
  }

  private static void tryCommunicateAllUncommunicatedWellInfoMessages(RobotController rc) throws GameActionException {
    // If we can't communicate at all, do nothing.
    if (!rc.canWriteSharedArray(0, 0)) {
      return;
    }

    // If we don't have any uncommunicated messages, do nothing.
    if (uncommunicatedWellInfoMessages.size() == 0) {
      return;
    }

    // Try to send all of our messages
    Set<Message> sentMessages = new HashSet<>();
    for (Message message : uncommunicatedWellInfoMessages) {
      boolean success = communicateWellInfoMessage(message, rc);
      if (success) {
        sentMessages.add(message);
      }
    }

    // Remove sent messages from the uncommunicated set
    for (Message message : sentMessages) {
      uncommunicatedWellInfoMessages.remove(message);
    }
  }

  private static boolean communicateWellInfoMessage(Message wellInfoMessage, RobotController rc) throws GameActionException {
    // If a well of this type was already communicated in close proximity to the given well, do not communicate
    //  this well.
    Map<MapLocation, Boolean> knownWells = getKnownWellsFor(getResourceTypeOf(wellInfoMessage.messageType));
    for (Map.Entry<MapLocation, Boolean> entry : knownWells.entrySet()) {
      MapLocation loc = entry.getKey();
      boolean isCommunicated = entry.getValue();
      if (isCommunicated && wellInfoMessage.loc.distanceSquaredTo(loc) <= RobotType.CARRIER.actionRadiusSquared) {
        // softly return true, to prevent re-communication of this message
        return true;
      }
    }

    // No well was already communicated that's similar to this well. Try to communicate it.
    return communicator.sendMessage(wellInfoMessage, rc);
  }

  private static MessageType getMessageTypeOf(ResourceType resourceType)  {
    switch(resourceType) {
      case ADAMANTIUM:  return MessageType.AD_WELL_LOC;
      case MANA:        return MessageType.MN_WELL_LOC;
      case ELIXIR:      return MessageType.EX_WELL_LOC;
      default:          throw new RuntimeException("should not be here");
    }
  }

  private static ResourceType getResourceTypeOf(MessageType messageType)  {
    switch(messageType) {
      case AD_WELL_LOC:  return ResourceType.ADAMANTIUM;
      case MN_WELL_LOC:  return ResourceType.MANA;
      case EX_WELL_LOC:  return ResourceType.ELIXIR;
      default:           throw new RuntimeException("should not be here");
    }
  }

  private static Map<MapLocation, Boolean> getKnownWellsFor(ResourceType resourceType) {
    switch(resourceType) {
      case ADAMANTIUM:  return knownAdmantiniumWells;
      case MANA:        return knownManaWells;
      case ELIXIR:      return knownElixirWells;
      default:          throw new RuntimeException("Should not be here");
    }
  }
}
