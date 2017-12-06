/*
 * Copyright (c) 2017 by Andrew Charneski.
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

package com.simiacryptus.mindseye.layers.cudnn.f32;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.layers.DerivativeTester;
import com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer;

/**
 * The type Convolution layer run.
 */
public class SimpleConvolutionLayerTest extends F32LayerTestBase {
  
  private final int radius;
  private final int bands;
  /**
   * The Layer.
   */
  SimpleConvolutionLayer layer;
  
  /**
   * Instantiates a new Simple convolution layer run.
   */
  public SimpleConvolutionLayerTest() {
    this(1, 1);
  }
  
  /**
   * Instantiates a new Simple convolution layer test.
   *
   * @param radius the radius
   * @param bands  the bands
   */
  protected SimpleConvolutionLayerTest(int radius, int bands) {
    this.radius = radius;
    this.bands = bands;
    layer = new SimpleConvolutionLayer(radius, radius, bands * bands);
    layer.filter.fill(() -> random());
  }
  
  @Override
  public NNLayer getLayer() {
    return layer;
  }
  
  @Override
  public NNLayer getReferenceLayer() {
    ConvolutionLayer convolutionLayer = new ConvolutionLayer(radius, radius, bands, bands, true);
    Tensor tensor = new Tensor(layer.filter.getDimensions());
    tensor.fillByCoord(c -> {
      int band = c.coords[2];
      int bandX = band % bands;
      int bandY = (band - bandX) / bands;
      assert band == bandX + bandY * bands;
      int bandT = bandY + bandX * bands;
      return layer.filter.get(c.coords[0], c.coords[1], bandT);
    });
    convolutionLayer.kernel.set(tensor);
    return convolutionLayer;
  }
  
  @Override
  public int[][] getInputDims() {
    return new int[][]{
      {radius, radius, bands}
    };
  }
  
  @Override
  public DerivativeTester getDerivativeTester() {
    return new DerivativeTester(1e-2, 1e-3);
  }
  
  /**
   * The type Multi band.
   */
  public static class MultiBand extends SimpleConvolutionLayerTest {
    /**
     * Instantiates a new Multi band.
     */
    public MultiBand() {
      super(1, 3);
    }
  }
  
  /**
   * The type Matrix.
   */
  public static class Matrix extends SimpleConvolutionLayerTest {
    /**
     * Instantiates a new Matrix.
     */
    public Matrix() {
      super(3, 1);
    }
  }
  
  /**
   * The type Image.
   */
  public static class Image extends SimpleConvolutionLayerTest {
    /**
     * Instantiates a new Image.
     */
    public Image() {
      super(3, 3);
    }
    
    @Override
    public int[][] getInputDims() {
      return new int[][]{
        {4, 4, 3}
      };
    }
    
  }
  
}
