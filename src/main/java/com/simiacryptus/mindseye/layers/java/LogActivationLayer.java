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

/**
 * The type Log activation layer.
 */
public final class LogActivationLayer extends SimpleActivationLayer<LogActivationLayer> {
  
  /**
   * Instantiates a new Log activation layer.
   *
   * @param id the id
   */
  protected LogActivationLayer(JsonObject id) {
    super(id);
  }
  
  /**
   * Instantiates a new Log activation layer.
   */
  public LogActivationLayer() {
  }
  
  /**
   * From json log activation layer.
   *
   * @param json the json
   * @return the log activation layer
   */
  public static LogActivationLayer fromJson(JsonObject json) {
    return new LogActivationLayer(json);
  }
  
  public JsonObject getJson() {
    return super.getJsonStub();
  }
  
  @Override
  protected final void eval(final double x, final double[] results) {
    final double minDeriv = 0;
    final double d = 0 == x ? Double.NaN : 1 / x;
    final double f = 0 == x ? Double.NEGATIVE_INFINITY : Math.log(Math.abs(x));
    assert Double.isFinite(d);
    assert minDeriv <= Math.abs(d);
    results[0] = f;
    results[1] = d;
  }
  
}