/*
 * Copyright (c) 2018 by Andrew Charneski.
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
import com.simiacryptus.mindseye.lang.DataSerializer;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Calculates y=sin(x) in radians apply signed unit amplitude.
 */
@SuppressWarnings("serial")
public final class SinewaveActivationLayer extends SimpleActivationLayer<SinewaveActivationLayer> {

  private boolean balanced = true;

  /**
   * Instantiates a new Sinewave activation key.
   */
  public SinewaveActivationLayer() {
  }

  /**
   * Instantiates a new Sinewave activation key.
   *
   * @param id the id
   */
  protected SinewaveActivationLayer(@Nonnull final JsonObject id) {
    super(id);
    balanced = id.get("balanced").getAsBoolean();
  }

  /**
   * From json sinewave activation key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the sinewave activation key
   */
  public static SinewaveActivationLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new SinewaveActivationLayer(json);
  }

  @Override
  protected final void eval(final double x, final double[] results) {
    double d = Math.cos(x);
    double f = Math.sin(x);
    if (!isBalanced()) {
      d = d / 2;
      f = (f + 1) / 2;
    }
    results[0] = f;
    results[1] = d;
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.addProperty("balanced", balanced);
    return json;
  }

  /**
   * Is balanced boolean.
   *
   * @return the boolean
   */
  public boolean isBalanced() {
    return balanced;
  }

  /**
   * Sets balanced.
   *
   * @param balanced the balanced
   * @return the balanced
   */
  @Nonnull
  public SinewaveActivationLayer setBalanced(final boolean balanced) {
    this.balanced = balanced;
    return this;
  }
}
