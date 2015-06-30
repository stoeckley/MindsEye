package com.simiacryptus.mindseye.test;

import java.util.Random;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.NDArray;
import com.simiacryptus.mindseye.PipelineNetwork;
import com.simiacryptus.mindseye.layers.BiasLayer;
import com.simiacryptus.mindseye.layers.DenseSynapseLayer;
import com.simiacryptus.mindseye.layers.SigmoidActivationLayer;
import com.simiacryptus.mindseye.layers.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.learning.DeltaInversionBuffer;

public class NetworkElementUnitTests {
  static final Logger log = LoggerFactory.getLogger(NetworkElementUnitTests.class);

  public static final Random random = new Random();

  @Test
  public void bias_train() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] {
        { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { -1, 1 }) }
    };
    new PipelineNetwork()
    .add(new BiasLayer(inputSize))
    .setRate(0.1).setVerbose(false)
    .test(samples, 1000, 0.1, 100);
  }

  @Test
  public void bias_bias() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] {
        { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { -1, 1 }) }
    };
    new PipelineNetwork()
    .add(new BiasLayer(inputSize))
    .setRate(0.1).setVerbose(false)
    .test(samples, 1000, 0.1, 100);
  }

  @Test
  public void denseSynapseLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] {
        { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 1, -1 }) }
    };
    new PipelineNetwork()
    .add(new BiasLayer(inputSize))
    .add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize).addWeights(() -> 0.1 * SimpleNetworkTests.random.nextGaussian()).freeze())
    .setRate(0.1).setVerbose(false)
    .test(samples, 1000, 0.1, 100);
  }

  @Test
  public void denseSynapseLayer_train() throws Exception {
    DeltaInversionBuffer.DEBUG = true;
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] {
        { new NDArray(inputSize, new double[] { 1, 0 }), new NDArray(outSize, new double[] { 0, -1 }) },
        { new NDArray(inputSize, new double[] { 0, 1 }), new NDArray(outSize, new double[] { 1, 0 }) },
        { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1, -1 }) }
    };
    new PipelineNetwork()
    .add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize).addWeights(() -> 0.1 * SimpleNetworkTests.random.nextGaussian()))
    .setRate(0.1).setVerbose(true)
    .test(samples, 1000, 0.1, 100);
  }

  @Test
  public void sigmoidActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] {
        { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 1, -1 }) }
    };
    new PipelineNetwork()
    .add(new BiasLayer(inputSize))
    .add(new SigmoidActivationLayer())
    .setRate(0.1).setVerbose(false)
    .test(samples, 1000, 0.1, 100);
  }

  @Test
  public void softmaxActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] {
        { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 0.1, 0.9 }) }
    };
    new PipelineNetwork()
    .add(new BiasLayer(inputSize))
    .add(new SoftmaxActivationLayer())
    .setRate(0.1).setVerbose(false)
    .test(samples, 1000, 0.1, 100);
  }

}
