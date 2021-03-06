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
import com.simiacryptus.mindseye.layers.LayerTestBase;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * The type Mean sq loss key apply.
 */
public abstract class MeanSqLossLayerTest extends LayerTestBase {

  @Nonnull
  @Override
  public int[][] getSmallDims(Random random) {
    return new int[][]{
        {8, 8, 1}, {8, 8, 1}
    };
  }

  @Nonnull
  @Override
  public Layer getLayer(final int[][] inputSize, Random random) {
    return new MeanSqLossLayer();
  }

  @Override
  public Class<? extends Layer> getReferenceLayerClass() {
    return com.simiacryptus.mindseye.layers.java.MeanSqLossLayer.class;
  }

  @Nonnull
  @Override
  public int[][] getLargeDims(Random random) {
    return new int[][]{
        {1200, 1200, 3}, {1200, 1200, 3}
    };
  }

  /**
   * Basic apply.
   */
  public class Basic extends MeanSqLossLayerTest {

  }

  /**
   * Test using asymmetric input.
   */
  public class Asymetric extends MeanSqLossLayerTest {

    @Nonnull
    @Override
    public int[][] getSmallDims(Random random) {
      return new int[][]{
          {2, 3, 1}, {2, 3, 1}
      };
    }

    @Nonnull
    @Override
    public int[][] getLargeDims(Random random) {
      return new int[][]{
          {200, 300, 100}, {200, 300, 100}
      };
    }
  }

}
