package aloha.communication.basic;

import battlecode.common.*;
import aloha.communication.*;
import aloha.robots.headquarters.HeadquartersState;

public class Encoding {
  // ENTITY_ENCODING_LENGTH is the number of bits it takes to encode an entity
  public static final int ENTITY_ENCODING_LENGTH = 3;
  // ENTITY_ENCODING_MASK is a mask with the last ENTITY_ENCODING_LENGTH bits set
  public static final int ENTITY_ENCODING_MASK = 7;

  // MESSAGE_TYPE_ENCODING_LENGTH is the number of bits it takes to encode a message type
  public static final int MESSAGE_TYPE_ENCODING_LENGTH = 4;
  // MESSAGE_TYPE_ENCODING_MASK is a mask with the last MESSAGE_TYPE_ENCODING_LENGTH bits set
  public static final int MESSAGE_TYPE_ENCODING_MASK = 31;

  // MAPLOCATION_ENCODING_LENGTH is the number of bits it takes to encode a maplocation
  public static final int MAPLOCATION_ENCODING_LENGTH = 12;
  // MAPLOCATION_ENCODING_MASK is a mask with the last MAPLOCATION_ENCODING_LENGTH bits set
  public static final int MAPLOCATION_ENCODING_MASK = 8191;

  // HQ_STATE_ENCODING_LENGTH is the number of bits it takes to encode a headquarters state
  public static final int HQ_STATE_ENCODING_LENGTH = 4;
  // HQ_STATE_ENCODING_MASK is a mask with the last HQ_STATE_ENCODING_LENGTH bits set
  public static final int HQ_STATE_ENCODING_MASK = 31;

  // COORDINATE_ENCODING_LENGTH is the number of bits it takes to encode an x or y coordinate, which both range from [0, 60).
  public static final int COORDINATE_ENCODING_LENGTH = 6;
  // COORDINATE_ENCODING_MASK is a mask with the last COORDINATE_ENCODING_LENGTH bits set
  public static final int COORDINATE_ENCODING_MASK = 127;

  public static int of(Entity entity) {
    switch(entity) {
    case ALL_ROBOTS:          return 1;
    case HEADQUARTERS:        return 2;
    case CARRIERS:            return 3;
    case LAUNCHERS:           return 4;
    case AMPLIFIERS:          return 5;
    case BOOSTERS:            return 6;
    case DESTABILIZERS:       return 7;
    default: throw new RuntimeException("should not be here");
    }
  }

  public static int of(MessageType messageType) {
    switch(messageType) {
    case HQ_STATE:            return 1;
    case ENEMY_LOC:           return 2;
    case NO_ENEMY_LOC:        return 3;
    case AD_WELL_LOC:         return 4;
    case MN_WELL_LOC:         return 5;
    case EX_WELL_LOC:         return 6;
    default: throw new RuntimeException("should not be here");
    }
  }

  public static int of(MapLocation loc) {
    // Build the encoding, in the format
    //    <x coordinate bits> | <y coordinate bits>
    int encoding = 0;
    encoding = (encoding << COORDINATE_ENCODING_LENGTH) | loc.x;
    encoding = (encoding << COORDINATE_ENCODING_LENGTH) | loc.y;
    return encoding;
  }

  public static int of(HeadquartersState hqState) {
    switch(hqState) {
    case DEAD:                return 1;
    case BUILD_ANCHOR:        return 2;
    case BUILD_CARRIER:       return 3;
    default: throw new RuntimeException("should not be here");
    }
  }

  public static int ofHQStateMessage(Message message) {
    // Build the encoding, in the format
    //    <location bits> | <hq state bits>
    int encoding = 0;
    encoding = (encoding << Encoding.MAPLOCATION_ENCODING_LENGTH) | Encoding.of(message.loc);
    encoding = (encoding << Encoding.HQ_STATE_ENCODING_LENGTH) | Encoding.of(message.hqState);
    return encoding;
  }

  // ofLocationMessage encodes messages that only have a message type and location component
  public static int ofLocationMessage(Message message) {
    // Build the encoding, in the format
    //    <location bits> | <message type bits>
    int encoding = 0;
    encoding = (encoding << Encoding.MAPLOCATION_ENCODING_LENGTH) | Encoding.of(message.loc);
    encoding = (encoding << Encoding.MESSAGE_TYPE_ENCODING_LENGTH) | Encoding.of(message.messageType);
    return encoding;
  }
}
