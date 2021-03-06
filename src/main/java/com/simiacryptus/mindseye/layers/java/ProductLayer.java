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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * The type Product key.
 */
@SuppressWarnings("serial")
public class ProductLayer extends LayerBase {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(ProductLayer.class);

  /**
   * Instantiates a new Product key.
   */
  public ProductLayer() {
  }

  /**
   * Instantiates a new Product key.
   *
   * @param id the id
   */
  protected ProductLayer(@Nonnull final JsonObject id) {
    super(id);
  }

  /**
   * From json product key.
   *
   * @param json the json
   * @param rs   the rs
   * @return the product key
   */
  public static ProductLayer fromJson(@Nonnull final JsonObject json, Map<CharSequence, byte[]> rs) {
    return new ProductLayer(json);
  }

  @Nonnull
  @Override
  public Result eval(@Nonnull final Result... inObj) {
    Arrays.stream(inObj).forEach(nnResult -> nnResult.addRef());
    Arrays.stream(inObj).forEach(x -> x.getData().addRef());
    final Result in0 = inObj[0];
    @Nonnull final double[] sum_A = new double[in0.getData().length()];
    final Tensor[] outputA = IntStream.range(0, in0.getData().length()).mapToObj(dataIndex -> {
      double sum = 1;
      for (@Nonnull final Result element : inObj) {
        Tensor tensor = element.getData().get(dataIndex);
        @Nullable final double[] input = tensor.getData();
        for (final double element2 : input) {
          sum *= element2;
        }
        tensor.freeRef();
      }
      sum_A[dataIndex] = sum;
      return new Tensor(new double[]{sum}, 1);
    }).toArray(i -> new Tensor[i]);
    return new Result(TensorArray.wrap(outputA), (@Nonnull final DeltaSet<UUID> buffer, @Nonnull final TensorList delta) -> {
      for (@Nonnull final Result in_l : inObj) {
        if (in_l.isAlive()) {
          @Nonnull TensorArray tensorArray = TensorArray.wrap(IntStream.range(0, delta.length()).mapToObj(dataIndex -> {
            Tensor dataTensor = delta.get(dataIndex);
            Tensor lTensor = in_l.getData().get(dataIndex);
            @Nonnull final Tensor passback = new Tensor(lTensor.getDimensions());
            for (int i = 0; i < lTensor.length(); i++) {
              passback.set(i, dataTensor.get(0) * sum_A[dataIndex] / lTensor.getData()[i]);
            }
            dataTensor.freeRef();
            lTensor.freeRef();
            return passback;
          }).toArray(i -> new Tensor[i]));
          in_l.accumulate(buffer, tensorArray);
        }
      }
    }) {

      @Override
      protected void _free() {
        Arrays.stream(inObj).forEach(nnResult -> nnResult.freeRef());
        Arrays.stream(inObj).forEach(x -> x.getData().freeRef());
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
    return super.getJsonStub();
  }

  @Nonnull
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
}
