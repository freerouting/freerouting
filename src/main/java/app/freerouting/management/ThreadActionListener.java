package app.freerouting.management;

public interface ThreadActionListener {

  void autorouterStarted();

  void autorouterAborted();

  void autorouterFinished();
}
