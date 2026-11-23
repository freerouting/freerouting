package app.freerouting.io.kicad;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of a KiCad S-expression, {@code (keyword atom (child ...) ...)}, read in full. Used for
 * the small items of a board file whose content is needed; the large ones are skipped unread.
 *
 * @param keyword the first atom of the list
 * @param children the rest of the list in file order: {@link String} atoms and nested nodes
 */
record SexprNode(String keyword, List<Object> children) {

  /** The first nested list with this keyword, or null. */
  SexprNode child(String childKeyword) {
    for (Object child : children) {
      if (child instanceof SexprNode node && node.keyword.equals(childKeyword)) {
        return node;
      }
    }
    return null;
  }

  /** All the nested lists with this keyword, in file order. */
  List<SexprNode> childrenNamed(String childKeyword) {
    List<SexprNode> result = new ArrayList<>();
    for (Object child : children) {
      if (child instanceof SexprNode node && node.keyword.equals(childKeyword)) {
        result.add(node);
      }
    }
    return result;
  }

  /** The atoms of the list, nested lists left out. */
  List<String> atoms() {
    List<String> result = new ArrayList<>();
    for (Object child : children) {
      if (child instanceof String atom) {
        result.add(atom);
      }
    }
    return result;
  }

  /** The atom at this position among the atoms, or an empty string. */
  String atom(int index) {
    List<String> atoms = atoms();
    return index < atoms.size() ? atoms.get(index) : "";
  }

  /** Whether the atom is one of the atoms of the list. */
  boolean hasAtom(String value) {
    return atoms().contains(value);
  }

  /** The number at this position among the atoms, in millimetres, or the fallback. */
  double number(int index, double fallback) {
    try {
      return Double.parseDouble(atom(index));
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
