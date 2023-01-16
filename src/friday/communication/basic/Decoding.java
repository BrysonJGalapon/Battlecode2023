package friday.communication.basic;

import battlecode.common.*;
import friday.communication.*;
import friday.robots.headquarters.HeadquartersState;

// Decoding decodes the values encoded by Encoding
public class Decoding {
  public static Entity entity(int encoding) {
    switch(encoding) {
    case 1:   return Entity.ALL_ROBOTS;
    case 2:   return Entity.HEADQUARTERS;
    case 3:   return Entity.CARRIERS;
    case 4:   return Entity.LAUNCHERS;
    case 5:   return Entity.AMPLIFIERS;
    case 6:   return Entity.BOOSTERS;
    case 7:   return Entity.DESTABILIZERS;
    default: throw new RuntimeException("should not be here");
    }
  }

  public static MessageType messageType(int encoding) {
    switch(encoding) {
    case 1:   return MessageType.HQ_STATE;
    case 2:   return MessageType.ENEMY_LOC;
    case 3:   return MessageType.NO_ENEMY_LOC;
    case 4:   return MessageType.AD_WELL_LOC;
    case 5:   return MessageType.MN_WELL_LOC;
    case 6:   return MessageType.EX_WELL_LOC;
    case 7:   return MessageType.FRIENDLY_ISLAND_LOC;
    case 8:   return MessageType.ENEMY_ISLAND_LOC;
    case 9:   return MessageType.NEUTRAL_ISLAND_LOC;
    default: throw new RuntimeException("should not be here, got: " + encoding);
    }
  }

  public static MapLocation mapLocation(int encoding) {
    // Assume the encoding is in the format
    //    <x coordinate bits> | <y coordinate bits>

    // Extract the y coordinate by extracting the last COORDINATE_ENCODING_LENGTH bits
    int y = encoding & Encoding.COORDINATE_ENCODING_MASK;

    // Extract the x coordinate by extracting the next COORDINATE_ENCODING_LENGTH bits
    int x = (encoding >> Encoding.COORDINATE_ENCODING_LENGTH) & Encoding.COORDINATE_ENCODING_MASK;

    return new MapLocation(x, y);
  }

  public static HeadquartersState hqState(int encoding) {
    switch(encoding) {
    case 1:   return HeadquartersState.BUILD_ANCHOR;
    case 2:   return HeadquartersState.BUILD_CARRIER;
    case 3:   return HeadquartersState.BUILD_LAUNCHER;
    case 4:   return HeadquartersState.BUILD_AMPLIFIER;
    default: throw new RuntimeException("should not be here");
    }
  }

  public static Message hqStateMessage(int encoding) {
    // Assume the encoding is in the format
    //    <location bits> | <hq state bits>

    // Extract the hq state by extracting the first HQ_STATE_ENCODING_LENGTH bits
    int hqStateEncoding = encoding & Encoding.HQ_STATE_ENCODING_MASK;

    // Extract the loc state by extracting the next MAPLOCATION_ENCODING_LENGTH bits
    int locEncoding = (encoding >> Encoding.HQ_STATE_ENCODING_LENGTH) & Encoding.MAPLOCATION_ENCODING_MASK;

    HeadquartersState hqState = Decoding.hqState(hqStateEncoding);
    MapLocation loc = Decoding.mapLocation(locEncoding);
    return Message.builder(MessageType.HQ_STATE).loc(loc).hqState(hqState).build();
  }

  public static Message locationMessage(int encoding) {
    // Assume the encoding is in the format
    //    <location bits> | <message type bits>

    // Extract the hq state by extracting the first MESSAGE_TYPE_ENCODING_LENGTH bits
    int messageTypeEncoding = encoding & Encoding.MESSAGE_TYPE_ENCODING_MASK;

    // Extract the loc state by extracting the next MAPLOCATION_ENCODING_LENGTH bits
    int locEncoding = (encoding >> Encoding.MESSAGE_TYPE_ENCODING_LENGTH) & Encoding.MAPLOCATION_ENCODING_MASK;

    MessageType messageType = Decoding.messageType(messageTypeEncoding);
    MapLocation loc = Decoding.mapLocation(locEncoding);
    return Message.builder(messageType).loc(loc).build();
  }
}
