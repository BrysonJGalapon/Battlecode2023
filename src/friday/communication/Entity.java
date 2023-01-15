package friday.communication;

import battlecode.common.*;

public enum Entity {
  ALL_ROBOTS,
  HEADQUARTERS,
  CARRIERS,
  LAUNCHERS,
  AMPLIFIERS,
  BOOSTERS,
  DESTABILIZERS;

  public static Entity of(RobotType robotType) {
    switch(robotType) {
      case HEADQUARTERS:  return Entity.HEADQUARTERS;
      case CARRIER:       return Entity.CARRIERS;
      case LAUNCHER:      return Entity.LAUNCHERS;
      case AMPLIFIER:     return Entity.AMPLIFIERS;
      case BOOSTER:       return Entity.BOOSTERS;
      case DESTABILIZER:  return Entity.DESTABILIZERS;
      default: throw new RuntimeException("should not be here");
    }
  }
}


