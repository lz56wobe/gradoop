package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions;

import org.apache.flink.api.common.aggregators.Aggregator;
import org.apache.flink.types.BooleanValue;

public class SccNewMaxAggregator implements Aggregator<BooleanValue> {

  private boolean newMaxFound = false;

  @Override
  public BooleanValue getAggregate() {
    return new BooleanValue(newMaxFound);
  }

  @Override
  public void aggregate(BooleanValue newMaxFoundValue) {
    this.newMaxFound = (this.newMaxFound || newMaxFoundValue.get());
  }

  public void aggregate(boolean newMaxFound) {
    this.newMaxFound = (this.newMaxFound || newMaxFound);
  }

  @Override
  public void reset() {
    this.newMaxFound = false;
  }
}
