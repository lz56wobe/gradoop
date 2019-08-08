package org.gradoop.flink.algorithms.gelly.stronglyconnectedcomponents;

import org.gradoop.flink.model.GradoopFlinkTestBase;
import org.gradoop.flink.model.impl.epgm.GraphCollection;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;

import org.gradoop.flink.util.FlinkAsciiGraphLoader;
import org.junit.Test;

public class AnnotateStronglyConnectedComponentsTest extends GradoopFlinkTestBase {

  private static final String propertyKey = "SCC";

  @Test
  public void testByElementIds() throws Exception {
    String graph = "input[" +
      // First component
      "(v0 {id:0, component:1})" +
      // Second component
      "(v1 {id:1, component:2})" +
      "(v2 {id:2, component:2})" +
      "(v3 {id:3, component:2})" +
      "(v4 {id:4, component:2})" +
      "(v5 {id:5, component:2})" +
      "(v6 {id:6, component:2})" +
      "(v1)-[e0]->(v2)" +
      "(v2)-[e1]->(v1)" +
      "(v1)-[e2]->(v3)" +
      "(v3)-[e3]->(v1)" +
      "(v2)-[e4]->(v3)" +
      "(v3)-[e5]->(v2)" +
      "(v3)-[e6]->(v4)" +
      "(v4)-[e7]->(v5)" +
      "(v5)-[e8]->(v4)" +
      "(v5)-[e9]->(v6)" +
      "(v6)-[e10]->(v5)" +
      "(v6)-[e11]->(v2)" +
      // Third component
      "(v7 {id:7, component:3})" +
      "(v8 {id:8, component:3})" +
      "(v6)-[e12]->(v7)" +
      "(v7)-[e13]->(v8)" +
      "(v8)-[e14]->(v7)" +
      // Fourth component
      "(v9 {id:9, component:4})" +
      // Fifth component
      "(v10 {id:10, component:5})" +
      "(v9)-[e15]->(v10)" +
      // Sixt component
      "(v11 {id:11, component:6})" +
      "(v11)-[e16]->(v3)" +
      // Seventh component
      "(v12 {id:12, component:7})" +
      "(v12)-[e17]->(v11)" +
      // Eight component
      "(v13 {id:13, component:8})" +
      "(v13)-[e18]->(v11)" +
      // Ninth component
      "(v14 {id:14, component:9})" +
      "(v14)-[e19]->(v13)" +
      // Tenth component
      "(v15 {id:15, component:10})" +
      "(v14)-[e20]->(v15)" +
      "]";

    FlinkAsciiGraphLoader loader = getLoaderFromString(graph);
    LogicalGraph input = loader.getLogicalGraphByVariable("input");

    LogicalGraph result = input.callForGraph(
      new AnnotateStronglyConnectedComponents(propertyKey, 10));

    GraphCollection components = input.splitBy("component");
    GraphCollection SCCs = result.splitBy("SCC");

    collectAndAssertTrue(SCCs.equalsByGraphElementIds(components));
  }
}
