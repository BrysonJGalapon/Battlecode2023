package friday.communication;

import battlecode.common.*;
import friday.robots.headquarters.HeadquartersState;

// Message represents a message to a specific entity.
public class Message {
  // metadata fields (required)
  public final MessageType messageType;

  // data fields (optional)
  public final MapLocation loc;
  public final Entity recipient;
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

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof Message)) {
      return false;
    }

    // this.messageType = messageType;
    // this.recipient = recipient;
    // this.loc = loc;
    // this.hqState = hqState;

    Message other = (Message) o;
    return this.messageType == other.messageType &&
      this.recipient == other.recipient &&
      this.loc.equals(other.loc) && this.hqState == other.hqState;
  }

  @Override
  public int hashCode() {
    int result = 17;

    result = 31 * result + this.messageType.hashCode();
    if (this.recipient != null) {
      result = 31 * result + this.recipient.hashCode();
    }
    if (this.loc != null) {
      result = 31 * result + this.loc.hashCode();
    }
    if (this.hqState != null) {
      result = 31 * result + this.hqState.hashCode();
    }

    return result;
  }
}
