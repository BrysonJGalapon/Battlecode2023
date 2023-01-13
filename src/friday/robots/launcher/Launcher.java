package friday.robots.launcher;

import battlecode.common.*;
import java.util.*;
import friday.utils.Utils;

public class Launcher {
  static final Random rng = new Random(6147);

  public static void run(RobotController rc) throws GameActionException {
      // Try to attack someone
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if (enemies.length >= 0) {
            // MapLocation toAttack = enemies[0].location;
            MapLocation toAttack = rc.getLocation().add(Direction.EAST);

            if (rc.canAttack(toAttack)) {
                rc.setIndicatorString("Attacking");
                rc.attack(toAttack);
            }
        }

        // Also try to move randomly.
        Direction dir = Utils.directions[rng.nextInt(Utils.directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
        }
  }
}
