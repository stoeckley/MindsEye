package com.simiacryptus.mindseye.test.regression;

import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.net.basic.BiasLayer;
import com.simiacryptus.mindseye.net.basic.DenseSynapseLayer;
import com.simiacryptus.mindseye.net.basic.SigmoidActivationLayer;
import com.simiacryptus.mindseye.net.dag.DAGNetwork;

public class BooleanSigmoidNetworkTests {
  static final Logger log = LoggerFactory.getLogger(BooleanSigmoidNetworkTests.class);

  public static final Random random = new Random();

  public NDArray[][] getTrainingData(final BiFunction<Boolean, Boolean, Boolean> gate) {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    final Function<double[], double[]> fn = v -> new double[] { gate.apply(v[0] == 1, v[1] == 1) ? 1 : -1 };
    final NDArray[][] samples = new NDArray[][] {
        // XOR:
        { new NDArray(inputSize, new double[] { 0, 0 }), null }, { new NDArray(inputSize, new double[] { 0, 1 }), null }, { new NDArray(inputSize, new double[] { 1, 0 }), null },
        { new NDArray(inputSize, new double[] { 1, 1 }), null } };
    for (int i = 0; i < samples.length; i++) {
      samples[i][1] = new NDArray(outSize, fn.apply(samples[i][0].getData()));
    }
    return samples;
  }

  public void test(final NDArray[][] samples) {
    final int[] midSize = new int[] { 2 };
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    new DAGNetwork()

        .add(new DenseSynapseLayer(NDArray.dim(inputSize), midSize)).add(new BiasLayer(midSize)).add(new SigmoidActivationLayer())

        .add(new DenseSynapseLayer(NDArray.dim(midSize), outSize)).add(new BiasLayer(outSize)).add(new SigmoidActivationLayer()).trainer(samples).setMutationAmplitude(10.)
        .verifyConvergence(0.01, 100);
  }

  @Test
  public void test_BasicNN_AND() throws Exception {
    final BiFunction<Boolean, Boolean, Boolean> gate = (a, b) -> a && b;
    final NDArray[][] samples = getTrainingData(gate);
    test(samples);
  }

  @Test
  public void test_BasicNN_OR() throws Exception {
    final BiFunction<Boolean, Boolean, Boolean> gate = (a, b) -> a || b;
    final NDArray[][] samples = getTrainingData(gate);
    test(samples);
  }

  @Test
  public void test_BasicNN_XOR() throws Exception {
    final BiFunction<Boolean, Boolean, Boolean> gate = (a, b) -> a != b;
    final NDArray[][] samples = getTrainingData(gate);
    test(samples);
  }

}
