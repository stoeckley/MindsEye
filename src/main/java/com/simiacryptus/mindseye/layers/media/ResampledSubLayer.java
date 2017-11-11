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

package com.simiacryptus.mindseye.layers.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.network.PipelineNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class ResampledSubLayer extends NNLayer {
  
  private final int scale;
  private final NNLayer subnetwork;
  
  public JsonObject getJson() {
    JsonObject json = super.getJsonStub();
    json.addProperty("scale", scale);
    json.add("subnetwork", subnetwork.getJson());
    return json;
  }
  
  public static ResampledSubLayer fromJson(JsonObject json) {
    return new ResampledSubLayer(json);
  }
  
  protected ResampledSubLayer(JsonObject json) {
    super(json);
    scale = json.getAsJsonPrimitive("scale").getAsInt();
    subnetwork = NNLayer.fromJson(json.getAsJsonObject("subnetwork"));
  }
  
  public ResampledSubLayer(int scale, NNLayer subnetwork) {
    super();
    this.scale = scale;
    this.subnetwork = subnetwork;
  }
  
  @Override
  public NNResult eval(NNExecutionContext nncontext, final NNResult... inObj) {
    assert (1 == inObj.length);
    final NNResult input = inObj[0];
    final TensorList batch = input.getData();
    final int[] inputDims = batch.get(0).getDimensions();
    assert (3 == inputDims.length);
  
  
    PipelineNetwork dynamicNetwork = new PipelineNetwork();
    
    
    
    
    
    
    return dynamicNetwork.eval(nncontext, inObj);
  }
  
  
  @Override
  public List<double[]> state() {
    return new ArrayList<>();
  }
  
  
}
