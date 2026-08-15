package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.rendering.GraphicsContext;
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

  /** Creates a new instance of ColorManager. */
  public ColorManager(BoardFrame boardFrame) {
    setLanguage(boardFrame.getLocale());
    GraphicsContext graphicsContext = boardFrame.boardPanel.boardHandling.graphicsContext;

    this.setTitle(tm.getText("colorManager"));
    final JPanel panel = new JPanel();
    final int textfieldHeight = 20;
    final int tableWidth = 1100;
    final int itemColorTableHeight = graphicsContext.itemColorTable.getRowCount() * textfieldHeight;

    panel.setPreferredSize(new Dimension(10 + tableWidth, 90 + itemColorTableHeight));

    layersColorTable = new JTable(graphicsContext.itemColorTable);
    layersColorTable.setPreferredScrollableViewportSize(
        new Dimension(tableWidth, itemColorTableHeight));
    JScrollPane itemScrollPane = initColorTable(layersColorTable, boardFrame.getLocale());
    panel.add(itemScrollPane, BorderLayout.NORTH);

    generalColorTable = new JTable(graphicsContext.otherColorTable);
    generalColorTable.setPreferredScrollableViewportSize(
        new Dimension(tableWidth, textfieldHeight));
    JScrollPane otherScrollPane = initColorTable(generalColorTable, boardFrame.getLocale());
    panel.add(otherScrollPane, BorderLayout.SOUTH);
    getContentPane().add(panel, BorderLayout.CENTER);
    this.pack();
    this.setResizable(false);
  }

  /** Initializes colorTable and return the created scrollPane of the color table. */
  private static JScrollPane initColorTable(JTable colorTable, Locale locale) {
    // Create the scroll pane and add the table to it.
    JScrollPane scrollPane = new JScrollPane(colorTable);
    // Set up renderer and editor for the Color columns.
    colorTable.setDefaultRenderer(Color.class, new ColorRenderer(true));

    setUpColorEditor(colorTable, locale);
    return scrollPane;
  }

  // Set up the editor for the Color cells.
  private static void setUpColorEditor(JTable table, Locale locale) {
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
    table.setDefaultEditor(Color.class, colorEditor);

    // Set up the dialog that the colorEditorButton brings up.
    final JColorChooser colorChooser = new JColorChooser();
    ActionListener okListener = _ -> colorEditor.currentColor = colorChooser.getColor();

    TextManager tm = new TextManager(ColorManager.class, locale);
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

  /** Reassigns the table model variables because they may have changed in graphicsContext. */
  public void setTableModels(GraphicsContext graphicsContext) {
    this.layersColorTable.setModel(graphicsContext.itemColorTable);
    this.generalColorTable.setModel(graphicsContext.otherColorTable);
  }

  private static class ColorRenderer extends JLabel implements TableCellRenderer {

    Border unselectedBorder;
    Border selectedBorder;
    boolean isBordered;

    public ColorRenderer(boolean isBordered) {
      super();
      this.isBordered = isBordered;
      setOpaque(true); // MUST do this for background to show up.
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object color, boolean isSelected, boolean hasFocus, int row, int column) {
      setBackground((Color) color);
      if (isBordered) {
        if (isSelected) {
          if (selectedBorder == null) {
            selectedBorder =
                BorderFactory.createMatteBorder(2, 5, 2, 5, table.getSelectionBackground());
          }
          setBorder(selectedBorder);
        } else {
          if (unselectedBorder == null) {
            unselectedBorder = BorderFactory.createMatteBorder(2, 5, 2, 5, table.getBackground());
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
