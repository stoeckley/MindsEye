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

package com.simiacryptus.mindseye.layers;

import com.simiacryptus.mindseye.layers.activation.SigmoidActivationLayer;
import com.simiacryptus.mindseye.layers.activation.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.layers.activation.SqActivationLayer;
import com.simiacryptus.mindseye.layers.loss.MeanSqLossLayer;
import com.simiacryptus.mindseye.layers.media.MaxSubsampleLayer;
import com.simiacryptus.mindseye.layers.reducers.ProductLayer;
import com.simiacryptus.mindseye.layers.reducers.SumInputsLayer;
import com.simiacryptus.mindseye.layers.reducers.SumReducerLayer;
import com.simiacryptus.mindseye.layers.synapse.*;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.ml.Tensor;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ComponentPerformanceTests {
  public static final double deltaFactor = 1e-6;
  
  private static final Logger log = LoggerFactory.getLogger(ComponentPerformanceTests.class);
  
  public static int test(final NNLayer component, final Tensor outputPrototype, final Tensor... inputPrototype) throws Throwable {
    long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60);
    int iterations = 0;
    while (timeout > System.currentTimeMillis()) {
      iterations++;
      NNResult eval = component.eval(inputPrototype);
      DeltaSet deltaSet = new DeltaSet();
      eval.accumulate(deltaSet, new Tensor[]{outputPrototype});
    }
    log.info(String.format("Iterations completed: %s", iterations));
    return iterations;
  }
  
  @Test
  public void testBiasLayer() throws Throwable {
    final Tensor outputPrototype = new Tensor(3);
    final Tensor inputPrototype = new Tensor(3).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new BiasLayer(outputPrototype.getDims()).setWeights(i -> Util.R.get().nextGaussian());
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testDenseSynapseLayer1() throws Throwable {
    final Tensor outputPrototype = new Tensor(2);
    final Tensor inputPrototype = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new JavaDenseSynapseLayer(inputPrototype.dim(), outputPrototype.getDims()).setWeights(() -> Util.R.get().nextGaussian());
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testToeplitzSynapseLayer1() throws Throwable {
    final Tensor inputPrototype = new Tensor(3, 3).fill(() -> Util.R.get().nextGaussian());
    final Tensor outputPrototype = new Tensor(3, 3);
    final NNLayer component = new ToeplitzSynapseLayer(inputPrototype.getDims(), outputPrototype.getDims()).setWeights(() -> Util.R.get().nextGaussian());
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testToeplitzSynapseLayer2() throws Throwable {
    final Tensor inputPrototype = new Tensor(3, 3).fill(() -> Util.R.get().nextGaussian());
    final Tensor outputPrototype = new Tensor(3, 3, 2, 3);
    final NNLayer component = new ToeplitzSynapseLayer(inputPrototype.getDims(), outputPrototype.getDims()).setWeights(() -> Util.R.get().nextGaussian());
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testDenseSynapseLayerJBLAS1() throws Throwable {
    final Tensor outputPrototype = new Tensor(2);
    final Tensor inputPrototype = new Tensor(3).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new DenseSynapseLayer(inputPrototype.getDims(), outputPrototype.getDims()).setWeights(() -> Util.R.get().nextGaussian());
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testDenseSynapseLayer2() throws Throwable {
    final Tensor outputPrototype = new Tensor(2);
    final Tensor inputPrototype = new Tensor(3).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new JavaDenseSynapseLayer(inputPrototype.dim(), outputPrototype.getDims()).setWeights(() -> Util.R.get().nextGaussian());
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testMaxSubsampleLayer() throws Throwable {
    final Tensor outputPrototype = new Tensor(1, 1, 1);
    final Tensor inputPrototype = new Tensor(2, 2, 1).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new MaxSubsampleLayer(2, 2, 1);
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testProductLayer() throws Throwable {
    final Tensor outputPrototype = new Tensor(1);
    final Tensor inputPrototype1 = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final Tensor inputPrototype2 = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new ProductLayer();
    test(component, outputPrototype, inputPrototype1, inputPrototype2);
  }
  
  @Test
  public void testSigmoidLayer() throws Throwable {
    final Tensor outputPrototype = new Tensor(3);
    final Tensor inputPrototype = new Tensor(3).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new SigmoidActivationLayer();
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testSoftmaxLayer() throws Throwable {
    final Tensor inputPrototype = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final Tensor outputPrototype = inputPrototype.copy();
    final NNLayer component = new SoftmaxActivationLayer();
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testSqActivationLayer() throws Throwable {
    final Tensor outputPrototype = new Tensor(3);
    final Tensor inputPrototype = new Tensor(3).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new SqActivationLayer();
    test(component, outputPrototype, inputPrototype);
  }
  
  @Test
  public void testSqLossLayer() throws Throwable {
    final Tensor outputPrototype = new Tensor(1);
    final Tensor inputPrototype1 = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final Tensor inputPrototype2 = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new MeanSqLossLayer();
    test(component, outputPrototype, inputPrototype1, inputPrototype2);
  }
  
  @Test
  public void testSumLayer() throws Throwable {
    final Tensor outputPrototype = new Tensor(1);
    final Tensor inputPrototype1 = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final Tensor inputPrototype2 = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new SumInputsLayer();
    test(component, outputPrototype, inputPrototype1, inputPrototype2);
  }
  
  @Test
  public void testSumReducerLayer() throws Throwable {
    final Tensor outputPrototype = new Tensor(1);
    final Tensor inputPrototype1 = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final Tensor inputPrototype2 = new Tensor(2).fill(() -> Util.R.get().nextGaussian());
    final NNLayer component = new SumReducerLayer();
    test(component, outputPrototype, inputPrototype1, inputPrototype2);
  }
  
}