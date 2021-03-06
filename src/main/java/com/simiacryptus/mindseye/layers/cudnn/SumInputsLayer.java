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
import com.simiacryptus.mindseye.lang.cudnn.CudaSystem;
import com.simiacryptus.mindseye.lang.cudnn.MultiPrecision;
import com.simiacryptus.mindseye.lang.cudnn.Precision;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Computes a weighted binary sum of two layers. Provides two weighting coefficients, one for each input. This can be
 * used to implement a summation key, a difference key, a scaling key, or any combination.
 */
@SuppressWarnings("serial")
public class SumInputsLayer extends LayerBase implements MultiPrecision<SumInputsLayer> {

  private Precision precision = Precision.Double;
  private boolean parallel = true;

  /**
   * Instantiates a new Product inputs key.
   */
  public SumInputsLayer() {
    super();
  }

  /**
   * Instantiates a new Product inputs key.
   *
   * @param json the id
   */
  protected SumInputsLayer(@Nonnull final JsonObject json) {
    super(json);
    precision = Precision.valueOf(json.get("precision").getAsString());
    setParallel(json.get("parallel").getAsBoolean());
  }

  /**
   * From json product inputs key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the product inputs key
   */
  public static SumInputsLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new SumInputsLayer(json);
  }

  /**
   * Gets compatibility key.
   *
   * @return the compatibility key
   */
  @Nonnull
  public Layer getCompatibilityLayer() {
    return new com.simiacryptus.mindseye.layers.java.SumInputsLayer();

  }

  @Nullable
  @Override
  public Result evalAndFree(@Nonnull final Result... inObj) {
    @Nonnull final int[] dimensions = inObj[0].getData().getDimensions();
    if (3 != dimensions.length) {
      throw new IllegalArgumentException("dimensions=" + Arrays.toString(dimensions));
    }
    for (int i = 1; i < inObj.length; i++) {
      if (Tensor.length(dimensions) != Tensor.length(inObj[i].getData().getDimensions())) {
        throw new IllegalArgumentException(Arrays.toString(dimensions) + " != " + Arrays.toString(inObj[i].getData().getDimensions()));
      }
    }
    if (!CudaSystem.isEnabled()) return getCompatibilityLayer().evalAndFree(inObj);
    Stream<TensorList> tensorListStream = Arrays.stream(inObj).map(x -> x.getData());
    if (!CoreSettings.INSTANCE().isSingleThreaded() && parallel) tensorListStream = tensorListStream.parallel();
    return new Result(tensorListStream.reduce((leftData, rightData) -> CudaSystem.run(gpu -> {
      return gpu.addAndFree(precision, leftData, rightData);
    }, leftData, rightData)).get(), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList delta) -> {
      @Nonnull Stream<Result> deltaStream = Arrays.stream(inObj);
      if (!CoreSettings.INSTANCE().isSingleThreaded() && parallel) deltaStream = deltaStream.parallel();
      deltaStream.filter(Result::isAlive).forEach(obj -> {
        delta.addRef();
        obj.accumulate(buffer, delta);
      });
    }) {

      @Override
      protected void _free() {
        Arrays.stream(inObj).forEach(x -> x.freeRef());
      }


      @Override
      public boolean isAlive() {
        for (@Nonnull final Result element : inObj)
          if (element.isAlive()) {
            return true;
          }
        return false;
      }

    };
  }

  @Nonnull
  @Override
  public JsonObject getJson(Map<CharSequence, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJsonStub();
    json.addProperty("precision", precision.name());
    json.addProperty("parallel", isParallel());
    return json;
  }

  @Override
  public Precision getPrecision() {
    return precision;
  }

  @Nonnull
  @Override
  public SumInputsLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }


  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }

  /**
   * Is parallel boolean.
   *
   * @return the boolean
   */
  public boolean isParallel() {
    return parallel;
  }

  /**
   * Sets parallel.
   *
   * @param parallel the parallel
   * @return the parallel
   */
  public SumInputsLayer setParallel(boolean parallel) {
    this.parallel = parallel;
    return this;
  }

}
