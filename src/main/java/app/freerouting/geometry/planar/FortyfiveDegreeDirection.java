package app.freerouting.geometry.planar;

@SuppressWarnings("all") // Eclipse regards getDirection() as unused

/** Enum for the eight 45-degree direction starting from right in counterclocksense to down45. */
public enum FortyfiveDegreeDirection {
  RIGHT {
    public IntDirection getDirection() {
      return Direction.RIGHT;
    }
  },
  RIGHT45 {
    public IntDirection getDirection() {
      return Direction.RIGHT45;
    }
  },
  UP {
    public IntDirection getDirection() {
      return Direction.UP;
    }
  },
  UP45 {
    public IntDirection getDirection() {
      return Direction.UP45;
    }
  },
  LEFT {
    public IntDirection getDirection() {
      return Direction.LEFT;
    }
  },
  LEFT45 {
    public IntDirection getDirection() {
      return Direction.LEFT45;
    }
  },
  DOWN {
    public IntDirection getDirection() {
      return Direction.DOWN;
    }
  },
  DOWN45 {
    public IntDirection getDirection() {
      return Direction.DOWN45;
    }
  }
}
