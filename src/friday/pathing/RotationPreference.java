package aloha.pathing;

import aloha.utils.*;

public enum RotationPreference {
    LEFT,
    RIGHT,
    RANDOM;

    public static RotationPreference[] CONCRETE_ROTATION_PREFERENCES = new RotationPreference[]{LEFT, RIGHT};

    public static RotationPreference getRandomConcreteRotationPreference() {
        return Utils.getRandomValueFrom(CONCRETE_ROTATION_PREFERENCES);
    }

    public RotationPreference opposite() {
        switch (this) {
            case LEFT: return RIGHT;
            case RIGHT: return LEFT;
            default: throw new RuntimeException("Should not be here");
        }
    }
}
