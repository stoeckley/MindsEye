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

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.layers.LayerTestBase;

import java.util.Random;

/**
 * The type Avg image band layer run.
 */
public abstract class AvgImageBandLayerTest extends LayerTestBase {
  
  @Override
  public int[][] getInputDims(Random random) {
    return new int[][]{
      {2, 2, 3}
    };
  }
  
  @Override
  public NNLayer getLayer(final int[][] inputSize, Random random) {
    return new AvgImageBandLayer();
  }
  
  @Override
  public int[][] getPerfDims(Random random) {
    return new int[][]{
      {200, 200, 3}
    };
  }
  
  /**
   * Basic Test
   */
  public static class Basic extends AvgImageBandLayerTest {
  }
  
}
