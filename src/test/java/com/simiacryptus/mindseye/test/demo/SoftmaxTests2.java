package com.simiacryptus.mindseye.test.demo;

import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.net.basic.BiasLayer;
import com.simiacryptus.mindseye.net.basic.DenseSynapseLayer;
import com.simiacryptus.mindseye.net.basic.SigmoidActivationLayer;
import com.simiacryptus.mindseye.net.dag.DAGNetwork;
import com.simiacryptus.mindseye.net.dev.MinMaxFilterLayer;
import com.simiacryptus.mindseye.net.dev.PermutationLayer;
import com.simiacryptus.mindseye.training.Tester;

public class SoftmaxTests2 extends SimpleClassificationTests {

  @Override
  public DAGNetwork buildNetwork() {

    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final int[] midSize = new int[] { 10 };
    final int midLayers = 1;
    DAGNetwork net = new DAGNetwork();

    // net = net.add(new
    // SynapseActivationLayer(NDArray.dim(inputSize)).setWeights(()->1.));
    net = net.add(new DenseSynapseLayer(NDArray.dim(inputSize), midSize));
    net = net.add(new BiasLayer(midSize));
    net = net.add(new PermutationLayer());
    // net = net.add(new LinearActivationLayer());
    // net = net.add(new
    // SynapseActivationLayer(NDArray.dim(midSize)).setWeights(()->1.));
    net = net.add(new MinMaxFilterLayer());
    net = net.add(new SigmoidActivationLayer());

    for (int i = 0; i < midLayers; i++) {
      net = net.add(new DenseSynapseLayer(NDArray.dim(midSize), midSize));
      net = net.add(new BiasLayer(midSize));
      net = net.add(new PermutationLayer());
      // net = net.add(new LinearActivationLayer());
      net = net.add(new MinMaxFilterLayer());
      net = net.add(new SigmoidActivationLayer());
    }

    // net = net.add(new
    // SynapseActivationLayer(NDArray.dim(midSize)).setWeights(()->1.));
    net = net.add(new DenseSynapseLayer(NDArray.dim(midSize), outSize));
    // net = net.add(new PermutationLayer());
    // net = net.add(new
    // SynapseActivationLayer(NDArray.dim(outSize)).setWeights(()->1.));
    net = net.add(new BiasLayer(outSize));

    // net = net.add(new ExpActivationLayer());
    // net = net.add(new L1NormalizationLayer());
    // net = net.add(new LinearActivationLayer());
    net = net.add(new MinMaxFilterLayer());
    net = net.add(new SigmoidActivationLayer());
    // net = net.add(new SoftmaxActivationLayer());

    return net;
  }

  @Override
  public void test_Gaussians() throws Exception {
    super.test_Gaussians();
  }

  @Override
  public void test_II() throws Exception {
    super.test_II();
  }

  @Override
  public void test_III() throws Exception {
    super.test_III();
  }

  @Override
  public void test_Lines() throws Exception {

    super.test_Lines();
  }

  @Override
  public void test_O() throws Exception {
    super.test_O();
  }

  @Override
  public void test_O2() throws Exception {
    super.test_O2();
  }

  @Override
  public void test_O22() throws Exception {
    super.test_O22();
  }

  @Override
  public void test_O3() throws Exception {
    super.test_O3();
  }

  @Override
  public void test_oo() throws Exception {
    super.test_oo();
  }

  @Override
  public void test_simple() throws Exception {
    super.test_simple();
  }

  @Override
  public void test_snakes() throws Exception {
    super.test_snakes();
  }

  @Override
  public void test_sos() throws Exception {
    super.test_sos();
  }

  @Override
  public void test_X() throws Exception {
    super.test_X();
  }

  @Override
  public void test_xor() throws Exception {
    super.test_xor();
  }

  @Override
  public void verify(final Tester trainer) {
    trainer.setVerbose(true);
    trainer.setMutationAmplitude(10);
    // trainer.getInner().setAlignEnabled(false);
    trainer.getInner().setPopulationSize(1).setNumberOfGenerations(0);
    trainer.verifyConvergence(0.01, 10);
  }

}
