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
import java.util.function.Function;
import java.util.function.IntToDoubleFunction;
import java.util.stream.IntStream;

/**
 * Scales the input using per-color-band coefficients
 */
@SuppressWarnings("serial")
public class ImgBandScaleLayer extends LayerBase {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(ImgBandScaleLayer.class);
  @Nullable
  private final double[] weights;

  /**
   * Instantiates a new Img band scale key.
   */
  protected ImgBandScaleLayer() {
    super();
    weights = null;
  }

  /**
   * Instantiates a new Img band scale key.
   *
   * @param bands the bands
   */
  public ImgBandScaleLayer(final double... bands) {
    super();
    weights = bands;
  }


  /**
   * Instantiates a new Img band scale key.
   *
   * @param json the json
   */
  protected ImgBandScaleLayer(@Nonnull final JsonObject json) {
    super(json);
    weights = JsonUtil.getDoubleArray(json.getAsJsonArray("bias"));
  }

  /**
   * From json img band scale key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the img band scale key
   */
  public static ImgBandScaleLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new ImgBandScaleLayer(json);
  }

  /**
   * Add weights img band scale key.
   *
   * @param f the f
   * @return the img band scale key
   */
  @Nonnull
  public ImgBandScaleLayer addWeights(@Nonnull final DoubleSupplier f) {
    Util.add(f, getWeights());
    return this;
  }

  @Nonnull
  @Override
  public Result eval(final Result... inObj) {
    return eval(inObj[0]);
  }

  /**
   * Eval nn result.
   *
   * @param input the input
   * @return the nn result
   */
  @Nonnull
  public Result eval(@Nonnull final Result input) {
    @Nullable final double[] weights = getWeights();
    final TensorList inData = input.getData();
    inData.addRef();
    input.addRef();
    @Nullable Function<Tensor, Tensor> tensorTensorFunction = tensor -> {
      if (tensor.getDimensions().length != 3) {
        throw new IllegalArgumentException(Arrays.toString(tensor.getDimensions()));
      }
      if (tensor.getDimensions()[2] != weights.length) {
        throw new IllegalArgumentException(String.format("%s: %s does not have %s bands",
            getName(), Arrays.toString(tensor.getDimensions()), weights.length));
      }
      @Nullable Tensor tensor1 = tensor.mapCoords(c -> tensor.get(c) * weights[c.getCoords()[2]]);
      tensor.freeRef();
      return tensor1;
    };
    Tensor[] data = inData.stream().parallel().map(tensorTensorFunction).toArray(i -> new Tensor[i]);
    return new Result(TensorArray.wrap(data), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList delta) -> {
      if (!isFrozen()) {
        final Delta<UUID> deltaBuffer = buffer.get(ImgBandScaleLayer.this.getId(), weights);
        IntStream.range(0, delta.length()).forEach(index -> {
          @Nonnull int[] dimensions = delta.getDimensions();
          int z = dimensions[2];
          int y = dimensions[1];
          int x = dimensions[0];
          final double[] array = RecycleBin.DOUBLES.obtain(z);
          Tensor deltaTensor = delta.get(index);
          @Nullable final double[] deltaArray = deltaTensor.getData();
          Tensor inputTensor = inData.get(index);
          @Nullable final double[] inputData = inputTensor.getData();
          for (int i = 0; i < z; i++) {
            for (int j = 0; j < y * x; j++) {
              //array[i] += deltaArray[i + z * j];
              array[i] += deltaArray[i * x * y + j] * inputData[i * x * y + j];
            }
          }
          inputTensor.freeRef();
          deltaTensor.freeRef();
          assert Arrays.stream(array).allMatch(v -> Double.isFinite(v));
          deltaBuffer.addInPlace(array);
          RecycleBin.DOUBLES.recycle(array, array.length);
        });
        deltaBuffer.freeRef();
      }
      if (input.isAlive()) {
        Tensor[] tensors = delta.stream().map(t -> {
          @Nullable Tensor tensor = t.mapCoords((c) -> t.get(c) * weights[c.getCoords()[2]]);
          t.freeRef();
          return tensor;
        }).toArray(i -> new Tensor[i]);
        @Nonnull TensorArray tensorArray = TensorArray.wrap(tensors);
        input.accumulate(buffer, tensorArray);
      }
    }) {

      @Override
      protected void _free() {
        inData.freeRef();
        input.freeRef();
      }


      @Override
      public boolean isAlive() {
        return input.isAlive() || !isFrozen();
      }
    };
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.add("bias", JsonUtil.getJson(getWeights()));
    return json;
  }

  /**
   * Get wieghts double [ ].
   *
   * @return the double [ ]
   */
  @Nullable
  public double[] getWeights() {
    if (!Arrays.stream(weights).allMatch(v -> Double.isFinite(v))) {
      throw new IllegalStateException(Arrays.toString(weights));
    }
    return weights;
  }

  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  @Nonnull
  public ImgBandScaleLayer setWeights(@Nonnull final IntToDoubleFunction f) {
    @Nullable final double[] bias = getWeights();
    for (int i = 0; i < bias.length; i++) {
      bias[i] = f.applyAsDouble(i);
    }
    assert Arrays.stream(bias).allMatch(v -> Double.isFinite(v));
    return this;
  }

  /**
   * Set nn key.
   *
   * @param ds the ds
   * @return the nn key
   */
  @Nonnull
  public Layer set(@Nonnull final double[] ds) {
    @Nullable final double[] bias = getWeights();
    for (int i = 0; i < ds.length; i++) {
      bias[i] = ds[i];
    }
    assert Arrays.stream(bias).allMatch(v -> Double.isFinite(v));
    return this;
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList(getWeights());
  }
}
