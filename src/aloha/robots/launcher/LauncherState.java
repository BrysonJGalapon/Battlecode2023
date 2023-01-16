package aloha.robots.launcher;

public enum LauncherState {
  // FOLLOWER follows other launchers
  FOLLOWER,

  // OCCUPY_SKY_ISLAND occupies a sky-island
  OCCUPY_SKY_ISLAND,

  // PROTECT_WELL protects a well
  PROTECT_WELL,

  // CROWD_HQ crowds an enemy HQ and attacks robots that spawn
  CROWD_HQ;
}
