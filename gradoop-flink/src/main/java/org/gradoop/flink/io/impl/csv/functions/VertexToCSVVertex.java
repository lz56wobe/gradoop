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
package org.gradoop.flink.io.impl.csv.functions;

import org.apache.flink.api.java.functions.FunctionAnnotation;
import org.gradoop.common.model.impl.pojo.EPGMVertex;
import org.gradoop.flink.io.api.metadata.MetaDataSource;
import org.gradoop.flink.io.impl.csv.CSVConstants;
import org.gradoop.flink.io.impl.csv.tuples.CSVVertex;

/**
 * Converts an {@link EPGMVertex} into a CSV representation.
 * <p>
 * Forwarded fields:
 * <p>
 * label
 */
@FunctionAnnotation.ForwardedFields("label->f2")
public class VertexToCSVVertex extends ElementToCSV<EPGMVertex, CSVVertex> {
  /**
   * Reduce object instantiations.
   */
  private final CSVVertex csvVertex = new CSVVertex();

  @Override
  public CSVVertex map(EPGMVertex vertex) throws Exception {
    csvVertex.setId(vertex.getId().toString());
    csvVertex.setGradoopIds(collectionToCsvString(vertex.getGraphIds()));
    csvVertex.setLabel(StringEscaper.escape(vertex.getLabel(), CSVConstants.ESCAPED_CHARACTERS));
    csvVertex.setProperties(getPropertyString(vertex, MetaDataSource.VERTEX_TYPE));
    return csvVertex;
  }
}
