package com.simiacryptus.mindseye.test.demo;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

import com.simiacryptus.mindseye.layers.BiasLayer;
import com.simiacryptus.mindseye.layers.DenseSynapseLayer;
import com.simiacryptus.mindseye.layers.ExpActivationLayer;
import com.simiacryptus.mindseye.layers.N2NormalizationLayer;
import com.simiacryptus.mindseye.layers.SigmoidActivationLayer;
import com.simiacryptus.mindseye.layers.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.test.dev.MNIST;
import com.simiacryptus.mindseye.test.dev.SimpleMNIST;
import com.simiacryptus.mindseye.training.PipelineNetwork;
import com.simiacryptus.mindseye.training.Trainer;
import com.simiacryptus.mindseye.util.LabeledObject;
import com.simiacryptus.mindseye.util.Util;

public class MNISTClassificationTests extends ClassificationTestBase {
  
  public MNISTClassificationTests() {
    super();
    drawBG = false;
  }
  
  @Override
  public Trainer buildTrainer(NDArray[][] samples, PipelineNetwork net) {
    return super.buildTrainer(samples, net).setVerbose(true);
  }

  @Override
  public PipelineNetwork buildNetwork() {
    final int[] inputSize = new int[] { 28, 28 };
    final int[] outSize = new int[] { 10 };
    final PipelineNetwork net = new PipelineNetwork()
        .add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize))
        //.add(new BiasLayer(outSize))
        //.add(new SigmoidActivationLayer());
        //.add(new ExpActivationLayer())
        .add(new N2NormalizationLayer());
        //.add(new SoftmaxActivationLayer());
    return net;
  }
  
  public double[] inputToXY(NDArray input, int classificationActual, int classificationExpected) {
    double n = 10.;
    double[] c = new double[] { //
        ((classificationActual   + Util.R.get().nextDouble()) / (n+1)), //
         (classificationExpected + Util.R.get().nextDouble()) / (n+1) //
    };
    return new double[]{c[0]*6-3,c[1]*6-3};
  }
  
  @Test
  public void test() throws Exception {
    test(trainingData());
  }

  public NDArray[][] trainingData() throws IOException {
    int maxSize = 1000;
    List<LabeledObject<NDArray>> data = Util.shuffle(MNIST.trainingDataStream()
        .filter(this::filter)
        .collect(Collectors.toList()));
    NDArray[][] trainingData = data.parallelStream().limit(maxSize)
        .map(obj -> {
          int out = SimpleMNIST.toOut(remap(obj.label));
          NDArray output = SimpleMNIST.toOutNDArray(out, 10);
          return new NDArray[] { obj.data, output };
        })
        .toArray(i -> new NDArray[i][]);
    return trainingData;
  }

  private String remap(String label) {
    switch(label){
    case "[0]": return "[5]";
    case "[5]": return "[9]";
    case "[9]": return "[0]";
    default: return label;
    }
  }

  public boolean filter(LabeledObject<NDArray> item) {
    if(item.label.equals("[0]")) return true;
    if(item.label.equals("[5]")) return true;
    if(item.label.equals("[9]")) return true;
    return false;
  }

  @Override
  public void verify(Trainer trainer) {
    trainer.verifyConvergence(0, 0.0, 1);
  }
  
}