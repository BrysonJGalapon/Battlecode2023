package thursday.utils;

import java.util.*;
import battlecode.common.*;

public class Utils {
    public static final long SEED = 123141;
    private static Random RNG;

    /** Array containing all the possible movement directions. */
    public static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    public static Random getRng() {
        if (RNG == null) {
            RNG = new Random(SEED);
        }

        return RNG;
    }

    public static <T> T getRandomValueFrom(T[] arr) {
        int pick = getRng().nextInt(arr.length);
        return arr[pick];
    }

    public static Direction getRandomDirection() {
      return getRandomValueFrom(directions);
    }
}
