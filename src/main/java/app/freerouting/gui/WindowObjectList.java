package app.freerouting.gui;

import app.freerouting.board.CoordinateTransform;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.analytics.FRAnalytics;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Abstract class for windows displaying a list of objects */
public abstract class WindowObjectList extends BoardSavableSubWindow {

  protected static final int DEFAULT_TABLE_SIZE = 20;
  protected final BoardFrame boardFrame;
  protected final JPanel southPanel;

  /** The subwindows with information about selected object */
  protected final Collection<WindowObjectInfo> subwindows = new LinkedList<>();

  protected final JPanel mainPanel;
  protected final JPanel centerPanel;
  protected JLabel listEmptyMessage;
  protected JList<Object> list;
  private JScrollPane listScrollPane;
  private DefaultListModel<Object> listModel;

  /** Creates a new instance of ObjectListWindow */
  protected WindowObjectList(BoardFrame p_board_frame) {
    setLanguage(p_board_frame.get_locale());
    this.boardFrame = p_board_frame;

    // create main panel
    this.mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());
    this.add(mainPanel);

    // create center panel for list/empty message
    this.centerPanel = new JPanel(new BorderLayout());
    mainPanel.add(this.centerPanel, BorderLayout.CENTER);

    // create a panel for adding buttons
    this.southPanel = new JPanel();
    southPanel.setLayout(new BorderLayout());
    mainPanel.add(southPanel, BorderLayout.SOUTH);

    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new BorderLayout());
    this.southPanel.add(buttonPanel, BorderLayout.CENTER);

    JPanel northButtonPanel = new JPanel();
    buttonPanel.add(northButtonPanel, BorderLayout.NORTH);

    if (showInfoButton()) {
      JButton infoComponentsShowButton = new JButton(tm.getText("info"));
      infoComponentsShowButton.setToolTipText(tm.getText("info_tooltip"));
      ShowListener showListener = new ShowListener();
      infoComponentsShowButton.addActionListener(showListener);
      infoComponentsShowButton.addActionListener(
          _ ->
              FRAnalytics.buttonClicked(
                  "infoComponentsShowButton", infoComponentsShowButton.getText()));
      northButtonPanel.add(infoComponentsShowButton);
    }

    if (showSelectButton()) {
      JButton infoComponentsInstanceButton = new JButton(tm.getText("select"));
      infoComponentsInstanceButton.setToolTipText(tm.getText("select_tooltip"));
      SelectListener instanceListener = new SelectListener();
      infoComponentsInstanceButton.addActionListener(instanceListener);
      infoComponentsInstanceButton.addActionListener(
          _ ->
              FRAnalytics.buttonClicked(
                  "infoComponentsInstanceButton", infoComponentsInstanceButton.getText()));
      northButtonPanel.add(infoComponentsInstanceButton);
    }

    JPanel southButtonPanel = new JPanel();
    buttonPanel.add(southButtonPanel, BorderLayout.SOUTH);

    if (showInvertButton()) {
      JButton infoComponentsInvertButton = new JButton(tm.getText("invert"));
      infoComponentsInvertButton.setToolTipText(tm.getText("invert_tooltip"));
      infoComponentsInvertButton.addActionListener(new InvertListener());
      infoComponentsInvertButton.addActionListener(
          _ ->
              FRAnalytics.buttonClicked(
                  "infoComponentsInvertButton", infoComponentsInvertButton.getText()));
      southButtonPanel.add(infoComponentsInvertButton);
    }

    if (showRecalculateButton()) {
      JButton infoComponentsRecalculateButton = new JButton(tm.getText("recalculate"));
      infoComponentsRecalculateButton.setToolTipText(tm.getText("recalculate_tooltip"));
      RecalculateListener recalculateListener = new RecalculateListener();
      infoComponentsRecalculateButton.addActionListener(recalculateListener);
      infoComponentsRecalculateButton.addActionListener(
          _ ->
              FRAnalytics.buttonClicked(
                  "infoComponentsRecalculateButton", infoComponentsRecalculateButton.getText()));
      southButtonPanel.add(infoComponentsRecalculateButton);
    }

    this.listEmptyMessage = new JLabel(tm.getText("listEmpty"));
    this.listEmptyMessage.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

    // Dispose this window and all subwindows when closing the window.
    this.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent evt) {
            dispose();
          }
        });
  }

  protected boolean showInfoButton() {
    return true;
  }

  protected boolean showSelectButton() {
    return true;
  }

  protected boolean showInvertButton() {
    return true;
  }

  protected boolean showRecalculateButton() {
    return true;
  }

  @Override
  public void setVisible(boolean p_value) {
    if (p_value) {
      recalculate();
    }
    super.setVisible(p_value);
  }

  protected void recalculate() {
    boolean firstTime = this.list == null;
    if (firstTime) {
      this.listModel = new DefaultListModel<>();
      this.list = new JList<>(this.listModel);
      this.list.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    } else {
      this.listModel.clear();
    }

    this.fill_list();

    if (firstTime) {
      if (this.list.getVisibleRowCount() > 0) {
        listScrollPane = new JScrollPane(this.list);
        centerPanel.add(listScrollPane, BorderLayout.CENTER);
      } else {
        centerPanel.add(listEmptyMessage, BorderLayout.CENTER);
      }
      this.pack();

      this.list.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent evt) {
              if (evt.getClickCount() > 1) {
                select_instances();
              }
            }
          });
    } else {
      if (this.listModel.isEmpty()) {
        if (listScrollPane != null) {
          centerPanel.remove(listScrollPane);
        }
        centerPanel.add(listEmptyMessage, BorderLayout.CENTER);
      } else {
        centerPanel.remove(listEmptyMessage);
        if (listScrollPane == null) {
          listScrollPane = new JScrollPane(this.list);
        }
        centerPanel.add(listScrollPane, BorderLayout.CENTER);
      }
      centerPanel.revalidate();
      centerPanel.repaint();
    }
  }

  @Override
  public void dispose() {
    for (WindowObjectInfo currSubwindow : this.subwindows) {
      if (currSubwindow != null) {
        currSubwindow.dispose();
      }
    }
    super.dispose();
  }

  protected void add_to_list(Object p_object) {
    this.listModel.addElement(p_object);
  }

  /** Fills the list with the objects to display. */
  protected abstract void fill_list();

  protected abstract void select_instances();

  /** Saves also the filter string to disk. */
  @Override
  public void save(ObjectOutputStream p_object_stream) {
    int[] selectedIndices;
    if (this.list != null) {
      selectedIndices = this.list.getSelectedIndices();
    } else {
      selectedIndices = new int[0];
    }
    try {
      p_object_stream.writeObject(selectedIndices);
    } catch (IOException e) {
      FRLogger.error("WindowObjectList.save: save failed", e);
    }
    super.save(p_object_stream);
  }

  @Override
  public boolean read(ObjectInputStream p_object_stream) {
    int[] savedSelectedIndices;
    try {
      savedSelectedIndices = (int[]) p_object_stream.readObject();
    } catch (Exception e) {
      FRLogger.error("WindowObjectListWithFilter.read: read failed", e);
      return false;
    }
    boolean result = super.read(p_object_stream);
    if (this.list != null && savedSelectedIndices.length > 0) {
      this.list.setSelectedIndices(savedSelectedIndices);
    }
    return result;
  }

  /** Listens to the button for showing the selected padstacks */
  private class ShowListener implements ActionListener {

    private static final int WINDOW_OFFSET = 30;

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      List<Object> selectedObjects = list.getSelectedValuesList();
      if (selectedObjects.isEmpty()) {
        return;
      }
      Collection<WindowObjectInfo.Printable> objectList = new LinkedList<>();
      for (int i = 0; i < selectedObjects.size(); i++) {
        objectList.add((WindowObjectInfo.Printable) (selectedObjects.get(i)));
      }
      CoordinateTransform coordinateTransform =
          boardFrame.boardPanel.boardHandling.coordinateTransform;
      WindowObjectInfo newWindow =
          WindowObjectInfo.display(
              tm.getText("window_title"), objectList, boardFrame, coordinateTransform);
      Point loc = getLocation();
      Point newWindowLocation =
          new Point((int) (loc.getX() + WINDOW_OFFSET), (int) (loc.getY() + WINDOW_OFFSET));
      newWindow.setLocation(newWindowLocation);
      subwindows.add(newWindow);
    }
  }

  /** Listens to the button for showing the selected incompletes */
  private class SelectListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      select_instances();
    }
  }

  /** Listens to the button for inverting the selection */
  private class InvertListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      if (listModel == null) {
        return;
      }
      int[] newSelectedIndices = new int[listModel.getSize() - list.getSelectedIndices().length];
      int currIndex = 0;
      for (int i = 0; i < listModel.getSize(); i++) {
        if (!list.isSelectedIndex(i)) {
          newSelectedIndices[currIndex] = i;
          ++currIndex;
        }
      }
      list.setSelectedIndices(newSelectedIndices);
    }
  }

  /** Listens to the button for recalculating the content of the window */
  private class RecalculateListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent p_evt) {
      recalculate();
    }
  }
}
