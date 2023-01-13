package aloha.communication;

import battlecode.common.*;
import aloha.robots.headquarters.HeadquartersState;

// Message represents a message to a specific entity.
public class Message {
  // metadata fields (required)
  public final MessageType messageType;
  public final Entity recipient;

  // data fields (optional)
  public final MapLocation loc;
  public final HeadquartersState hqState;

  private Message(MessageType messageType, Entity recipient, MapLocation loc, HeadquartersState hqState) {
    this.messageType = messageType;
    this.recipient = recipient;
    this.loc = loc;
    this.hqState = hqState;
  }

  public static Builder builder(MessageType messageType) {
    return new Builder(messageType);
  }

  public static class Builder {
    private final MessageType messageType;

    private Entity recipient;
    private MapLocation loc;
    private HeadquartersState hqState;

    private Builder(MessageType messageType) {
      this.messageType = messageType;
    }

    public Builder recipient(Entity recipient) {
      this.recipient = recipient;
      return this;
    }

    public Builder loc(MapLocation loc) {
      this.loc = loc;
      return this;
    }

    public Builder hqState(HeadquartersState hqState) {
      this.hqState = hqState;
      return this;
    }

    public Message build() {
      return new Message(messageType, recipient, loc, hqState);
    }
  }
}
