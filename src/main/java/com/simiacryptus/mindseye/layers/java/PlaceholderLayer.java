/*
 * Copyright (c) 2017 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.layers.java;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.NNExecutionContext;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.NNResult;

import java.util.List;

/**
 * The type Placeholder layer.
 *
 * @param <T> the type parameter
 */
public final class PlaceholderLayer<T> extends NNLayer {
  
  private final T key;
  
  /**
   * Instantiates a new Placeholder layer.
   *
   * @param key the key
   */
  public PlaceholderLayer(T key) {
    if (null == key) throw new UnsupportedOperationException();
    this.key = key;
    setName(getClass().getSimpleName() + "/" + getId());
  }
  
  @Override
  public NNResult eval(NNExecutionContext nncontext, NNResult[] array) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public JsonObject getJson() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public List<double[]> state() {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public Object getId() {
    return this.key;
  }
  
}
