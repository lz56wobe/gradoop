package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.asm.degree.annotate.directed.VertexDegrees;
import org.gradoop.common.model.impl.id.GradoopId;

public class DegreeVertexToSccVertexMap implements MapFunction<Vertex<GradoopId,
  VertexDegrees.Degrees>, Vertex<GradoopId, SCCVertexValue>> {

  private final Vertex<GradoopId, SCCVertexValue> reuseVertex;

  public DegreeVertexToSccVertexMap() {
    this.reuseVertex = new Vertex<>();
  }

  @Override
  public Vertex<GradoopId, SCCVertexValue> map(
    Vertex<GradoopId, VertexDegrees.Degrees> degreeVertex) throws Exception {
    GradoopId id = degreeVertex.getId();
    reuseVertex.setId(id);
    reuseVertex.setValue(new SCCVertexValue(id.toString()));
    return reuseVertex;
  }
}
