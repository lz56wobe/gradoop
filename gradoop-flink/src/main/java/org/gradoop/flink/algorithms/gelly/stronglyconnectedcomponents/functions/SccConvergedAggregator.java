package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions;

import org.apache.flink.api.common.aggregators.Aggregator;
import org.apache.flink.types.BooleanValue;

public class SccConvergedAggregator implements Aggregator<BooleanValue> {

  private boolean hasConverged = false;

  @Override
  public BooleanValue getAggregate() {
    return new BooleanValue(hasConverged);
  }

  @Override
  public void aggregate(BooleanValue hasConvergedValue) {
    this.hasConverged = (this.hasConverged || hasConvergedValue.get());
  }

  public void aggregate(boolean hasConverged) {
    this.hasConverged = (this.hasConverged || hasConverged);
  }

  @Override
  public void reset() {
    this.hasConverged = false;
  }
}
