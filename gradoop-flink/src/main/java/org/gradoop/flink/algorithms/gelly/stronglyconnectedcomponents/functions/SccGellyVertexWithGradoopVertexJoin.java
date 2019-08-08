package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions;

import org.apache.flink.api.common.functions.JoinFunction;
import org.gradoop.common.model.impl.id.GradoopId;
import org.gradoop.common.model.impl.pojo.EPGMVertex;

public class SccGellyVertexWithGradoopVertexJoin implements JoinFunction<EPGMVertex,
  org.apache.flink.graph.Vertex<GradoopId, SCCVertexValue>, EPGMVertex> {

  private final String propertyKey;

  public SccGellyVertexWithGradoopVertexJoin(String propertyKey) {
    this.propertyKey = propertyKey;
  }

  @Override
  public EPGMVertex join(EPGMVertex epgmVertex,
    org.apache.flink.graph.Vertex<GradoopId, SCCVertexValue> gellyVertex) throws Exception {
    epgmVertex.setProperty(propertyKey, gellyVertex.getValue().getSccId());
    return epgmVertex;
  }
}
