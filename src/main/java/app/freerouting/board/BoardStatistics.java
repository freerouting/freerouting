package app.freerouting.board;

public class BoardStatistics
{
  public int unrouted_item_count;
  public int routed_item_count;
  public int ripped_item_count;
  public int not_found_item_count;
  public int traceCount;
  public int viaCount;
  public int conductionAreaCount;
  public int drillItemCount;
  public int pinCount;
  public int componentOutlineCount;
  public int otherCount;
  public double totalTraceLength;
  public double weightedTraceLength;
  // TODO: is this the same as the unrouted item count?
  public int incompleteItemCount;
}