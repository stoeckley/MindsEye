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

package com.simiacryptus.mindseye.layers.cudnn.conv;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.lang.cudnn.CudaSystem;
import com.simiacryptus.mindseye.lang.cudnn.MultiPrecision;
import com.simiacryptus.mindseye.lang.cudnn.Precision;
import com.simiacryptus.mindseye.layers.Explodable;
import com.simiacryptus.mindseye.layers.java.FullyConnectedReferenceLayer;
import com.simiacryptus.mindseye.layers.java.ReshapeLayer;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.util.FastRandom;
import com.simiacryptus.util.JsonUtil;
import com.simiacryptus.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * A dense matrix operator using vector-matrix multiplication. Represents a fully connected key of synapses, where all
 * inputs are connected to all outputs via seperate coefficients.
 */
@SuppressWarnings("serial")
public class FullyConnectedLayer extends LayerBase implements MultiPrecision<FullyConnectedLayer>, Explodable {
  private static final Logger log = LoggerFactory.getLogger(FullyConnectedLayer.class);
  /**
   * The Input dims.
   */
  @Nullable
  public final int[] inputDims;
  /**
   * The Output dims.
   */
  @Nullable
  public final int[] outputDims;
  @Nullable
  private final Tensor weights;

  private Precision precision = Precision.Double;
  private int batchBands = 0;

  /**
   * Instantiates a new Img eval key.
   */
  private FullyConnectedLayer() {
    outputDims = null;
    weights = null;
    inputDims = null;
  }

  /**
   * Instantiates a new Fully connected key.
   *
   * @param inputDims  the input dims
   * @param outputDims the output dims
   */
  public FullyConnectedLayer(@Nonnull final int[] inputDims, @Nonnull final int[] outputDims) {
    final int inputs = Tensor.length(inputDims);
    this.inputDims = Arrays.copyOf(inputDims, inputDims.length);
    this.outputDims = Arrays.copyOf(outputDims, outputDims.length);
    final int outs = Tensor.length(outputDims);
    weights = new Tensor(inputs, outs);
    setWeights(() -> {
      final double ratio = Math.sqrt(6. / (inputs + outs + 1));
      final double fate = Util.R.get().nextDouble();
      final double v = (1 - 2 * fate) * ratio;
      return v;
    });
  }

  /**
   * Instantiates a new Img eval key.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected FullyConnectedLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    super(json);
    outputDims = JsonUtil.getIntArray(json.getAsJsonArray("outputDims"));
    inputDims = JsonUtil.getIntArray(json.getAsJsonArray("inputDims"));
    @Nullable final Tensor data = Tensor.fromJson(json.get("weights"), rs);
    weights = data;
    this.precision = Precision.valueOf(json.getAsJsonPrimitive("precision").getAsString());
  }

  /**
   * From json img eval key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the img eval key
   */
  public static FullyConnectedLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new FullyConnectedLayer(json, rs);
  }

  /**
   * Sets weights.
   *
   * @param data the data
   * @return the weights
   */
  @Nonnull
  public FullyConnectedLayer set(final double[] data) {
    weights.set(data);
    return this;
  }

  /**
   * Set fully connected key.
   *
   * @param data the data
   * @return the fully connected key
   */
  @Nonnull
  public FullyConnectedLayer set(@Nonnull final Tensor data) {
    weights.set(data);
    return this;
  }

  /**
   * Sets weights log.
   *
   * @param value the value
   * @return the weights log
   */
  @Nonnull
  public FullyConnectedLayer setWeightsLog(final double value) {
    getWeights().setByCoord(c -> (FastRandom.INSTANCE.random() - 0.5) * Math.pow(10, value));
    return this;
  }

  /**
   * Gets compatibility key.
   *
   * @return the compatibility key
   */
  @Nonnull
  public Layer getCompatibilityLayer() {
    return new FullyConnectedReferenceLayer(inputDims, outputDims).set(getWeights());
  }

  @Nullable
  @Override
  public Result evalAndFree(final Result... inObj) {
    if (!CudaSystem.isEnabled()) return getCompatibilityLayer().evalAndFree(inObj);
    Layer explode = explode();
    Result eval = explode.evalAndFree(inObj);
    explode.freeRef();
    return eval;
  }

  /**
   * Explode pipeline network.
   *
   * @return the pipeline network
   */
  @Nonnull
  public Layer explode() {
    int inputVol = Tensor.length(inputDims);
    int outVol = Tensor.length(outputDims);
    @Nonnull PipelineNetwork network = new PipelineNetwork(1);
    network.wrap(new ReshapeLayer(1, 1, inputVol)).freeRef();
    @Nullable Tensor tensor = this.weights.reshapeCast(1, 1, inputVol * outVol);
    @Nonnull ConvolutionLayer convolutionLayer = new ConvolutionLayer(1, 1, inputVol, outVol)
        .set(tensor)
        .setBatchBands(getBatchBands());
    @Nonnull ExplodedConvolutionGrid grid = convolutionLayer
        .getExplodedNetwork();
    convolutionLayer.freeRef();
    tensor.freeRef();
    grid.add(network.getHead());
    grid.freeRef();
    network.wrap(new ReshapeLayer(outputDims)).freeRef();
    network.setName(getName());
    return network;
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, @Nonnull DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.add("outputDims", JsonUtil.getJson(outputDims));
    json.add("inputDims", JsonUtil.getJson(inputDims));
    @Nullable Tensor tensor = getWeights();
    json.add("weights", tensor.toJson(resources, dataSerializer));
    json.addProperty("precision", precision.name());
    return json;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList(getWeights().getData());
  }

  @Override
  public Precision getPrecision() {
    return precision;
  }

  @Nonnull
  @Override
  public FullyConnectedLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }

  /**
   * The Weights.
   *
   * @return the weights
   */
  @Nullable
  public Tensor getWeights() {
    return weights;
  }

  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  @Nonnull
  public FullyConnectedLayer setWeights(@Nonnull final DoubleSupplier f) {
    Arrays.parallelSetAll(getWeights().getData(), i -> f.getAsDouble());
    return this;
  }

  /**
   * Gets batch bands.
   *
   * @return the batch bands
   */
  public int getBatchBands() {
    return batchBands;
  }

  /**
   * Sets batch bands.
   *
   * @param batchBands the batch bands
   * @return the batch bands
   */
  @Nonnull
  public FullyConnectedLayer setBatchBands(int batchBands) {
    this.batchBands = batchBands;
    return this;
  }
}
