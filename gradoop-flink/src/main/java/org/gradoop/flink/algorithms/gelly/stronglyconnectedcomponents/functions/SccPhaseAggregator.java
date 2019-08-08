package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions;

import org.apache.flink.api.common.aggregators.Aggregator;
import org.apache.flink.types.IntValue;

public class SccPhaseAggregator implements Aggregator<IntValue> {

  private int phase = 0;

  @Override
  public IntValue getAggregate() {
    return new IntValue(phase);
  }

  @Override
  public void aggregate(IntValue phaseValue) {
    this.phase = phaseValue.getValue();
  }

  public void aggregate(int phase) {
    this.phase = phase;
  }

  @Override
  public void reset() {
    this.phase = 0;
  }
}
