package app.freerouting.board.facade;

import app.freerouting.board.model.items.Trace;
import app.freerouting.logger.FRLogger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;

/**
 * Owns board snapshots and the existing binary serialization helpers.
 *
 * <p>All state held by this manager is transient; the board itself remains the serialized root and
 * therefore keeps its established wire representation.
 */
public final class BoardSnapshotManager {

  private final BasicBoard board;

  BoardSnapshotManager(BasicBoard board) {
    this.board = board;
  }

  /** Serializes either the complete board or the historical basic trace profile. */
  byte[] serialize(boolean basicProfile) {
    try {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
      if (basicProfile) {
        objectStream.writeObject(board.getTraces());
        objectStream.writeObject(board.getVias());
        objectStream.writeObject(board.itemList);
      } else {
        objectStream.writeObject(board);
      }
      objectStream.close();
      return outputStream.toByteArray();
    } catch (Exception exception) {
      FRLogger.error("Couldn't serialize board", exception);
      return null;
    }
  }

  /** Deserializes a board from the established binary representation. */
  static BasicBoard deserialize(byte[] objectByteArray) {
    try {
      ByteArrayInputStream inputStream = new ByteArrayInputStream(objectByteArray);
      ObjectInputStream objectStream = new app.freerouting.util.SafeObjectInputStream(inputStream);
      return (BasicBoard) objectStream.readObject();
    } catch (Exception exception) {
      FRLogger.error("Couldn't deserialize board", exception);
      return null;
    }
  }

  /** Returns an MD5 hash of the board trace-state profile. */
  String getHash() {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      digest.update(serialize(true));
      byte[] hashedBytes = digest.digest();
      StringBuilder result = new StringBuilder();
      for (byte hashedByte : hashedBytes) {
        result.append(Integer.toString((hashedByte & 0xff) + 0x100, 16).substring(1));
      }
      return result.toString();
    } catch (Exception exception) {
      FRLogger.error("Couldn't calculate hash for board", exception);
      return null;
    }
  }

  /** Makes the current item/component state restorable by undo. */
  void generateSnapshot() {
    board.itemList.generateSnapshot();
    board.components.generateSnapshot();
  }

  /** Removes the top snapshot from the undo stack. */
  boolean popSnapshot() {
    return board.itemList.popSnapshot();
  }

  /** Returns the number of trace IDs that differ from another board. */
  int diffTraces(BasicBoard compareTo) {
    int result = 0;
    java.util.HashSet<Integer> traceIds = new java.util.HashSet<>();
    for (Trace trace : board.getTraces()) {
      traceIds.add(trace.getId());
    }
    for (Trace trace : compareTo.getTraces()) {
      if (!traceIds.contains(trace.getId())) {
        result++;
      } else {
        traceIds.remove(trace.getId());
      }
    }
    return result + traceIds.size();
  }
}
