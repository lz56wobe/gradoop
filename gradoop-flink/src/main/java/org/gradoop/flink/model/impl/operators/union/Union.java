/*
 * Copyright © 2014 - 2019 Leipzig University (Database Research Group)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradoop.flink.model.impl.operators.union;

import org.apache.flink.api.java.DataSet;
import org.gradoop.common.model.impl.pojo.EPGMGraphHead;
import org.gradoop.common.model.impl.pojo.EPGMVertex;
import org.gradoop.common.model.impl.pojo.EPGMEdge;
import org.gradoop.flink.model.impl.functions.epgm.Id;
import org.gradoop.flink.model.impl.operators.base.SetOperatorBase;

/**
 * Returns a collection with all logical graphs from two input collections.
 * Graph equality is based on their identifiers.
 */
public class Union extends SetOperatorBase {

  @Override
  protected DataSet<EPGMVertex> computeNewVertices(
    DataSet<EPGMGraphHead> newGraphHeads) {
    return firstCollection.getVertices()
      .union(secondCollection.getVertices())
      .distinct(new Id<EPGMVertex>());
  }

  @Override
  protected DataSet<EPGMGraphHead> computeNewGraphHeads() {
    return firstCollection.getGraphHeads()
      .union(secondCollection.getGraphHeads())
      .distinct(new Id<EPGMGraphHead>());
  }

  @Override
  protected DataSet<EPGMEdge> computeNewEdges(DataSet<EPGMVertex> newVertices) {
    return firstCollection.getEdges()
      .union(secondCollection.getEdges())
      .distinct(new Id<EPGMEdge>());
  }
}
