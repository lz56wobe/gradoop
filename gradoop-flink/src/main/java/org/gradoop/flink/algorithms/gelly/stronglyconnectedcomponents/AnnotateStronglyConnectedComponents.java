package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents;

import org.apache.flink.api.java.DataSet;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.pregel.VertexCentricConfiguration;
import org.apache.flink.types.NullValue;
import org.gradoop.common.model.impl.id.GradoopId;
import org.gradoop.common.model.impl.pojo.EPGMVertex;
import org.gradoop.flink.algorithms.gelly.GradoopGellyAlgorithm;
import org.gradoop.flink.algorithms.gelly.functions.EdgeToGellyEdgeWithNullValue;
import org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions.SCCComputeFunction;
import org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions.SCCVertexValue;
import org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions.SccConvergedAggregator;
import org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions.SccGellyVertexWithGradoopVertexJoin;
import org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions.SccNewMaxAggregator;
import org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions.SccPhaseAggregator;
import org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions.VertexToGellyVertexWithSCCValue;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.model.impl.functions.epgm.Id;

import static org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents.functions.SCCComputeFunction.*;

public class AnnotateStronglyConnectedComponents  extends GradoopGellyAlgorithm<SCCVertexValue, NullValue> {

  private final String propertyKey;
  private final int maxIteration;

  public AnnotateStronglyConnectedComponents(String propertyKey, int maxIteration) {
    super(new VertexToGellyVertexWithSCCValue(), new EdgeToGellyEdgeWithNullValue());
    this.propertyKey = propertyKey;
    this.maxIteration = maxIteration;
  }

  @Override
  public LogicalGraph executeInGelly(Graph<GradoopId, SCCVertexValue, NullValue> gellyGraph) throws Exception {

    VertexCentricConfiguration parameters = new VertexCentricConfiguration();
    parameters.registerAggregator(PHASE_AGGREGATOR, new SccPhaseAggregator());
    parameters.registerAggregator(NEW_MAX_AGGREGATOR, new SccNewMaxAggregator());
    parameters.registerAggregator(CONVERGED_AGGREGATOR, new SccConvergedAggregator());
    // parameters.setParallelism(1);

    // Annotate gelly graph vertices with scc ids
    DataSet<Vertex<GradoopId, SCCVertexValue>> annotatedGellyVertices =
      gellyGraph.runVertexCentricIteration(new SCCComputeFunction(), null, maxIteration,
        parameters).getVertices();

    // Annotate GradoopGraph vertices
    DataSet<EPGMVertex> annotatedEPGMVertices = currentGraph.getVertices()
      .join(annotatedGellyVertices)
      .where(new Id<>()).equalTo(0)
      .with(new SccGellyVertexWithGradoopVertexJoin(propertyKey));

    return currentGraph.getConfig().getLogicalGraphFactory().fromDataSets(
      currentGraph.getGraphHead(), annotatedEPGMVertices, currentGraph.getEdges());
  }
}
