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

package com.simiacryptus.mindseye.test.unit;

import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.layers.cudnn.GpuController;
import com.simiacryptus.mindseye.layers.java.PlaceholderLayer;
import com.simiacryptus.mindseye.test.SimpleEval;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.util.data.ScalarStatistics;
import com.simiacryptus.util.io.KryoUtil;
import com.simiacryptus.util.io.NotebookOutput;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The type Derivative tester.
 */
public class BatchDerivativeTester implements ComponentTest {
  
  /**
   * The Probe size.
   */
  public final double probeSize;
  private final double tolerance;
  private final int batches;
  private boolean testLearning = true;
  private boolean testFeedback = true;
  private boolean verify = true;
  private boolean verbose = true;
  
  /**
   * Instantiates a new Derivative tester.
   *
   * @param tolerance the tolerance
   * @param probeSize the probe size
   * @param batches   the batches
   */
  public BatchDerivativeTester(double tolerance, double probeSize, int batches) {
    this.tolerance = tolerance;
    this.probeSize = probeSize;
    this.batches = batches;
  }
  
  /**
   * Test tolerance statistics.
   *
   * @param log
   * @param component      the component
   * @param inputPrototype the input prototype
   * @return the tolerance statistics
   */
  public ToleranceStatistics test(NotebookOutput log, final NNLayer component, final Tensor... inputPrototype) {
    log.h3("Differential Validation");
    return log.code(() -> {
      return test(component, inputPrototype);
    });
  }
  
  /**
   * Test tolerance statistics.
   *
   * @param component   the component
   * @param inputSingle the input prototype
   * @return the tolerance statistics
   */
  public ToleranceStatistics test(NNLayer component, Tensor[] inputSingle) {
    Tensor[] inputPrototype = IntStream.range(0, batches).mapToObj(i -> inputSingle[0].copy()).toArray(j -> new Tensor[j]);
    Tensor outputPrototype = SimpleEval.run(component, inputPrototype[0]).getOutput();
    ToleranceStatistics statistics = new ToleranceStatistics();
    if (verbose) {
      System.out.println(String.format("Inputs: %s", Arrays.stream(inputPrototype).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).get()));
      System.out.println(String.format("Inputs Statistics: %s", Arrays.stream(inputPrototype).map(x -> new ScalarStatistics().add(x.getData()).toString()).reduce((a, b) -> a + ",\n" + b).get()));
      System.out.println(String.format("Output: %s", outputPrototype.prettyPrint()));
      System.out.println(String.format("Outputs Statistics: %s", new ScalarStatistics().add(outputPrototype.getData())));
    }
    if (isTestFeedback()) {
      statistics = statistics.combine(IntStream.range(0, inputPrototype.length).mapToObj(i -> {
        final Tensor measuredGradient = !verify ? null : measureFeedbackGradient(component, i, outputPrototype, inputPrototype);
        final Tensor implementedGradient = getFeedbackGradient(component, i, outputPrototype, inputPrototype);
        try {
          ToleranceStatistics result = IntStream.range(0, null == measuredGradient ? 0 : measuredGradient.dim()).mapToObj(i1 -> {
            return new ToleranceStatistics().accumulate(measuredGradient.getData()[i1], implementedGradient.getData()[i1]);
          }).reduce((a, b) -> a.combine(b)).orElse(new ToleranceStatistics());
          
          if (!(result.absoluteTol.getMax() < tolerance)) throw new AssertionError(result.toString());
          //System.out.println(String.format("Component: %s", component));
          if (verbose) {
            System.out.println(String.format("Feedback for input %s", i));
            System.out.println(String.format("Inputs Values: %s", inputPrototype[i].prettyPrint()));
            System.out.println(String.format("Value Statistics: %s", new ScalarStatistics().add(inputPrototype[i].getData())));
            System.out.println(String.format("Implemented Feedback: %s", implementedGradient.prettyPrint()));
            System.out.println(String.format("Implemented Statistics: %s", new ScalarStatistics().add(implementedGradient.getData())));
            if (null != measuredGradient) {
              System.out.println(String.format("Measured Feedback: %s", measuredGradient.prettyPrint()));
              System.out.println(String.format("Measured Statistics: %s", new ScalarStatistics().add(measuredGradient.getData())));
              System.out.println(String.format("Feedback Error: %s", measuredGradient.minus(implementedGradient).prettyPrint()));
              System.out.println(String.format("Error Statistics: %s", new ScalarStatistics().add(measuredGradient.minus(implementedGradient).getData())));
            }
          }
          return result;
        } catch (final Throwable e) {
          //System.out.println(String.format("Component: %s", component));
          System.out.println(String.format("Feedback for input %s", i));
          System.out.println(String.format("Inputs Values: %s", inputPrototype[i].prettyPrint()));
          System.out.println(String.format("Value Statistics: %s", new ScalarStatistics().add(inputPrototype[i].getData())));
          System.out.println(String.format("Implemented Feedback: %s", implementedGradient.prettyPrint()));
          System.out.println(String.format("Implemented Statistics: %s", new ScalarStatistics().add(implementedGradient.getData())));
          if (null != measuredGradient) {
            System.out.println(String.format("Measured: %s", measuredGradient.prettyPrint()));
            System.out.println(String.format("Measured Statistics: %s", new ScalarStatistics().add(measuredGradient.getData())));
            System.out.println(String.format("Feedback Error: %s", measuredGradient.minus(implementedGradient).prettyPrint()));
            System.out.println(String.format("Error Statistics: %s", new ScalarStatistics().add(measuredGradient.minus(implementedGradient).getData())));
          }
          throw e;
        }
      }).reduce((a, b) -> a.combine(b)).get());
    }
    if (isTestLearning()) {
      ToleranceStatistics prev = statistics;
      statistics = IntStream.range(0, component.state().size()).mapToObj(i -> {
        final Tensor measuredGradient = !verify ? null : measureLearningGradient(component, i, outputPrototype, inputPrototype);
        final Tensor implementedGradient = getLearningGradient(component, i, outputPrototype, inputPrototype);
        try {
          ToleranceStatistics result = IntStream.range(0, null == measuredGradient ? 0 : measuredGradient.dim()).mapToObj(i1 -> {
            return new ToleranceStatistics().accumulate(measuredGradient.getData()[i1], implementedGradient.getData()[i1]);
          }).reduce((a, b) -> a.combine(b)).orElse(new ToleranceStatistics());
          if (!(result.absoluteTol.getMax() < tolerance)) {
            throw new AssertionError(result.toString());
          }
          else {
            //System.out.println(String.format("Component: %s", component));
            if (verbose) {
              
              System.out.println(String.format("Learning Gradient for weight set %s", i));
              System.out.println(String.format("Weights: %s", new Tensor(component.state().get(i)).prettyPrint()));
              System.out.println(String.format("Implemented Gradient: %s", implementedGradient.prettyPrint()));
              System.out.println(String.format("Implemented Statistics: %s", new ScalarStatistics().add(implementedGradient.getData())));
              if (null != measuredGradient) {
                System.out.println(String.format("Measured Gradient: %s", measuredGradient.prettyPrint()));
                System.out.println(String.format("Measured Statistics: %s", new ScalarStatistics().add(measuredGradient.getData())));
                System.out.println(String.format("Gradient Error: %s", measuredGradient.minus(implementedGradient).prettyPrint()));
                System.out.println(String.format("Error Statistics: %s", new ScalarStatistics().add(measuredGradient.minus(implementedGradient).getData())));
              }
            }
            return result;
          }
        } catch (final Throwable e) {
          //System.out.println(String.format("Component: %s", component));
          System.out.println(String.format("Learning Gradient for weight set %s", i));
          System.out.println(String.format("Implemented Gradient: %s", implementedGradient.prettyPrint()));
          System.out.println(String.format("Implemented Statistics: %s", new ScalarStatistics().add(implementedGradient.getData())));
          if (null != measuredGradient) {
            System.out.println(String.format("Measured Gradient: %s", measuredGradient.prettyPrint()));
            System.out.println(String.format("Measured Statistics: %s", new ScalarStatistics().add(measuredGradient.getData())));
            System.out.println(String.format("Gradient Error: %s", measuredGradient.minus(implementedGradient).prettyPrint()));
            System.out.println(String.format("Error Statistics: %s", new ScalarStatistics().add(measuredGradient.minus(implementedGradient).getData())));
          }
          throw e;
        }
        
      }).reduce((a, b) -> a.combine(b)).map(x -> x.combine(prev)).orElseGet(() -> prev);
    }
    //System.out.println(String.format("Component: %s\nInputs: %s\noutput=%s", component, Arrays.toString(inputPrototype), outputPrototype));
    System.out.println(String.format("Finite-Difference Derivative Accuracy:"));
    System.out.println(String.format("absoluteTol: %s", statistics.absoluteTol));
    System.out.println(String.format("relativeTol: %s", statistics.relativeTol));
    
    testFrozen(component, inputPrototype);
    testUnFrozen(component, inputPrototype);
    
    return statistics;
  }
  
  /**
   * Test frozen.
   *
   * @param component      the component
   * @param inputPrototype the input prototype
   */
  public void testFrozen(NNLayer component, Tensor[] inputPrototype) {
    AtomicBoolean reachedInputFeedback = new AtomicBoolean(false);
    NNLayer frozen = component.copy().freeze();
    GpuController.run(exe -> {
      NNResult eval = frozen.eval(exe, new NNResult(new TensorArray(inputPrototype)) {
        @Override
        public void accumulate(DeltaSet buffer, TensorList data) {
          reachedInputFeedback.set(true);
        }
  
        @Override
        public boolean isAlive() {
          return true;
        }
      });
      DeltaSet<NNLayer> buffer = new DeltaSet();
      eval.accumulate(buffer, eval.getData().copy());
      List<Delta> deltas = component.state().stream().map(doubles -> {
        return buffer.stream().filter(x -> x.target == doubles).findFirst().orElse(null);
      }).filter(x -> x != null).collect(Collectors.toList());
      if (!deltas.isEmpty() && !component.state().isEmpty()) {
        throw new AssertionError("Frozen component listed in delta. Deltas: " + deltas);
      }
      int inElements = Arrays.stream(inputPrototype).mapToInt(x -> x.dim()).sum();
      if (!reachedInputFeedback.get() && 0 < inElements) {
        throw new RuntimeException("Frozen component did not pass input backwards");
      }
    });
  }
  
  /**
   * Test un frozen.
   *
   * @param component      the component
   * @param inputPrototype the input prototype
   */
  public void testUnFrozen(NNLayer component, Tensor[] inputPrototype) {
    AtomicBoolean reachedInputFeedback = new AtomicBoolean(false);
    NNLayer frozen = component.copy().setFrozen(false);
    GpuController.run(exe -> {
      NNResult eval = frozen.eval(exe, new NNResult(new TensorArray(inputPrototype)) {
        @Override
        public void accumulate(DeltaSet buffer, TensorList data) {
          reachedInputFeedback.set(true);
        }
  
        @Override
        public boolean isAlive() {
          return true;
        }
      });
      DeltaSet<NNLayer> buffer = new DeltaSet();
      eval.accumulate(buffer, eval.getData());
      List<double[]> stateList = frozen.state();
      List<Delta<NNLayer>> deltas = stateList.stream().map(doubles -> {
        return buffer.stream().filter(x -> x.target == doubles).findFirst().orElse(null);
      }).filter(x -> x != null).collect(Collectors.toList());
      if (deltas.isEmpty() && !stateList.isEmpty()) {
        throw new AssertionError("Nonfrozen component not listed in delta. Deltas: " + deltas);
      }
      if (!reachedInputFeedback.get()) {
        throw new RuntimeException("Nonfrozen component did not pass input backwards");
      }
    });
  }
  
  private Tensor getFeedbackGradient(final NNLayer component, final int inputIndex, final Tensor outputPrototype, final Tensor... inputPrototype) {
    final Tensor inputTensor = inputPrototype[inputIndex];
    final int inputDims = inputTensor.dim();
    final Tensor result = new Tensor(inputDims, outputPrototype.dim());
    for (int j = 0; j < outputPrototype.dim(); j++) {
      final int j_ = j;
      PlaceholderLayer inputKey = new PlaceholderLayer(new Tensor());
      final NNResult copyInput = new NNResult(new TensorArray(inputPrototype)) {
        @Override
        public void accumulate(final DeltaSet<NNLayer> buffer, final TensorList data) {
          final Tensor gradientBuffer = new Tensor(inputDims, outputPrototype.dim());
          if (!Arrays.equals(inputTensor.getDimensions(), data.get(inputIndex).getDimensions())) {
            throw new AssertionError();
          }
          for (int i = 0; i < inputDims; i++) {
            gradientBuffer.set(new int[]{i, j_}, data.get(inputIndex).getData()[i]);
          }
          buffer.get(inputKey, new double[gradientBuffer.dim()]).addInPlace(gradientBuffer.getData());
        }
        
        @Override
        public boolean isAlive() {
          return true;
        }
      };
      final Tensor[] data = copyInput.getData().stream().map(s -> s.mapCoords(k -> (k.getIndex() == j_) ? 1 : 0)).toArray(i -> new Tensor[i]);
      GpuController.INSTANCE.distribute(Arrays.<Tensor[]>asList(inputPrototype),
        (d, exe) -> {
          NNResult eval = component.eval(exe, copyInput);
          Tensor tensor = eval.getData().get(0);
          DeltaSet<NNLayer> deltaSet = new DeltaSet();
          eval.accumulate(deltaSet, new TensorArray(eval.getData().stream().map(x->x.map(v->1)).toArray(i->new Tensor[i])));
          Delta<NNLayer> inputDelta = deltaSet.getMap().get(inputKey);
          if (null != inputDelta) result.accum(new Tensor(inputDelta.getDelta(), result.getDimensions()));
          return tensor;
        }, (a, b) -> a.add(b));
    }
    return result;
  }
  
  private Tensor getLearningGradient(final NNLayer component, final int layerNum, final Tensor outputPrototype, final Tensor... inputPrototype) {
    component.setFrozen(false);
    final double[] stateArray = component.state().get(layerNum);
    final int stateLen = stateArray.length;
    final Tensor gradient = new Tensor(stateLen, outputPrototype.dim());
    for (int j = 0; j < outputPrototype.dim(); j++) {
      final int j_ = j;
      final DeltaSet<NNLayer> buffer = new DeltaSet();
      final Tensor[] data = {new Tensor(outputPrototype.getDimensions()).fill((k) -> k == j_ ? 1 : 0)};
      GpuController.call(exe -> {
        NNResult eval = component.eval(exe, NNResult.singleResultArray(new Tensor[][]{inputPrototype}));
        Tensor tensor = eval.getData().get(0);
        eval.accumulate(buffer, new TensorArray(data));
        return tensor;
      });
      final DoubleBuffer<NNLayer> deltaFlushBuffer = buffer.getMap().values().stream().filter(x -> x.target == stateArray).findFirst().orElse(null);
      if (null != deltaFlushBuffer) {
        for (int i = 0; i < stateLen; i++) {
          gradient.set(new int[]{i, j_}, deltaFlushBuffer.getDelta()[i]);
        }
      }
    }
    return gradient;
  }
  
  private Tensor measureFeedbackGradient(final NNLayer component, final int inputIndex, final Tensor outputPrototype, final Tensor... inputPrototype) {
    final Tensor measuredGradient = new Tensor(inputPrototype[inputIndex].dim(), outputPrototype.dim());
    Tensor baseOutput = GpuController.call(exe -> {
      return component.eval(exe, NNResult.singleResultArray(new Tensor[][]{inputPrototype})).getData().get(0);
    });
    outputPrototype.set(baseOutput);
    for (int i = 0; i < inputPrototype[inputIndex].dim(); i++) {
      final Tensor inputProbe = inputPrototype[inputIndex].copy();
      inputProbe.add(i, probeSize * 1);
      final Tensor[] copyInput = Arrays.copyOf(inputPrototype, inputPrototype.length);
      copyInput[inputIndex] = inputProbe;
      Tensor evalProbe = GpuController.call(exe -> {
        return component.eval(exe, NNResult.singleResultArray(new Tensor[][]{copyInput})).getData().get(0);
      });
      final Tensor delta = evalProbe.minus(baseOutput).scaleInPlace(1. / probeSize);
      for (int j = 0; j < delta.dim(); j++) {
        measuredGradient.set(new int[]{i, j}, delta.getData()[j]);
      }
    }
    return measuredGradient;
  }
  
  private Tensor measureLearningGradient(final NNLayer component, final int layerNum, final Tensor outputPrototype, final Tensor... inputPrototype) {
    final int stateLen = component.state().get(layerNum).length;
    final Tensor gradient = new Tensor(stateLen, outputPrototype.dim());
    
    Tensor baseOutput = GpuController.call(exe -> {
      return component.eval(exe, NNResult.singleResultArray(new Tensor[][]{inputPrototype})).getData().get(0);
    });
    
    for (int i = 0; i < stateLen; i++) {
      final NNLayer copy = KryoUtil.kryo().copy(component);
      copy.state().get(layerNum)[i] += probeSize;
      
      Tensor evalProbe = GpuController.call(exe -> {
        return copy.eval(exe, NNResult.singleResultArray(new Tensor[][]{inputPrototype})).getData().get(0);
      });
      
      final Tensor delta = evalProbe.minus(baseOutput).scaleInPlace(1. / probeSize);
      for (int j = 0; j < delta.dim(); j++) {
        gradient.set(new int[]{i, j}, delta.getData()[j]);
      }
    }
    return gradient;
  }
  
  /**
   * Is test learning boolean.
   *
   * @return the boolean
   */
  public boolean isTestLearning() {
    return testLearning;
  }
  
  /**
   * Sets test learning.
   *
   * @param testLearning the test learning
   * @return the test learning
   */
  public BatchDerivativeTester setTestLearning(boolean testLearning) {
    this.testLearning = testLearning;
    return this;
  }
  
  /**
   * Is test feedback boolean.
   *
   * @return the boolean
   */
  public boolean isTestFeedback() {
    return testFeedback;
  }
  
  /**
   * Sets test feedback.
   *
   * @param testFeedback the test feedback
   * @return the test feedback
   */
  public BatchDerivativeTester setTestFeedback(boolean testFeedback) {
    this.testFeedback = testFeedback;
    return this;
  }
  
  /**
   * Is verify boolean.
   *
   * @return the boolean
   */
  public boolean isVerify() {
    return verify;
  }
  
  /**
   * Sets verify.
   *
   * @param verify the verify
   * @return the verify
   */
  public BatchDerivativeTester setVerify(boolean verify) {
    this.verify = verify;
    return this;
  }
  
  /**
   * Is verbose boolean.
   *
   * @return the boolean
   */
  public boolean isVerbose() {
    return verbose;
  }
  
  /**
   * Sets verbose.
   *
   * @param verbose the verbose
   * @return the verbose
   */
  public BatchDerivativeTester setVerbose(boolean verbose) {
    this.verbose = verbose;
    return this;
  }
}