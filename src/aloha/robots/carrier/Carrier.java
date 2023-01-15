package aloha.robots.carrier;

import battlecode.common.*;
import java.util.*;
import aloha.utils.*;
import aloha.pathing.*;
import aloha.communication.*;
import static aloha.RobotPlayer.OPPONENT;
import static aloha.RobotPlayer.MY_TEAM;

public class Carrier {
  private static CarrierState state = CarrierState.COLLECT_RESOURCE;
  private static final Communicator communicator = Communicator.newCommunicator();
  private static final PathFinder explorePathFinder = new ExplorePathFinder();
  private static final PathFinder fuzzyPathFinder = new FuzzyPathFinder();
  private static final Random rng = Utils.getRng();

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
  private static final Map<MapLocation, Boolean> knownManaWells = new HashMap<>();
  private static final Map<MapLocation, Boolean> knownAdmantiniumWells = new HashMap<>();
  private static final Map<MapLocation, Boolean> knownElixirWells = new HashMap<>();
  // knownFriendlyIslands, knownNeutralIslands, and knownEnemyIslands are
  //  cached fields representing known locations and indices of sky-islands, mapping to
  //  a boolean describing if the sky-island was discovered via communication or not.
  //  knownFriendlyIslands, knownNeutralIslands, and knownEnemyIslands may be
  //  inaccurate over time, since islands can be taken over.
  private static final Map<MapLocation, Boolean> knownFriendlyIslands = new HashMap<>();
  private static final Map<MapLocation, Boolean> knownNeutralIslands = new HashMap<>();
  private static final Map<MapLocation, Boolean> knownEnemyIslands = new HashMap<>();
  // uncommunicatedWellInfoMessages contains the set of well info messages we were unable to
  //  communicate, but would like to communicate.
  private static Set<Message> uncommunicatedWellInfoMessages = new HashSet<>();
  // uncommunicatedWellInfoMessages contains the set of well sky-island messages we were unable to
  //  communicate, but would like to communicate.
  private static Set<Message> uncommunicatedSkyIslandMessages = new HashSet<>();

  // SKY_ISLAND_REGION_RADIUS represents the radius of a sky island region. We define regions
  //  to avoid exploding the shared array and internal caches with sky-island locations.
  private static int SKY_ISLAND_REGION_RADIUS = 72;

  public static void run(RobotController rc) throws GameActionException {
    switch(state) {
      case TAKE_ANCHOR:       runTakeAnchor(rc);      senseLocalSkyIslands(null, rc); break;
      case PLACE_ANCHOR:      runPlaceAnchor(rc);     break;
      case COLLECT_RESOURCE:  runCollectResource(rc); senseLocalSkyIslands(null, rc); break;
      case DEPOSIT_RESOURCE:  runDepositResource(rc); senseLocalSkyIslands(null, rc); break;
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
      // If we've cached any Mn, Ad, or Ex wells, path to the closest one
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

         Map<MapLocation,Boolean> otherKnownWells = getKnownWellsFor(wellInfo.getResourceType());

          // Cache seen well location, for faster lookup next time
          //  value is false because we discovered this well via sight, not by communication.
          if (!otherKnownWells.containsKey(wellInfo.getMapLocation())) {
            otherKnownWells.put(wellInfo.getMapLocation(), false);
          }

          // If we see a resource well, try to communicate the well info even if
          //  we don't collect from it, since other robots might want to collect from
          //  it.
          Message wellInfoMessage = Message.builder(getMessageTypeOf(wellInfo.getResourceType()))
            .recipient(Entity.CARRIERS)
            .loc(wellInfo.getMapLocation())
            .build();

          // If we've already communicated this message in a previous turn, do not communicate it again
          boolean alreadyCommunicated = otherKnownWells.getOrDefault(wellInfo.getMapLocation(), false);
          if (!alreadyCommunicated) {
            // If we couldn't communicate the message (possibly due to not being in range of HQ, or amplifier, or sky-island)
            //  add it to a cached set of uncommunicated messages for retry later on.
            boolean success = communicateWellInfoMessage(wellInfoMessage, rc);
            if (!success) {
              uncommunicatedWellInfoMessages.add(wellInfoMessage);
            }
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

    // TODO add YOLO code, where if we're sufficiently far away from the HQ and
    //  our health is <= 3, we throw our resources at an enemy.

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

    // If we have a dst but it's no longer neutral, reset dst and remove the neutral island from the cache.
    //  Also, try to send a message out for that destination so that other carriers don't path to this dst.
    if (dst != null) {
      // If we can see the dst and its no longer neutral, reset
      if (rc.canSenseLocation(dst)) {
        int islandIdx = rc.senseIsland(dst);
        Team islandOwner = rc.senseTeamOccupyingIsland(islandIdx);
        if (islandOwner != Team.NEUTRAL) {
          // Update our local caches
          knownNeutralIslands.remove(dst);
          addKnownIslands(islandOwner, dst, false);
          communicateKnownIsland(islandOwner, dst, rc);
          dst = null;
        }
      }

      // If we receive a message that the dst belongs to an enemy, reset it.
      if (dst != null) {
        List<Message> messages = communicator.receiveMessages(MessageType.ENEMY_ISLAND_LOC, rc);
        for (Message message : messages) {
          if (message.loc.equals(dst)) {
            // Update our local caches
            knownEnemyIslands.remove(dst);
            addKnownIslandsAndCommunicate(OPPONENT, dst, rc, true);
            dst = null;
          }
        }
      }

      // If we receive a message that the dst already belongs to us, reset it.
      if (dst != null) {
        List<Message> messages = communicator.receiveMessages(MessageType.FRIENDLY_ISLAND_LOC, rc);
        for (Message message : messages) {
          if (message.loc.equals(dst)) {
            // Update our local caches
            knownFriendlyIslands.remove(dst);
            addKnownIslandsAndCommunicate(MY_TEAM, dst, rc, true);
            dst = null;
          }
        }
      }
    }

    // If we don't already have a neutral sky-island to path to, try to identify one
    if (dst == null) {
      // If we've cached any sky-island locations, path to the closest one
      for (MapLocation loc: knownNeutralIslands.keySet()) {
        if (dst == null || myLocation.distanceSquaredTo(loc) < myLocation.distanceSquaredTo(dst)) {
          dst = loc;
        }
      }

      // No cached sky-islands to path to. If we see any neutral islands in sight, path to the closest one
      if (dst == null) {
        dst = senseLocalSkyIslands(Team.NEUTRAL, rc);
      }

      // No sky-islands in sight. If we received some messages for neutral sky islands, path to the closest one.
      if (dst == null) {
        List<Message> messages = communicator.receiveMessages(MessageType.NEUTRAL_ISLAND_LOC, rc);
        for (Message message : messages) {
          if (dst == null || myLocation.distanceSquaredTo(message.loc) < myLocation.distanceSquaredTo(dst)) {
            dst = message.loc;
          }

          // Cache seen sky-island location, for faster lookup next time
          //  value is true because we discovered this sky-island via communication.
          //  to avoid blowing up the cached map, verify that no other island locations are close to the loc.
          boolean existingIsland = false;
          for (MapLocation knownLoc : knownNeutralIslands.keySet()) {
            if (message.loc.distanceSquaredTo(knownLoc) <= SKY_ISLAND_REGION_RADIUS) {
              existingIsland = true;
              break;
            }
          }
          if (!existingIsland) {
            knownNeutralIslands.put(message.loc, true);
          }
        }
      }

      // No sky-islands identified. Explore for islands.
      // TODO hang around the HQ, to avoid dying while having a valuable anchor.
      if (dst == null) {
        Optional<Direction> dir = explorePathFinder.findPath(myLocation, null, rc);
        if (dir.isPresent() && rc.canMove(dir.get())) {
          rc.move(dir.get());
        }

        return;
      }
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

  // senseLocalSkyIslands senses the sky islands around this robot, and returns
  //  the closest sky island location owned by the given team, if any.
  private static MapLocation senseLocalSkyIslands(Team team, RobotController rc) throws GameActionException {
    MapLocation myLocation = rc.getLocation();
    MapLocation ret = null;

    int[] nearbyIslands = rc.senseNearbyIslands();
    for (int nearbyIsland : nearbyIslands) {
      MapLocation loc = rc.senseNearbyIslandLocations(nearbyIsland)[0];
      Team islandOwner = rc.senseTeamOccupyingIsland(nearbyIsland);

      // If the island is owned by the given team, update the returned MapLocation.
      if (islandOwner == team && (ret == null || myLocation.distanceSquaredTo(loc) < myLocation.distanceSquaredTo(ret))) {
        ret = loc;
      }

      addKnownIslandsAndCommunicate(islandOwner, loc, rc, false);
    }

    return ret;
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
    // If we can't communicate at all, do nothing.
    if (!rc.canWriteSharedArray(0, 0)) {
      return;
    }

    // If we don't have any uncommunicated messages, do nothing.
    if (uncommunicatedSkyIslandMessages.size() == 0) {
      return;
    }

    // Try to send all of our messages
    Set<Message> sentMessages = new HashSet<>();
    for (Message message : uncommunicatedSkyIslandMessages) {
      // If we're about to run out of bytecode, stop communicating messages
      if (Clock.getBytecodesLeft() <= 1000) {
        break;
      }

      boolean success = communicateSkyIslandMessage(message, rc);
      if (success) {
        sentMessages.add(message);
      }
    }

    // Remove sent messages from the uncommunicated set
    for (Message message : sentMessages) {
      uncommunicatedSkyIslandMessages.remove(message);
    }
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
      // If we're about to run out of bytecode, stop communicating messages
      if (Clock.getBytecodesLeft() <= 1000) {
        break;
      }

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

  private static boolean communicateSkyIslandMessage(Message skyIslandMessage, RobotController rc) throws GameActionException {
    // If a sky-island of this type was already communicated in close proximity to the given island, do not communicate this island.
    Map<MapLocation, Boolean> knownIslands = getKnownIslandsFor(getTeamOf(skyIslandMessage.messageType));
    for (Map.Entry<MapLocation, Boolean> entry : knownIslands.entrySet()) {
      MapLocation loc = entry.getKey();
      boolean isCommunicated = entry.getValue();
      if (isCommunicated && skyIslandMessage.loc.distanceSquaredTo(loc) <= SKY_ISLAND_REGION_RADIUS) {
        // softly return true, to prevent re-communication of this message
        return true;
      }
    }

    // No sky-island was already communicated that's similar to this sky-island. Try to communicate it.
    return communicator.sendMessage(skyIslandMessage, rc);
  }

  private static boolean communicateWellInfoMessage(Message wellInfoMessage, RobotController rc) throws GameActionException {
    // If a well of this type was already communicated in close proximity to the given well, do not communicate
    //  this well.
    Map<MapLocation, Boolean> knownWells = getKnownWellsFor(getResourceTypeOf(wellInfoMessage.messageType));
    for (Map.Entry<MapLocation, Boolean> entry : knownWells.entrySet()) {
      MapLocation loc = entry.getKey();
      boolean isCommunicated = entry.getValue();
      if (isCommunicated && wellInfoMessage.loc.distanceSquaredTo(loc) <= RobotType.CARRIER.visionRadiusSquared) {
        // softly return true, to prevent re-communication of this message
        return true;
      }
    }

    // No well was already communicated that's similar to this well. Try to communicate it.
    boolean success = communicator.sendMessage(wellInfoMessage, rc);
    if (success) {
      // Cache the location as communicated
      knownWells.put(wellInfoMessage.loc, true);
    }
    return success;
  }

  private static MessageType getMessageTypeOf(ResourceType resourceType)  {
    switch(resourceType) {
      case ADAMANTIUM:  return MessageType.AD_WELL_LOC;
      case MANA:        return MessageType.MN_WELL_LOC;
      case ELIXIR:      return MessageType.EX_WELL_LOC;
      default:          throw new RuntimeException("should not be here");
    }
  }

  private static MessageType getMessageTypeOf(Team team)  {
    if (team.equals(Team.NEUTRAL)) {
      return MessageType.NEUTRAL_ISLAND_LOC;
    } else if (team.equals(OPPONENT)) {
      return MessageType.ENEMY_ISLAND_LOC;
    } else if (team.equals(MY_TEAM)) {
      return MessageType.FRIENDLY_ISLAND_LOC;
    } else {
      throw new RuntimeException("Should not be here");
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

  private static Team getTeamOf(MessageType messageType)  {
    switch(messageType) {
      case NEUTRAL_ISLAND_LOC:  return Team.NEUTRAL;
      case ENEMY_ISLAND_LOC:    return OPPONENT;
      case FRIENDLY_ISLAND_LOC: return MY_TEAM;
      default:                  throw new RuntimeException("should not be here");
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

  private static Map<MapLocation, Boolean> getKnownIslandsFor(Team team) {
    if (team.equals(Team.NEUTRAL)) {
      return knownNeutralIslands;
    } else if (team.equals(OPPONENT)) {
      return knownEnemyIslands;
    } else if (team.equals(MY_TEAM)) {
      return knownFriendlyIslands;
    } else {
      throw new RuntimeException("Should not be here");
    }
  }

  private static boolean addKnownIslands(Team islandOwner, MapLocation loc, boolean isCommunicated) throws GameActionException {
    // Cache island locations.
    //  value is false because we discovered this sky-island via sight, not communication.
    //  to avoid blowing up the cached map, verify that no other island locations are close to the loc.
    Map<MapLocation, Boolean> knownIslands = getKnownIslandsFor(islandOwner);
    boolean existingIsland = false;
    for (MapLocation knownLoc : knownIslands.keySet()) {
      if (loc.distanceSquaredTo(knownLoc) <= SKY_ISLAND_REGION_RADIUS) {
        existingIsland = true;
        break;
      }
    }

    if (!existingIsland) {
      knownIslands.put(loc, isCommunicated);
    }

    return existingIsland;
  }

  private static void addKnownIslandsAndCommunicate(Team islandOwner, MapLocation loc, RobotController rc, boolean isCommunicated) throws GameActionException {
    boolean existingIsland = addKnownIslands(islandOwner, loc, isCommunicated);

    if (!existingIsland) {
      communicateKnownIsland(islandOwner, loc, rc);
    }
  }

  private static void communicateKnownIsland(Team islandOwner, MapLocation loc, RobotController rc) throws GameActionException {
    // TODO communicate sky island to amplifiers and launchers too. May need to read their messages too, to avoid blowup?
    // Try to communicate the neutral island location
    Message skyIslandMessage = Message.builder(getMessageTypeOf(islandOwner))
      .recipient(Entity.CARRIERS)
      .loc(loc)
      .build();
    // If we couldn't communicate the message (possibly due to not being in range of HQ, or amplifier, or sky-island)
    //  add it to a cached set of uncommunicated messages for retry later on.
    boolean success = communicateSkyIslandMessage(skyIslandMessage, rc);
    if (!success) {
      uncommunicatedSkyIslandMessages.add(skyIslandMessage);
    }
  }
}
