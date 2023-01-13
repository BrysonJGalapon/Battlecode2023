package aloha.robots.carrier;

public enum CarrierState {
  // TAKE_ANCHOR tries to take an anchor from the HQ
  TAKE_ANCHOR,

  // PLACE_ANCHOR tries to place an anchor on a sky-island
  PLACE_ANCHOR,

  // ATTACK_LOC tries to attack a location
  ATTACK_LOC,

  // COLLECT_RESOURCE tries to collect a resource from a well
  COLLECT_RESOURCE,

  // DEPOSIT_RESOURCE tries to deposit a resource to an HQ
  DEPOSIT_RESOURCE,

  // SURVIVE tries to extend the life of this robot as much as possible
  SURVIVE;
}
