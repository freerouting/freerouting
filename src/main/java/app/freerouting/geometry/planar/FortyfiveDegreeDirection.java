package app.freerouting.geometry.planar;

@SuppressWarnings("all") // Eclipse regards get_direction() as unused

/** Enum for the eight 45-degree direction starting from right in counterclocksense to down45. */
public enum FortyfiveDegreeDirection {
  RIGHT {
    public IntDirection get_direction() {
      return Direction.RIGHT;
    }
  },
  RIGHT45 {
    public IntDirection get_direction() {
      return Direction.RIGHT45;
    }
  },
  UP {
    public IntDirection get_direction() {
      return Direction.UP;
    }
  },
  UP45 {
    public IntDirection get_direction() {
      return Direction.UP45;
    }
  },
  LEFT {
    public IntDirection get_direction() {
      return Direction.LEFT;
    }
  },
  LEFT45 {
    public IntDirection get_direction() {
      return Direction.LEFT45;
    }
  },
  DOWN {
    public IntDirection get_direction() {
      return Direction.DOWN;
    }
  },
  DOWN45 {
    public IntDirection get_direction() {
      return Direction.DOWN45;
    }
  }
}
