package aloha.communication.basic;

import battlecode.common.*;
import java.util.*;
import aloha.communication.*;

import aloha.robots.headquarters.HeadquartersState;

public class BasicCommunicator implements Communicator {
  /***
  There are 64 indices in the shared array.

  Indices 0-3 are for headquarter states.
  Indices 4-13 are for messages intended for headquarter robots.
  Indices 14-23 are for messages intended for carrier robots.
  Indices 24-33 are for messages intended for launcher robots.
  Indices 34-43 are for messages intended for booster robots.
  Indices 44-53 are for messages intended for destabilizer robots.
  Indices 54-63 are for messages intended for amplifier robots.

  The first index in each of the robot-specific ranges (e.g. 4, 14, 24, etc.) holds
    a counter to the total number of writes made to this range ever made. If the
    counter overlows, it returns to 0.
  ***/

  // hqIndex is a cached index into the shared array of the headquarters this robot belongs to.
  private static int hqIndex = -1;

  // numReceivedMessages is a cached number of messages this robot received from the shared array.
  private static int numReceivedMessages = 0;

  @Override
  public boolean sendMessage(Message message, RobotController rc) throws GameActionException {
    switch(message.messageType) {
      case HQ_STATE:    return sendHQStateMessage(message, rc);
      case MN_WELL_LOC: return sendLocationMessage(message, rc);
      case EX_WELL_LOC: return sendLocationMessage(message, rc);
      case ENEMY_LOC:   return sendLocationMessage(message, rc);
      default:          throw new RuntimeException("should not be here");
    }
  }

  @Override
  public List<Message> receiveMessages(MessageType messageType, RobotController rc) throws GameActionException {
    switch(messageType) {
      case HQ_STATE:    return receiveHQStateMessages(rc);
      case AD_WELL_LOC: return receiveLocationMessages(messageType, rc);
      case MN_WELL_LOC: return receiveLocationMessages(messageType, rc);
      case EX_WELL_LOC: return receiveLocationMessages(messageType, rc);
      case ENEMY_LOC:   return receiveLocationMessages(messageType, rc);
      default:          throw new RuntimeException("should not be here");
    }
  }

  // sendLocationMessage sends messages that only contain a messageType and location
  private boolean sendLocationMessage(Message message, RobotController rc) throws GameActionException {
    // Get the total number of writes ever made to the given recipient by getting the
    //  value at the first index of the recipient's range
    int firstIndex = getFirstIndexOfRange(message.recipient);
    int numWrites = rc.readSharedArray(firstIndex);

    // Use the total number of writes to point to one of the 9 available indices
    int targetIdx = firstIndex + (numWrites % 9) + 1;

    // If we can't write to the shared array, return false
    int encoding = Encoding.ofLocationMessage(message);
    if (!rc.canWriteSharedArray(targetIdx, encoding)) {
      return false;
    }
    if(!rc.canWriteSharedArray(firstIndex, numWrites+1)) {
      return false;
    }

    rc.writeSharedArray(targetIdx, encoding);
    rc.writeSharedArray(firstIndex, numWrites+1);

    return true;
  }

  // receiveLocationMessages receives messages that only contain a messageType and location
  private List<Message> receiveLocationMessages(MessageType messageType, RobotController rc) throws GameActionException {
    // Get the total number of writes ever made to the given recipient by getting the
    //  value at the first index of the recipient's range
    int firstIndex = getFirstIndexOfRange(Entity.of(rc.getType()));
    int numWrites = rc.readSharedArray(firstIndex);

    List<Message> messages = new LinkedList<>();

    // Receive all messages written since the latest message was received. Cap the number of
    //  received messages to 9, since there will be at most 9 new messages to receive. Read the
    //  messages in reverse chronological order, but build the message list in chronological order.
    for (int count = numWrites-1; count >= numReceivedMessages && count >= numWrites-9; count--) {
      int targetIdx = firstIndex + (count % 9) + 1;
      int encoding = rc.readSharedArray(targetIdx);

      // Ignore empty messages
      if (encoding == 0) {
        continue;
      }

      Message message = Decoding.locationMessage(encoding);
      messages.add(0, message);
    }

    // It's possible we haven't read as many received messages as was ever written, but
    //  that only happens when more than 9 messages were written to the range in the last turn.
    //  Those messages are effectively lost, so we count them here anyway to ensure that
    //  subsequent invocations of this function will be correct.
    numReceivedMessages = numWrites;

    return messages;
  }

  // sendHQStateMessage writes the given headquarter's state and location to the shared array
  private boolean sendHQStateMessage(Message message, RobotController rc) throws GameActionException {
    // If we're not an HQ, do nothing for now, since non-HQ reporting of HQ state is not yet supported.
    if (rc.getType() != RobotType.HEADQUARTERS) {
      return false;
    }

    // If we're an HQ but not yet set our cached index, set it by finding the first empty slot
    // in the shared array. This assumes that the shared array is initialized to all 0's at the start of the game.
    if (hqIndex == -1) {
      for (int i = 0; i < 4; i++) {
        if (rc.readSharedArray(i) == 0) {
          hqIndex = i;
          break;
        }
      }

      // Ensure we have set an hqIndex
      if (hqIndex == -1) {
        throw new RuntimeException("should have set an hqIndex");
      }
    }

    // If we can't write to the shared array, return false
    int encoding = Encoding.ofHQStateMessage(message);
    if (!rc.canWriteSharedArray(hqIndex, encoding)) {
      return false;
    }

    rc.writeSharedArray(hqIndex, encoding);
    return true;
  }

  private int getFirstIndexOfRange(Entity entity) {
    switch(entity) {
      case HEADQUARTERS:        return 4;
      case CARRIERS:            return 14;
      case LAUNCHERS:           return 24;
      case BOOSTERS:            return 34;
      case DESTABILIZERS:       return 44;
      case AMPLIFIERS:          return 54;
      default: throw new RuntimeException("should not be here");
    }
  }

  private List<Message> receiveHQStateMessages(RobotController rc) throws GameActionException {
    // If we don't yet belong to an HQ, find the closest one
    if (hqIndex == -1) {
      hqIndex = getClosestHQIndex(rc);

      // We should always find an HQ, since they cannot be destroyed
      if (hqIndex == -1) {
        throw new RuntimeException("should not be here");
      }
    }

    int encoding = rc.readSharedArray(hqIndex);
    Message message = Decoding.hqStateMessage(encoding);

    return Arrays.asList(message);
  }

  private int getClosestHQIndex(RobotController rc) throws GameActionException {
    MapLocation myLoc = rc.getLocation();

    int closestHQIndex = -1;
    int shortestDistance = 0;
    for (int i = 0; i < 4; i++) {
      int encoding = rc.readSharedArray(i);

      // If HQ slot is empty, ignore it
      if (encoding == 0) {
        continue;
      }

      Message message = Decoding.hqStateMessage(encoding);

      // If the distance to this HQ is shorter than any one we've seen, set the hqIndex to it
      int distance = myLoc.distanceSquaredTo(message.loc);
      if (closestHQIndex == -1 || distance < shortestDistance) {
        closestHQIndex = i;
        shortestDistance = distance;
      }
    }

    return closestHQIndex;
  }
}
