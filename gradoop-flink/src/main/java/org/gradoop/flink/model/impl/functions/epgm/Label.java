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
package org.gradoop.flink.model.impl.functions.epgm;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.functions.FunctionAnnotation;
import org.apache.flink.api.java.functions.KeySelector;
import org.gradoop.common.model.api.entities.Labeled;

/**
 * labeled element => label
 *
 * @param <L> labeled type
 */
@FunctionAnnotation.ForwardedFields("label->*")
public class Label<L extends Labeled>
  implements MapFunction<L, String>, KeySelector<L, String> {

  @Override
  public String map(L l) throws Exception {
    return l.getLabel();
  }

  @Override
  public String getKey(L l) {
    return l.getLabel();
  }
}
