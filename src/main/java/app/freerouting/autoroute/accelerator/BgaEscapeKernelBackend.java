package app.freerouting.autoroute.accelerator;

/**
 * A compute backend that runs the BGA escape kernels itself and hands back the solution buffer,
 * without exposing its driver objects to the solver.
 */
public interface BgaEscapeKernelBackend {

  /**
   * Runs rasterisation, via site evaluation and the ring-by-ring negotiation for one package.
   *
   * @param grid the marshalled package and grid
   * @return {@link BgaEscapeGrid#SOLUTION_STRIDE} ints per pin, in the layout written by {@code
   *     expand_ring_candidates} and {@code commit_cell_winners}
   */
  int[] runBgaEscape(BgaEscapeGrid grid);
}
