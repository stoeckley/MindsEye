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

package com.simiacryptus.mindseye.layers.aparapi;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.layers.LayerTestBase;

import java.util.Random;

/**
 * The type Convolution layer run.
 */
public abstract class ConvolutionLayerTest extends LayerTestBase {
  
  /**
   * Basic 3x3 convolution with 2 color bands
   */
  public static class Basic extends ConvolutionLayerTest {
  
    private final int inputBands = 1;
    private final int outputBands = 1;
  
    @Override
    public NNLayer getLayer(final int[][] inputSize, Random random) {
      return new ConvolutionLayer(3, 3, inputBands, outputBands, true).setWeights(this::random);
    }
    
    @Override
    public int[][] getInputDims(Random random) {
      return new int[][]{
        {8, 8, 1}
      };
    }
    
    @Override
    public int[][] getPerfDims(Random random) {
      
      return new int[][]{
        {200, 200, inputBands}
      };
    }
    
  }
  
  
  /**
   * Reducing the number of bands (output less data than input)
   */
  public static class Downsize extends ConvolutionLayerTest {
  
    @Override
    public int[][] getInputDims(Random random) {
      return new int[][]{
        {3, 3, 7}
      };
    }
  
    @Override
    public NNLayer getLayer(final int[][] inputSize, Random random) {
      return new ConvolutionLayer(3, 3, 7, 3, false).setWeights(this::random);
    }
  
  }
  
  /**
   * Increasing the number of bands (output more data than input)
   */
  public static class Upsize extends ConvolutionLayerTest {
  
    @Override
    public int[][] getInputDims(Random random) {
      return new int[][]{
        {3, 3, 2}
      };
    }
    
    @Override
    public NNLayer getLayer(final int[][] inputSize, Random random) {
      return new ConvolutionLayer(3, 3, 2, 3, false).setWeights(this::random);
    }
  
  }
}
