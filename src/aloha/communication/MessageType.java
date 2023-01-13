package aloha.communication;

public enum MessageType {
  // HQ_STATE messages store a MapLocation of the headquarters and a HeadquartersState of the headquarters
  HQ_STATE,

  // ENEMY_LOC messages store a MapLocation of the enemy
  ENEMY_LOC,

  // AD_WELL_LOC messages store a MapLocation of the Ad well
  AD_WELL_LOC,

  // MN_WELL_LOC messages store a MapLocation of the Mn well
  MN_WELL_LOC,

  // EX_WELL_LOC messages store a MapLocation of the Ex well
  EX_WELL_LOC;
}
