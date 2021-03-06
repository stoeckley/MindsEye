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

import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.lang.cudnn.Precision;
import com.simiacryptus.mindseye.test.unit.SingleDerivativeTester;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * The type Product key apply.
 */
public abstract class GateBiasLayerTest extends CudaLayerTestBase {

  /**
   * The Precision.
   */
  final Precision precision;

  /**
   * Instantiates a new Product key apply.
   *
   * @param precision the precision
   */
  public GateBiasLayerTest(final Precision precision) {
    this.precision = precision;
  }

  @Nonnull
  @Override
  public int[][] getSmallDims(Random random) {
    return new int[][]{
        {4, 4, 3}, {1, 1, 3}
    };
  }

  @Override
  public int[][] getLargeDims(final Random random) {
    return new int[][]{
        {1200, 1200, 3}, {1, 1, 3}
    };
  }

  @Nonnull
  @Override
  public Layer getLayer(final int[][] inputSize, Random random) {
    return new GateBiasLayer().setPrecision(precision);
  }

  /**
   * Multiplication of 2 inputs using 64-bit precision
   */
  public static class Double extends GateBiasLayerTest {
    /**
     * Instantiates a new Double.
     */
    public Double() {
      super(Precision.Double);
    }
  }

  /**
   * Multiplication of 2 inputs using 32-bit precision
   */
  public static class Float extends GateBiasLayerTest {
    /**
     * Instantiates a new Float.
     */
    public Float() {
      super(Precision.Float);
    }

    @Override
    public SingleDerivativeTester getDerivativeTester() {
      return new SingleDerivativeTester(1e-2, 1e-3);
    }

  }
}
