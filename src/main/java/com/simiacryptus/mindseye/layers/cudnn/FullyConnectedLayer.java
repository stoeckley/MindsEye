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

package com.simiacryptus.mindseye.layers.cudnn;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.layers.java.ReshapeLayer;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.util.FastRandom;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.stream.IntStream;

/**
 * A dense matrix operator using vector-matrix multiplication. Represents a fully connected layer of synapses, where all
 * inputs are connected to all outputs via seperate coefficients.
 */
@SuppressWarnings("serial")
public class FullyConnectedLayer extends NNLayer implements LayerPrecision<FullyConnectedLayer> {
  private static final Logger log = LoggerFactory.getLogger(FullyConnectedLayer.class);
  /**
   * The Input dims.
   */
  public final int[] inputDims;
  /**
   * The Output dims.
   */
  public final int[] outputDims;
  private final Tensor weights;
  
  private Precision precision = Precision.Double;
  public static boolean invert = false;
  
  /**
   * Instantiates a new Img concat layer.
   */
  private FullyConnectedLayer() {
    outputDims = null;
    weights = null;
    inputDims = null;
  }
  
  /**
   * Instantiates a new Fully connected layer.
   *
   * @param inputDims  the input dims
   * @param outputDims the output dims
   */
  public FullyConnectedLayer(final int[] inputDims, final int[] outputDims) {
    final int inputs = Tensor.dim(inputDims);
    this.inputDims = Arrays.copyOf(inputDims, inputDims.length);
    this.outputDims = Arrays.copyOf(outputDims, outputDims.length);
    final int outs = Tensor.dim(outputDims);
    weights = new Tensor(inputs, outs);
    setWeights(() -> {
      final double ratio = Math.sqrt(6. / (inputs + outs + 1));
      final double fate = Util.R.get().nextDouble();
      final double v = (1 - 2 * fate) * ratio;
      return v;
    });
  }
  
  /**
   * Instantiates a new Img concat layer.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected FullyConnectedLayer(final JsonObject json, Map<String, byte[]> rs) {
    super(json);
    outputDims = JsonUtil.getIntArray(json.getAsJsonArray("outputDims"));
    inputDims = JsonUtil.getIntArray(json.getAsJsonArray("inputDims"));
    final Tensor data = Tensor.fromJson(json.get("weights"), rs);
    assert !invert || isInvertedSchema(data);
    weights = invert ? invertSchema(data) : data;
    this.precision = Precision.valueOf(json.getAsJsonPrimitive("precision").getAsString());
  }
  
  /**
   * From json img concat layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the img concat layer
   */
  public static FullyConnectedLayer fromJson(final JsonObject json, Map<String, byte[]> rs) {
    return new FullyConnectedLayer(json, rs);
  }
  
  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  public FullyConnectedLayer setWeights(final DoubleSupplier f) {
    Arrays.parallelSetAll(getWeights().getData(), i -> f.getAsDouble());
    return this;
  }
  
  public Tensor getInvertedWeights() {
    return invertSchema(getWeights());
  }
  
  /**
   * Set fully connected layer.
   *
   * @param data the data
   * @return the fully connected layer
   */
  public FullyConnectedLayer setInvertedWeights(final Tensor data) {
    assert isInvertedSchema(data);
    getWeights().set(invertSchema(data));
    return this;
  }
  
  public Tensor invertSchema(Tensor data) {
    int[] key = IntStream.range(0, data.getDimensions().length).map(x -> -x).sorted().map(x -> -x).toArray();
    Tensor tensor = data.permuteDimensions(key);
    return tensor;
  }
  
  public boolean isInvertedSchema(Tensor data) {
    assert (inputDims.length + outputDims.length) == data.getDimensions().length;
    int i = 0;
    for (; i < outputDims.length; i++) {
      if (outputDims[outputDims.length - (i + 1)] != data.getDimensions()[i]) return false;
    }
    for (; i < inputDims.length + outputDims.length; i++)
      if (inputDims[inputDims.length - (i - outputDims.length + 1)] != data.getDimensions()[i])
        return false;
    return true;
  }
  
  /**
   * Sets weights log.
   *
   * @param value the value
   * @return the weights log
   */
  public FullyConnectedLayer setWeightsLog(final double value) {
    getWeights().coordStream(true).parallel().forEach(c -> {
      getWeights().set(c, (FastRandom.random() - 0.5) * Math.pow(10, value));
    });
    return this;
  }
  
  /**
   * Gets compatibility layer.
   *
   * @return the compatibility layer
   */
  public NNLayer getCompatibilityLayer() {
    return new com.simiacryptus.mindseye.layers.java.FullyConnectedLayer(inputDims, outputDims).set(getWeights());
  }
  
  @Override
  public NNResult eval(final NNExecutionContext nncontext, final NNResult... inObj) {
    if (((CudaExecutionContext) nncontext).getDeviceNumber() < 0) return getCompatibilityLayer().eval(nncontext, inObj);
    PipelineNetwork network = new PipelineNetwork(1);
    int inputVol = Tensor.dim(inputDims);
    int outVol = Tensor.dim(outputDims);
    network.add(new ReshapeLayer(1, 1, inputVol));
    network.add(new ConvolutionLayer(1, 1, inputVol, outVol) {
      public Tensor getKernel() {
        return FullyConnectedLayer.this.weights.reshapeCast(1, 1, inputVol * outVol);
      }
    });
    network.add(new ReshapeLayer(outputDims));
    return network.eval(nncontext, inObj);
  }
  
  @Override
  public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    final JsonObject json = super.getJsonStub();
    json.add("outputDims", JsonUtil.getJson(outputDims));
    json.add("inputDims", JsonUtil.getJson(inputDims));
    Tensor tensor = invert ? getInvertedWeights() : getWeights();
    json.add("weights", tensor.toJson(resources, dataSerializer));
    json.addProperty("precision", precision.name());
    return json;
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList(getWeights().getData());
  }
  
  @Override
  public Precision getPrecision() {
    return precision;
  }
  
  @Override
  public FullyConnectedLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }
  
  /**
   * The Weights.
   */
  public Tensor getWeights() {
    return weights;
  }
}
