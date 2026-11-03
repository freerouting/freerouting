package app.freerouting.autoroute.pipeline;

import app.freerouting.board.model.items.Item;
import java.util.List;

/** Test-only access to real pass boundaries without making them public production APIs. */
public final class AutoroutePassTestSupport {
  private AutoroutePassTestSupport() {}

  /** Snapshots the actual representatives selected before seeded reordering. */
  public static List<Item> representatives(BatchAutorouter router) {
    return List.copyOf(router.getAutorouteItems(router.board));
  }

  /** Runs a production pass, including the seeded permutation for passes after the first. */
  public static boolean runPass(BatchAutorouter router, int pass) {
    return router.autoroutePass(pass);
  }
}
