package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions;

import org.gradoop.common.model.impl.id.GradoopId;
import org.gradoop.common.model.impl.pojo.EPGMVertex;
import org.gradoop.flink.algorithms.gelly.functions.VertexToGellyVertex;

public class VertexToGellyVertexWithSCCValue implements VertexToGellyVertex<SCCVertexValue> {

  private final org.apache.flink.graph.Vertex<GradoopId, SCCVertexValue> reuseVertex;

  public VertexToGellyVertexWithSCCValue() {
    this.reuseVertex = new org.apache.flink.graph.Vertex<>();
  }

  @Override
  public org.apache.flink.graph.Vertex<GradoopId, SCCVertexValue> map(EPGMVertex epgmVertex) throws Exception {
    GradoopId id = epgmVertex.getId();
    SCCVertexValue value = new SCCVertexValue(id.toString());
    reuseVertex.setId(id);
    reuseVertex.setValue(value);
    return reuseVertex;
  }
}
