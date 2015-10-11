package com.simiacryptus.mindseye.test.demo.mnist;

import com.simiacryptus.mindseye.Util;
import com.simiacryptus.mindseye.core.NDArray;
import com.simiacryptus.mindseye.core.delta.NNLayer;
import com.simiacryptus.mindseye.net.DAGNetwork;
import com.simiacryptus.mindseye.net.activation.SigmoidActivationLayer;
import com.simiacryptus.mindseye.net.activation.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.net.basic.BiasLayer;
import com.simiacryptus.mindseye.net.basic.DenseSynapseLayer;
import com.simiacryptus.mindseye.net.media.ConvolutionSynapseLayer;
import com.simiacryptus.mindseye.net.media.MaxSubsampleLayer;

public class MNISTClassificationTests3 extends MNISTClassificationTests {

  @Override
  public NNLayer<DAGNetwork> buildNetwork() {
    final int[] inputSize = new int[] { 28, 28, 1 };
    DAGNetwork net = new DAGNetwork();

    net = net.add(new ConvolutionSynapseLayer(new int[] { 3, 3 }, 4).addWeights(() -> Util.R.get().nextGaussian() * .1));
    net = net.add(new MaxSubsampleLayer(new int[] { 2, 2, 1 }));
    net = net.add(new SigmoidActivationLayer());

    // net = net.add(new SumSubsampleLayer(new int[] { 13, 13, 1 }));

    final int[] size = net.eval(new NDArray(inputSize)).data[0].getDims();
    net = net.add(new DenseSynapseLayer(NDArray.dim(size), new int[] { 10 }));
    net = net.add(new BiasLayer(10));
    net = net.add(new SoftmaxActivationLayer());
    return net;
  }

}
