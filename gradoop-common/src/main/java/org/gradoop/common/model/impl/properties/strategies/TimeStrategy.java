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
package org.gradoop.common.model.impl.properties.strategies;

import org.apache.flink.core.memory.DataInputView;
import org.apache.hadoop.hbase.util.Bytes;
import org.gradoop.common.model.impl.properties.DateTimeSerializer;
import org.gradoop.common.model.impl.properties.PropertyValue;
import org.gradoop.common.model.impl.properties.Type;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Arrays;

/**
 * Strategy class for handling {@code PropertyValue} operations with a value of the type
 * {@code LocalTime}.
 */
public class TimeStrategy extends AbstractFixSizedPropertyValueStrategy<LocalTime> {

  @Override
  public LocalTime read(DataInputView inputView, byte typeByte) throws IOException {
    int length = DateTimeSerializer.SIZEOF_TIME;
    byte[] rawBytes = new byte[length];

    for (int i  = 0; i < rawBytes.length; i++) {
      rawBytes[i] = inputView.readByte();
    }

    return DateTimeSerializer.deserializeTime(rawBytes);
  }

  @Override
  public int compare(LocalTime value, Object other) {
    if (other instanceof LocalTime) {
      return value.compareTo((LocalTime) other);
    }
    throw new IllegalArgumentException(String.format(
      "Incompatible types: %s, %s", value.getClass(), other.getClass()));
  }

  @Override
  public boolean is(Object value) {
    return value instanceof LocalTime;
  }

  @Override
  public Class<LocalTime> getType() {
    return LocalTime.class;
  }

  @Override
  public LocalTime get(byte[] bytes) {
    return DateTimeSerializer.deserializeTime(
      Arrays.copyOfRange(
        bytes, PropertyValue.OFFSET, DateTimeSerializer.SIZEOF_TIME + PropertyValue.OFFSET
      ));
  }

  @Override
  public byte getRawType() {
    return Type.TIME.getTypeByte();
  }

  @Override
  public byte[] getRawBytes(LocalTime value) {
    byte[] valueBytes = DateTimeSerializer.serializeTime(value);
    byte[] rawBytes = new byte[PropertyValue.OFFSET + DateTimeSerializer.SIZEOF_TIME];
    rawBytes[0] = getRawType();
    Bytes.putBytes(rawBytes, PropertyValue.OFFSET, valueBytes, 0, valueBytes.length);
    return rawBytes;
  }
}
