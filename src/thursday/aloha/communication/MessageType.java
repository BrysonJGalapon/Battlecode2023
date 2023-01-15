package aloha.communication;

public enum MessageType {
  // HQ_STATE messages store a MapLocation of the headquarters and a HeadquartersState of the headquarters
  HQ_STATE,

  // ENEMY_LOC messages store a MapLocation of the enemy
  ENEMY_LOC,

  // NO_ENEMY_LOC messages store a MapLocation of where the enemy is not
  NO_ENEMY_LOC,

  // AD_WELL_LOC messages store a MapLocation of the Ad well
  AD_WELL_LOC,

  // MN_WELL_LOC messages store a MapLocation of the Mn well
  MN_WELL_LOC,

  // EX_WELL_LOC messages store a MapLocation of the Ex well
  EX_WELL_LOC,

  // FRIENDLY_ISLAND_LOC messages store a MapLocation of one of the cells in the sky-island owned by us
  FRIENDLY_ISLAND_LOC,

  // NEUTRAL_ISLAND_LOC messages store a MapLocation of one of the cells in the sky-island not owned by anyone
  NEUTRAL_ISLAND_LOC,

  // ENEMY_ISLAND_LOC messages store a MapLocation of one of the cells in the sky-island owned by the enemy
  ENEMY_ISLAND_LOC;
}
