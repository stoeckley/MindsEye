package com.simiacryptus.mindseye.test.demo;

import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.net.basic.BiasLayer;
import com.simiacryptus.mindseye.net.basic.DenseSynapseLayer;
import com.simiacryptus.mindseye.net.basic.EntropyLossLayer;
import com.simiacryptus.mindseye.net.basic.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.net.dag.DAGNetwork;
import com.simiacryptus.mindseye.training.Tester;

public class SoftmaxTests1 extends SimpleClassificationTests {
  @Override
  public DAGNetwork buildNetwork() {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final DAGNetwork net = new DAGNetwork()//
        .add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize))//
        .add(new BiasLayer(outSize))//
        .add(new SoftmaxActivationLayer());
    return net;
  }

  @Override
  public Tester buildTrainer(final NDArray[][] samples, final DAGNetwork net) {
    return net.trainer(samples, new EntropyLossLayer());
  }

  @Override
  public void verify(final Tester trainer) {
    trainer.setVerbose(true);
    trainer.setMutationAmplitude(2);
    //trainer.getInner().getDynamicRateTrainer().setStopError(-Double.POSITIVE_INFINITY);
    // trainer.getInner().setAlignEnabled(false);
    trainer.getInner().setPopulationSize(1).setNumberOfGenerations(0);
    //trainer.verifyConvergence(-Double.POSITIVE_INFINITY, 1);
    trainer.verifyConvergence(0.01, 10);
  }
}
