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
import com.simiacryptus.mindseye.lang.*;
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
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * A dense matrix operator using vector-matrix multiplication. Represents a fully connected key of synapses, where all
 * inputs are connected to all outputs via seperate coefficients.
 */
@SuppressWarnings("serial")
public class FullyConnectedReferenceLayer extends LayerBase {


  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(FullyConnectedReferenceLayer.class);
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
  /**
   * The Weights.
   */
  @Nullable
  public final Tensor weights;

  /**
   * Instantiates a new Fully connected key.
   */
  protected FullyConnectedReferenceLayer() {
    super();
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
  public FullyConnectedReferenceLayer(@Nonnull final int[] inputDims, @Nonnull final int[] outputDims) {
    this.inputDims = Arrays.copyOf(inputDims, inputDims.length);
    this.outputDims = Arrays.copyOf(outputDims, outputDims.length);
    final int inputs = Tensor.length(inputDims);
    final int outputs = Tensor.length(outputDims);
    weights = new Tensor(inputs, outputs);
    set(() -> {
      final double ratio = Math.sqrt(6. / (inputs + outputs + 1));
      final double fate = Util.R.get().nextDouble();
      final double v = (1 - 2 * fate) * ratio;
      return v;
    });
  }

  /**
   * Instantiates a new Fully connected key.
   *
   * @param json      the json
   * @param resources the resources
   */
  protected FullyConnectedReferenceLayer(@Nonnull final JsonObject json, Map<CharSequence, byte[]> resources) {
    super(json);
    outputDims = JsonUtil.getIntArray(json.getAsJsonArray("outputDims"));
    inputDims = JsonUtil.getIntArray(json.getAsJsonArray("inputDims"));
    weights = Tensor.fromJson(json.get("weights"), resources);
  }

  /**
   * From json fully connected key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the fully connected key
   */
  public static FullyConnectedReferenceLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new FullyConnectedReferenceLayer(json, rs);
  }

  @Override
  protected void _free() {
    weights.freeRef();
    super._free();
  }

  @Nonnull
  @Override
  public Result eval(final Result... inObj) {
    final Result inputResult = inObj[0];
    final TensorList indata = inputResult.getData();
    inputResult.addRef();
    indata.addRef();
    @Nonnull int[] inputDimensions = indata.getDimensions();
    assert Tensor.length(inputDimensions) == Tensor.length(this.inputDims) : Arrays.toString(inputDimensions) + " == " + Arrays.toString(this.inputDims);
    weights.addRef();
    return new Result(TensorArray.wrap(IntStream.range(0, indata.length()).mapToObj(index -> {
      @Nullable final Tensor input = indata.get(index);
      @Nullable final Tensor output = new Tensor(outputDims);
      weights.coordStream(false).forEach(c -> {
        int[] coords = c.getCoords();
        double prev = output.get(coords[1]);
        double w = weights.get(c);
        double i = input.get(coords[0]);
        double value = prev + w * i;
        output.set(coords[1], value);
      });
      input.freeRef();
      return output;
    }).toArray(i -> new Tensor[i])), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList delta) -> {
      if (!isFrozen()) {
        final Delta<UUID> deltaBuffer = buffer.get(FullyConnectedReferenceLayer.this.getId(), getWeights().getData());
        Tensor[] array = IntStream.range(0, indata.length()).mapToObj(i -> {
          @Nullable final Tensor inputTensor = indata.get(i);
          @Nullable final Tensor deltaTensor = delta.get(i);
          @Nonnull Tensor weights = new Tensor(FullyConnectedReferenceLayer.this.weights.getDimensions());
          weights.coordStream(false).forEach(c -> {
            int[] coords = c.getCoords();
            weights.set(c, inputTensor.get(coords[0]) * deltaTensor.get(coords[1]));
          });
          inputTensor.freeRef();
          deltaTensor.freeRef();
          return weights;
        }).toArray(i -> new Tensor[i]);
        Tensor tensor = Arrays.stream(array).reduce((a, b) -> {
          Tensor c = a.addAndFree(b);
          b.freeRef();
          return c;
        }).get();
        deltaBuffer.addInPlace(tensor.getData()).freeRef();
        tensor.freeRef();
      }
      if (inputResult.isAlive()) {
        @Nonnull final TensorList tensorList = TensorArray.wrap(IntStream.range(0, indata.length()).mapToObj(i -> {
          @Nullable final Tensor inputTensor = new Tensor(inputDims);
          @Nullable final Tensor deltaTensor = delta.get(i);
          weights.coordStream(false).forEach(c -> {
            int[] coords = c.getCoords();
            inputTensor.set(coords[0], inputTensor.get(coords[0]) + weights.get(c) * deltaTensor.get(coords[1]));
          });
          deltaTensor.freeRef();
          return inputTensor;
        }).toArray(i -> new Tensor[i]));
        inputResult.accumulate(buffer, tensorList);
      }
    }) {

      @Override
      protected void _free() {
        indata.freeRef();
        inputResult.freeRef();
        weights.freeRef();
      }

      @Override
      public boolean isAlive() {
        return inputResult.isAlive() || !isFrozen();
      }

    };
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, @Nonnull DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.add("outputDims", JsonUtil.getJson(outputDims));
    json.add("inputDims", JsonUtil.getJson(inputDims));
    json.add("weights", weights.toJson(resources, dataSerializer));
    return json;
  }


  /**
   * Gets weights.
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
  public FullyConnectedReferenceLayer set(@Nonnull final DoubleSupplier f) {
    Arrays.parallelSetAll(weights.getData(), i -> f.getAsDouble());
    return this;
  }

  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  @Nonnull
  public FullyConnectedReferenceLayer set(@Nonnull final IntToDoubleFunction f) {
    weights.set(f);
    return this;
  }

  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  @Nonnull
  public FullyConnectedReferenceLayer setByCoord(@Nonnull final ToDoubleFunction<Coordinate> f) {
    weights.coordStream(true).forEach(c -> {
      weights.set(c, f.applyAsDouble(c));
    });
    return this;
  }

  /**
   * Sets weights.
   *
   * @param data the data
   * @return the weights
   */
  @Nonnull
  public FullyConnectedReferenceLayer set(final double[] data) {
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
  public FullyConnectedReferenceLayer set(@Nonnull final Tensor data) {
    weights.set(data);
    return this;
  }

  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  @Nonnull
  public FullyConnectedReferenceLayer setByCoord(@Nonnull final ToDoubleBiFunction<Coordinate, Coordinate> f) {
    new Tensor(inputDims).coordStream(true).forEach(in -> {
      new Tensor(outputDims).coordStream(true).forEach(out -> {
        weights.set(new int[]{in.getIndex(), out.getIndex()}, f.applyAsDouble(in, out));
      });
    });
    return this;
  }

  /**
   * Sets weights log.
   *
   * @param value the value
   * @return the weights log
   */
  @Nonnull
  public FullyConnectedReferenceLayer setWeightsLog(final double value) {
    weights.coordStream(false).forEach(c -> {
      weights.set(c, (FastRandom.INSTANCE.random() - 0.5) * Math.pow(10, value));
    });
    return this;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList(getWeights().getData());
  }

}
