package thursday.pathing;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import thursday.pathing.WallFollower;
import thursday.utils.Utils;

import java.util.Random;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;

public class FuzzyPathFinder extends WallFollower implements PathFinder {
    private static final int IMMOVABLE_OBJECT_COST = 1000;
    private static final int MAX_VISITED_SIZE = 7;
    private final Deque<MapLocation> visited = new LinkedList<>();
    private static final Random rng = Utils.getRng();

    private boolean isLeftDisabled = false;
    private boolean isRightDisabled = false;

    private int getCost(MapLocation loc, RobotController rc) throws GameActionException {
        // consider edges of the map, other robots, and previously visited squares as 'immovable objects'
        if (!rc.onTheMap(loc) || rc.senseRobotAtLocation(loc) != null || visited.contains(loc) || !rc.sensePassability(loc)) {
            return IMMOVABLE_OBJECT_COST;
        }


        return 10;
    }

    public void visit(MapLocation src) {
        visited.addLast(src);
        if (visited.size() >= MAX_VISITED_SIZE) {
            visited.removeFirst();
        }
    }

    public void disableLeft() {
        this.isLeftDisabled = true;
    }

    public void disableRight() {
        this.isRightDisabled = true;
    }

    public void enableLeft() {
        this.isLeftDisabled = false;
    }

    public void enableRight() {
        this.isRightDisabled = false;
    }

    public void disableLeftAndRight() {
        disableLeft();
        disableRight();
    }

    public void enableLeftAndRight() {
        enableLeft();
        enableRight();
    }

    public Optional<Direction> getFuzzyDirection(MapLocation src, Direction straightAhead, RobotController rc) throws GameActionException {
        visit(src);
        Direction slightlyLeft = straightAhead.rotateLeft();
        Direction slightlyRight = straightAhead.rotateRight();
        Direction left = slightlyLeft.rotateLeft();
        Direction right = slightlyRight.rotateRight();

        MapLocation straightAheadLocation = src.add(straightAhead);
        MapLocation slightlyLeftLocation = src.add(slightlyLeft);
        MapLocation slightlyRightLocation = src.add(slightlyRight);
        MapLocation leftLocation = src.add(left);
        MapLocation rightLocation = src.add(right);

        int[] costs = new int[]{
                10 * getCost(straightAheadLocation, rc),
                10 * getCost(slightlyLeftLocation, rc),
                10 * getCost(slightlyRightLocation, rc),
                35 * getCost(leftLocation, rc),
                35 * getCost(rightLocation, rc),
        };

        int minCost = 10 * IMMOVABLE_OBJECT_COST;
        int minCostIndex = -1;
        int numMinCostEl = 0;
        for (int i = 0; i < costs.length; i++) {
            if (this.isLeftDisabled && (i == 3)) {
                continue;
            }

            if (this.isRightDisabled && (i == 4)) {
                continue;
            }

            if (costs[i] < minCost) {
                minCost = costs[i];
                minCostIndex = i;
                numMinCostEl = 1;
            } else if (costs[i] == minCost && rng.nextInt(numMinCostEl+1) == 0) {
                minCostIndex = i;
                numMinCostEl++;
            }
        }

        // can't make progress, don't move
        if (minCostIndex == -1) {
            return Optional.empty();
        }

        switch (minCostIndex) {
            case 0: return Optional.of(straightAhead);
            case 1: return Optional.of(slightlyLeft);
            case 2: return Optional.of(slightlyRight);
            case 3: return Optional.of(left);
            case 4: return Optional.of(right);
            default: throw new RuntimeException("Should not be here");
        }
    }

    @Override
    public Optional<Direction> findPath(MapLocation src, MapLocation dst, RobotController rc) throws GameActionException {
        if (src.equals(dst)) {
            return Optional.empty();
        }

        return getFuzzyDirection(src, src.directionTo(dst), rc);
    }
}
