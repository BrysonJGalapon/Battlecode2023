package thursday.communication;

import battlecode.common.*;
import java.util.*;
import thursday.communication.basic.*;

// Communicators can send and receive messages
public interface Communicator {
  public boolean sendMessage(Message message, RobotController rc) throws GameActionException;
  public List<Message> receiveMessages(MessageType messageType, RobotController rc) throws GameActionException;

  public static Communicator newCommunicator() {
    return new BasicCommunicator();
  }
}
