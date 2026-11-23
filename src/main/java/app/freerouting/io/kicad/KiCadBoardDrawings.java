package app.freerouting.io.kicad;

import app.freerouting.io.kicad.KiCadBoardZones.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The copper drawings and texts without net of a KiCad board, of the board and of its footprints,
 * read from its {@code .kicad_pcb} file.
 *
 * <p>The Specctra export of KiCad leaves them out entirely, while the KiCad DRC keeps every net
 * away from them. They are read as {@link KiCadCopperGraphic}s, a text as one per character.
 *
 * <p>Only the layer table, the positions of the footprints and the drawings are read, with the
 * reader of {@link KiCadBoardZones}; the rest of the file, the fill polygons of the zones among it,
 * is skipped without being stored.
 */
public final class KiCadBoardDrawings {

  private final String sourceName;
  private final List<KiCadCopperGraphic> netlessCopper;
  private final List<String> copperLayers;

  private KiCadBoardDrawings(
      String sourceName, List<KiCadCopperGraphic> netlessCopper, List<String> copperLayers) {
    this.sourceName = sourceName;
    this.netlessCopper = List.copyOf(netlessCopper);
    this.copperLayers = List.copyOf(copperLayers);
  }

  /**
   * The copper drawings without net of the board and of its footprints, in file order: lines, arcs,
   * circles, rectangles, polygons, curves and visible texts on a copper layer. The Specctra export
   * of KiCad leaves them out, while the KiCad DRC keeps every net away from them.
   */
  public List<KiCadCopperGraphic> netlessCopper() {
    return netlessCopper;
  }

  /**
   * The copper layers of the layer table of the board, from front to back, as its drawings name
   * them; empty if the board has no layer table.
   */
  public List<String> copperLayers() {
    return copperLayers;
  }

  /** The file name, for messages. */
  public String sourceName() {
    return sourceName;
  }

  /** Reads a {@code .kicad_pcb} file. */
  public static KiCadBoardDrawings read(Path path) throws IOException {
    return parse(Files.readString(path, StandardCharsets.UTF_8), path.getFileName().toString());
  }

  /**
   * Parses the text of a {@code .kicad_pcb} file.
   *
   * @throws IllegalArgumentException if the text is not a KiCad board
   */
  public static KiCadBoardDrawings parse(String text, String sourceName) {
    Reader reader = new Reader(text);
    reader.skipBlanks();
    if (!reader.enterList() || !"kicad_pcb".equals(reader.readAtom())) {
      throw new IllegalArgumentException("not a KiCad board file: no (kicad_pcb ...)");
    }
    List<String> copperLayers = new ArrayList<>();
    List<KiCadCopperGraphic> netlessCopper = new ArrayList<>();
    int version = 0;
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished()) {
        throw new IllegalArgumentException("not a KiCad board file: missing ')'");
      }
      if (reader.leaveList()) {
        break;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      String keyword = reader.readAtom();
      switch (keyword) {
        case "footprint", "module" -> readFootprint(reader, version, copperLayers, netlessCopper);
        case "gr_line",
            "gr_arc",
            "gr_circle",
            "gr_rect",
            "gr_poly",
            "gr_curve",
            "gr_text",
            "gr_text_box" -> {
          for (KiCadCopperGraphic.Draft draft :
              KiCadCopperGraphic.drafts(reader.readRestOfList(keyword), version, copperLayers)) {
            netlessCopper.add(draft.place(0, 0, 0, ""));
          }
        }
        case "layers" -> KiCadBoardZones.readCopperLayers(reader, copperLayers);
        case "version" -> {
          version = (int) KiCadBoardZones.toNumber(reader.readAtom());
          reader.skipToEndOfList();
        }
        default -> reader.skipToEndOfList();
      }
    }
    return new KiCadBoardDrawings(sourceName, netlessCopper, copperLayers);
  }

  /** Reads the rest of a {@code (footprint ...)} and adds its copper drawings without net. */
  private static void readFootprint(
      Reader reader,
      int version,
      List<String> copperLayers,
      List<KiCadCopperGraphic> netlessCopper) {
    double[] at = new double[3];
    List<KiCadCopperGraphic.Draft> graphics = new ArrayList<>();
    String reference = "";
    while (true) {
      reader.skipBlanks();
      if (reader.isFinished()) {
        throw new IllegalArgumentException("not a KiCad board file: unfinished footprint");
      }
      if (reader.leaveList()) {
        break;
      }
      if (!reader.enterList()) {
        reader.readAtom();
        continue;
      }
      String keyword = reader.readAtom();
      switch (keyword) {
        case "at" -> {
          KiCadBoardZones.readNumbers(reader, at);
          at[0] *= 1000.0;
          at[1] *= 1000.0;
          reader.skipToEndOfList();
        }
        case "fp_line",
            "fp_arc",
            "fp_circle",
            "fp_rect",
            "fp_poly",
            "fp_curve",
            "fp_text",
            "fp_text_box",
            "property" -> {
          SexprNode item = reader.readRestOfList(keyword);
          String itemReference = KiCadCopperGraphic.referenceOf(item);
          if (itemReference != null) {
            reference = itemReference;
          }
          graphics.addAll(KiCadCopperGraphic.drafts(item, version, copperLayers));
        }
        default -> reader.skipToEndOfList();
      }
    }
    // the drawings are written in the frame of the footprint, which can be placed after them
    for (KiCadCopperGraphic.Draft graphic : graphics) {
      netlessCopper.add(graphic.place(at[0], at[1], at[2], reference));
    }
  }
}
