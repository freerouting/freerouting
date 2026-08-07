package app.freerouting.gui;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;

/** Window for changing the colors of board objects. */
public class ColorManager extends BoardSavableSubWindow {

  private final JTable layersColorTable;
  private final JTable generalColorTable;

  /** Creates a new instance of ColorManager */
  public ColorManager(BoardFrame p_board_frame) {
    setLanguage(p_board_frame.get_locale());
    GraphicsContext graphicsContext = p_board_frame.boardPanel.boardHandling.graphicsContext;

    this.setTitle(tm.getText("colorManager"));
    final JPanel panel = new JPanel();
    final int textfieldHeight = 20;
    final int tableWidth = 1100;
    final int itemColorTableHeight = graphicsContext.itemColorTable.getRowCount() * textfieldHeight;

    panel.setPreferredSize(new Dimension(10 + tableWidth, 90 + itemColorTableHeight));

    layersColorTable = new JTable(graphicsContext.itemColorTable);
    layersColorTable.setPreferredScrollableViewportSize(
        new Dimension(tableWidth, itemColorTableHeight));
    JScrollPane itemScrollPane = init_color_table(layersColorTable, p_board_frame.get_locale());
    panel.add(itemScrollPane, BorderLayout.NORTH);

    generalColorTable = new JTable(graphicsContext.otherColorTable);
    generalColorTable.setPreferredScrollableViewportSize(
        new Dimension(tableWidth, textfieldHeight));
    JScrollPane otherScrollPane = init_color_table(generalColorTable, p_board_frame.get_locale());
    panel.add(otherScrollPane, BorderLayout.SOUTH);
    getContentPane().add(panel, BorderLayout.CENTER);
    this.pack();
    this.setResizable(false);
  }

  /** Initializes p_color_table and return the created scrollPane of the color table. */
  private static JScrollPane init_color_table(JTable p_color_table, Locale p_locale) {
    // Create the scroll pane and add the table to it.
    JScrollPane scrollPane = new JScrollPane(p_color_table);
    // Set up renderer and editor for the Color columns.
    p_color_table.setDefaultRenderer(Color.class, new ColorRenderer(true));

    setUpColorEditor(p_color_table, p_locale);
    return scrollPane;
  }

  // Set up the editor for the Color cells.
  private static void setUpColorEditor(JTable p_table, Locale p_locale) {
    // First, set up the colorEditorButton that brings up the dialog.
    final JButton colorEditorButton =
        new JButton("") {
          @Override
          public void setText(String s) {
            // Button never shows text -- only color.
          }
        };
    colorEditorButton.setBackground(Color.white);
    colorEditorButton.setBorderPainted(false);
    colorEditorButton.setMargin(new Insets(0, 0, 0, 0));

    // Now create an editor to encapsulate the colorEditorButton, and
    // set it up as the editor for all Color cells.
    final ColorEditor colorEditor = new ColorEditor(colorEditorButton);
    p_table.setDefaultEditor(Color.class, colorEditor);

    // Set up the dialog that the colorEditorButton brings up.
    final JColorChooser colorChooser = new JColorChooser();
    ActionListener okListener = _ -> colorEditor.currentColor = colorChooser.getColor();

    TextManager tm = new TextManager(ColorManager.class, p_locale);
    final JDialog dialog =
        JColorChooser.createDialog(
            colorEditorButton, tm.getText("pick_a_color"), true, colorChooser, okListener, null);

    // Here's the code that brings up the dialog.
    colorEditorButton.addActionListener(
        _ -> {
          colorEditorButton.setBackground(colorEditor.currentColor);
          colorChooser.setColor(colorEditor.currentColor);
          // Without the following line, the dialog comes up
          // in the middle of the screen.
          // dialog.setLocationRelativeTo(colorEditorButton);
          dialog.setVisible(true);
        });
    colorEditorButton.addActionListener(
        _ -> FRAnalytics.buttonClicked("colorEditorButton", colorEditorButton.getText()));
  }

  /** Reassigns the table model variables because they may have changed in p_graphics_context. */
  public void set_table_models(GraphicsContext p_graphics_context) {
    this.layersColorTable.setModel(p_graphics_context.itemColorTable);
    this.generalColorTable.setModel(p_graphics_context.otherColorTable);
  }

  private static class ColorRenderer extends JLabel implements TableCellRenderer {

    Border unselectedBorder;
    Border selectedBorder;
    boolean isBordered;

    public ColorRenderer(boolean p_is_bordered) {
      super();
      this.isBordered = p_is_bordered;
      setOpaque(true); // MUST do this for background to show up.
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable p_table,
        Object p_color,
        boolean p_is_selected,
        boolean p_has_focus,
        int p_row,
        int p_column) {
      setBackground((Color) p_color);
      if (isBordered) {
        if (p_is_selected) {
          if (selectedBorder == null) {
            selectedBorder =
                BorderFactory.createMatteBorder(2, 5, 2, 5, p_table.getSelectionBackground());
          }
          setBorder(selectedBorder);
        } else {
          if (unselectedBorder == null) {
            unselectedBorder = BorderFactory.createMatteBorder(2, 5, 2, 5, p_table.getBackground());
          }
          setBorder(unselectedBorder);
        }
      }
      return this;
    }
  }

  /**
   * The editor button that brings up the dialog. We extend DefaultCellEditor for convenience, even
   * though it mean we have to create a dummy check box. Another approach would be to copy the
   * implementation of TableCellEditor methods from the source code for DefaultCellEditor.
   */
  private static class ColorEditor extends DefaultCellEditor {

    Color currentColor;

    public ColorEditor(JButton b) {
      super(new JCheckBox()); // Unfortunately, the constructor
      // expects a checkbox, combo-box, or text field.
      editorComponent = b;
      setClickCountToStart(1); // This is usually 1 or 2.

      // Must do this so that editing stops when appropriate.
      b.addActionListener(_ -> fireEditingStopped());
    }

    @Override
    protected void fireEditingStopped() {
      super.fireEditingStopped();
    }

    @Override
    public Object getCellEditorValue() {
      return currentColor;
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int row, int column) {
      ((JButton) editorComponent).setText(value.toString());
      currentColor = (Color) value;
      return editorComponent;
    }
  }
}
