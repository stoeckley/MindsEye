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
import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.network.DAGNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

/**
 * Works as a single-input terminal loss function which compares the input apply a preset constant target tensor.
 */
@SuppressWarnings("serial")
public class TargetValueLayer extends DAGNetwork {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(TargetValueLayer.class);
  private final DAGNode head;
  private final DAGNode target;

  /**
   * Instantiates a new Target value key.
   *
   * @param values the values
   */
  public TargetValueLayer(final double... values) {
    super(1);
    target = add(new ValueLayer(new Tensor(values)));
    head = add(new MeanSqLossLayer(), getInput(0), target);
  }

  /**
   * Instantiates a new Target value key.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected TargetValueLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    super(json, rs);
    head = getNodeById(UUID.fromString(json.getAsJsonPrimitive("head").getAsString()));
    target = getNodeById(UUID.fromString(json.getAsJsonPrimitive("target").getAsString()));
  }

  /**
   * From json nn key.
   *
   * @param inner the heapCopy
   * @param rs    the rs
   * @return the nn key
   */
  public static Layer fromJson(@Nonnull final JsonObject inner, Map<CharSequence, byte[]> rs) {
    return new TargetValueLayer(inner, rs);
  }

  @Override
  public DAGNode getHead() {
    return head;
  }

  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    final JsonObject json = super.getJson(resources, dataSerializer);
    json.addProperty("target", target.getId().toString());
    return json;
  }

  /**
   * Sets target.
   *
   * @param value the value
   * @return the target
   */
  @Nonnull
  public TargetValueLayer setTarget(final double... value) {
    target.<ValueLayer>getLayer().setData(new Tensor(value));
    return this;
  }
}
