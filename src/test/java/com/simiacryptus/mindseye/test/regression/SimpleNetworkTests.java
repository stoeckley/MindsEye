package com.simiacryptus.mindseye.test.regression;

import java.util.Random;
import java.util.function.DoubleSupplier;

import com.simiacryptus.util.ml.Tensor;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.net.dag.DAGNetwork;
import com.simiacryptus.mindseye.net.activation.SigmoidActivationLayer;
import com.simiacryptus.mindseye.net.basic.BiasLayer;
import com.simiacryptus.mindseye.net.basic.DenseSynapseLayer;
import com.simiacryptus.mindseye.net.loss.EntropyLossLayer;
import com.simiacryptus.mindseye.net.loss.SqLossLayer;
import com.simiacryptus.mindseye.test.Tester;

public class SimpleNetworkTests {
  static final Logger log = LoggerFactory.getLogger(SimpleNetworkTests.class);

  public static final Random random = new Random();

  @Test
  public void test_BasicNN() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    final Tensor[][] samples = new Tensor[][] { { new Tensor(inputSize, new double[] { 0, 1 }), new Tensor(outSize, new double[] { 0.5 }) },
        { new Tensor(inputSize, new double[] { 1, 0 }), new Tensor(outSize, new double[] { 0 }) },
        { new Tensor(inputSize, new double[] { 0, 0 }), new Tensor(outSize, new double[] { 0.5 }) },
        { new Tensor(inputSize, new double[] { 1, 1 }), new Tensor(outSize, new double[] { 0 }) } };
    new Tester().init(samples, new DAGNetwork().add(new DenseSynapseLayer(Tensor.dim(inputSize), inputSize)).add(new BiasLayer(inputSize)).add(new SigmoidActivationLayer())
        .add(new DenseSynapseLayer(Tensor.dim(inputSize), outSize)).add(new BiasLayer(outSize)), new EntropyLossLayer()).verifyConvergence(0.1, 10);
  }

  @Test
  public void test_BasicNN_OR() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] midSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    final Tensor[][] samples = new Tensor[][] {
        // XOR:
        { new Tensor(inputSize, new double[] { 0, 1 }), new Tensor(outSize, new double[] { 1 }) },
        { new Tensor(inputSize, new double[] { 1, 0 }), new Tensor(outSize, new double[] { 1 }) },
        { new Tensor(inputSize, new double[] { 0, 0 }), new Tensor(outSize, new double[] { -1 }) },
        { new Tensor(inputSize, new double[] { 1, 1 }), new Tensor(outSize, new double[] { 1 }) } };
    new Tester().init(samples,
        new DAGNetwork()//
            .add(new DenseSynapseLayer(Tensor.dim(inputSize), midSize))//
            .add(new BiasLayer(midSize))//
            .add(new SigmoidActivationLayer())//
            .add(new DenseSynapseLayer(Tensor.dim(midSize), outSize))//
            .add(new BiasLayer(outSize))//
            .add(new SigmoidActivationLayer()),
        new SqLossLayer()).verifyConvergence(0.01, 100, 95);
  }

  @Test
  public void test_BasicNN_XOR_3layer() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] midSize = new int[] { 4 };
    final int[] outSize = new int[] { 1 };
    final Tensor[][] samples = new Tensor[][] {
        // XOR:
        { new Tensor(inputSize, new double[] { 0, 1 }), new Tensor(outSize, new double[] { 1 }) },
        { new Tensor(inputSize, new double[] { 1, 0 }), new Tensor(outSize, new double[] { 1 }) },
        { new Tensor(inputSize, new double[] { 0, 0 }), new Tensor(outSize, new double[] { -1 }) },
        { new Tensor(inputSize, new double[] { 1, 1 }), new Tensor(outSize, new double[] { -1 }) } };
    final DAGNetwork net = new DAGNetwork().add(new DenseSynapseLayer(Tensor.dim(inputSize), midSize)).add(new BiasLayer(midSize)).add(new SigmoidActivationLayer())
        .add(new DenseSynapseLayer(Tensor.dim(midSize), midSize)).add(new BiasLayer(midSize)).add(new SigmoidActivationLayer())
        .add(new DenseSynapseLayer(Tensor.dim(midSize), outSize)).add(new BiasLayer(outSize)).add(new SigmoidActivationLayer());
    new Tester().init(samples, net, new EntropyLossLayer()).verifyConvergence(0.01, 100);
  }

  @Test
  public void test_DualSigmoid() throws Exception {
    final int[] inputSize = new int[] { 1 };
    final int[] midSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    final Tensor[][] samples = new Tensor[][] { { new Tensor(inputSize, new double[] { -1 }), new Tensor(outSize, new double[] { 0 }) },
        { new Tensor(inputSize, new double[] { 0 }), new Tensor(outSize, new double[] { .2 }) },
        { new Tensor(inputSize, new double[] { 1 }), new Tensor(outSize, new double[] { 0 }) } };
    new Tester()
        .init(samples,
            new DAGNetwork()//
                .add(new DenseSynapseLayer(Tensor.dim(inputSize), midSize).setWeights(new double[] { 1, 1 }).freeze())//
                .add(new BiasLayer(midSize).set(new double[] { -1, 1 }))//
                .add(new SigmoidActivationLayer())//
                .add(new DenseSynapseLayer(Tensor.dim(midSize), outSize).setWeights(new double[] { 1, -1 }).freeze()),
            new EntropyLossLayer())//
        .verifyConvergence(0.1, 10);
  }

  @Test
  public void test_LinearNN() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] midSize = new int[] { 3 };
    final int[] outSize = new int[] { 1 };
    final Tensor[][] samples = new Tensor[][] { { new Tensor(inputSize, new double[] { 0, 0 }), new Tensor(outSize, new double[] { 0 }) },
        { new Tensor(inputSize, new double[] { 0, 1 }), new Tensor(outSize, new double[] { 1 }) },
        { new Tensor(inputSize, new double[] { 1, 0 }), new Tensor(outSize, new double[] { -1 }) },
        { new Tensor(inputSize, new double[] { 1, 1 }), new Tensor(outSize, new double[] { 0 }) } };
    new Tester().init(samples, new DAGNetwork().add(new DenseSynapseLayer(Tensor.dim(inputSize), midSize)).add(new DenseSynapseLayer(Tensor.dim(midSize), midSize))
        .add(new DenseSynapseLayer(Tensor.dim(midSize), outSize)).add(new BiasLayer(outSize)), new EntropyLossLayer()).setVerbose(false).verifyConvergence(0.1, 100);
  }

  @Test
  public void testDenseLinearLayer_2Layer() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final Tensor[][] samples = new Tensor[][] { { new Tensor(inputSize, new double[] { 0, 1 }), new Tensor(outSize, new double[] { 1, 0 }) },
        { new Tensor(inputSize, new double[] { 1, 0 }), new Tensor(outSize, new double[] { 0, 1 }) } };

    final DoubleSupplier f = () -> {
      double x = 15.0 * SimpleNetworkTests.random.nextGaussian();
      if (x < 0 && x > -0.001) {
        x -= 1;
      }
      if (x >= 0 && x < 0.001) {
        x += 1;
      }
      return x;
    };
    new Tester()
        .init(samples,
            new DAGNetwork() //
                .add(new DenseSynapseLayer(Tensor.dim(inputSize), inputSize).addWeights(f).freeze()) //
                .add(new DenseSynapseLayer(Tensor.dim(inputSize), outSize)),
            new SqLossLayer())//
        // .setVerbose(true)//
        .setStaticRate(0.01)//
        .verifyConvergence(0.01, 100);

    new Tester()
        .init(samples,
            new DAGNetwork() //
                .add(new DenseSynapseLayer(Tensor.dim(inputSize), inputSize)) //
                .add(new DenseSynapseLayer(Tensor.dim(inputSize), outSize).addWeights(f).freeze()),
            new SqLossLayer())//
        // .setStaticRate(0.01)
        // .setVerbose(true).setParallel(false)
        .verifyConvergence(0.01, 100);

    new Tester().init(samples,
        new DAGNetwork() //
            .add(new DenseSynapseLayer(Tensor.dim(inputSize), inputSize)) //
            .add(new DenseSynapseLayer(Tensor.dim(inputSize), outSize)),
        new SqLossLayer()).setStaticRate(0.01).verifyConvergence(0.01, 100, 80);

    new Tester().init(samples,
        new DAGNetwork() //
            .add(new DenseSynapseLayer(Tensor.dim(inputSize), inputSize)) //
            .add(new DenseSynapseLayer(Tensor.dim(inputSize), inputSize)) //
            .add(new DenseSynapseLayer(Tensor.dim(inputSize), outSize)),
        new SqLossLayer()).verifyConvergence(0.01, 100);
  }

}
