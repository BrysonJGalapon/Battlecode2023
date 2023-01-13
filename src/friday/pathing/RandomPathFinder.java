package friday.pathing;

import battlecode.common.*;
import java.util.*;
import friday.utils.*;

public class RandomPathFinder implements PathFinder {
  private static final Random rng = new Random(6147);

  public Optional<Direction> findPath(MapLocation src, MapLocation dst, RobotController rc) throws GameActionException {
    Direction dir = Utils.directions[rng.nextInt(Utils.directions.length)];
    if (rc.canMove(dir)) {
        rc.move(dir);
    }
    return Optional.empty();
  }
}
