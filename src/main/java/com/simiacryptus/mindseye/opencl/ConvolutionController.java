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

package com.simiacryptus.mindseye.opencl;

import com.simiacryptus.mindseye.net.media.ImgConvolutionSynapseLayer;
import com.simiacryptus.util.ml.Tensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public final class ConvolutionController {
  
  private static final BackpropKernel backpropTask = new BackpropKernel();
  private static final ConvolveKernel convolveTask = new ConvolveKernel();
  private static final GradientKernel kernelTask = new GradientKernel();
  
  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(ConvolutionController.class);
  
  private final int[] inputSize;
  private final int[] kernelSize;
  private final int[] outputSize;
  
  public ConvolutionController(final int[] inputSize, final int[] kernelSize) {
    this.inputSize = inputSize;
    this.kernelSize = kernelSize;
    this.outputSize = ImgConvolutionSynapseLayer.getOutputDims(inputSize, kernelSize);
    assert this.outputSize.length == 3;
    assert this.kernelSize.length == 3;
    assert this.inputSize.length == 3;
  }
  
  public static int MAX_BUFFER_SIZE = 1024 * 1024;
  public void backprop(final double[][] input, final double[] weights, final double[][] output) {
    int length = input.length;
    assert(length == output.length);
    int inLength = input[0].length;
    int outLength = output[0].length;
    int inputsPerRun = Math.min(Math.floorDiv(MAX_BUFFER_SIZE, inLength), length);
    int runs = length / inputsPerRun;
    int leftover = length - runs * inputsPerRun;
    double[] inputBuffer = null;
    double[] outputBuffer = null;
    for(int run=0;run<runs;run++) {
      int currentIndexOffset = run * inputsPerRun;
      int currentNumItems = run < run - 1 ? inputsPerRun : leftover == 0 ? inputsPerRun : leftover;
      if(null == inputBuffer || inputBuffer.length != inLength * currentNumItems) {
        Tensor.recycle(inputBuffer);
        inputBuffer = Tensor.obtain(inLength * currentNumItems);
      }
      if(null == outputBuffer || outputBuffer.length != outLength * currentNumItems) {
        Tensor.recycle(outputBuffer);
        outputBuffer = Tensor.obtain(outLength * currentNumItems);
      }
      for (int i = 0; i< currentNumItems; i++) {
        assert outLength == output[currentIndexOffset+i].length;
        System.arraycopy(output[currentIndexOffset+i], 0, outputBuffer, i * outLength, outLength);
      }
      backprop(inputBuffer,weights,outputBuffer);
      for (int i = 0; i< currentNumItems; i++) {
        assert inLength == input[currentIndexOffset+i].length;
        System.arraycopy(inputBuffer, i * inLength, input[currentIndexOffset+i], 0, inLength);
      }
    }
    Tensor.recycle(inputBuffer);
    Tensor.recycle(outputBuffer);

  }
  
  public void convolve(final double[][] input, final double[] weights, final double[][] output) {
    int length = input.length;
    assert(length == output.length);
    int inLength = input[0].length;
    int outLength = output[0].length;
    int inputsPerRun = Math.min(Math.floorDiv(MAX_BUFFER_SIZE, inLength), length);
    int runs = length / inputsPerRun;
    int leftover = length - runs * inputsPerRun;
    double[] inputBuffer = null;
    double[] outputBuffer = null;
    for(int run=0;run<runs;run++) {
      int currentIndexOffset = run * inputsPerRun;
      int currentNumItems = run < run - 1 ? inputsPerRun : leftover == 0 ? inputsPerRun : leftover;
      if(null == inputBuffer || inputBuffer.length != inLength * currentNumItems) {
        Tensor.recycle(inputBuffer);
        inputBuffer = Tensor.obtain(inLength * currentNumItems);
      }
      if(null == outputBuffer || outputBuffer.length != outLength * currentNumItems) {
        Tensor.recycle(outputBuffer);
        outputBuffer = Tensor.obtain(outLength * currentNumItems);
      }
      for (int i = 0; i< currentNumItems; i++) {
        assert inLength == input[currentIndexOffset+i].length;
        System.arraycopy(input[currentIndexOffset+i], 0, inputBuffer, i * inLength, inLength);
      }
      convolve(inputBuffer,weights,outputBuffer);
      for (int i = 0; i< currentNumItems; i++) {
        assert outLength == output[currentIndexOffset+i].length;
        System.arraycopy(outputBuffer, i * outLength, output[currentIndexOffset+i], 0, outLength);
      }
    }
    Tensor.recycle(inputBuffer);
    Tensor.recycle(outputBuffer);
  }
  
  public void gradient(final double[][] input, final double[] weights, final double[][] output) {
    int length = input.length;
    assert(length == output.length);
    int inLength = input[0].length;
    int outLength = output[0].length;
    int inputsPerRun = Math.min(Math.floorDiv(MAX_BUFFER_SIZE, inLength), length);
    int runs = length / inputsPerRun;
    int leftover = length - runs * inputsPerRun;
    double[] inputBuffer = null;
    double[] outputBuffer = null;
    for(int run=0;run<runs;run++) {
      int currentIndexOffset = run * inputsPerRun;
      int currentNumItems = run < run - 1 ? inputsPerRun : leftover == 0 ? inputsPerRun : leftover;
      if(null == inputBuffer || inputBuffer.length != inLength * currentNumItems) {
        Tensor.recycle(inputBuffer);
        inputBuffer = Tensor.obtain(inLength * currentNumItems);
      }
      if(null == outputBuffer || outputBuffer.length != outLength * currentNumItems) {
        Tensor.recycle(outputBuffer);
        outputBuffer = Tensor.obtain(outLength * currentNumItems);
      }
      for (int i = 0; i< currentNumItems; i++) {
        assert inLength == input[currentIndexOffset+i].length;
        assert outLength == output[currentIndexOffset+i].length;
        System.arraycopy(input[currentIndexOffset+i], 0, inputBuffer, i * inLength, inLength);
        System.arraycopy(output[currentIndexOffset+i], 0, outputBuffer, i * outLength, outLength);
      }
      gradient(inputBuffer,weights,outputBuffer);
    }
    Tensor.recycle(inputBuffer);
    Tensor.recycle(outputBuffer);
  }
  
  private void backprop(final double[] input, final double[] weights, final double[] output) {
    assert this.kernelSize[0] * this.kernelSize[1] * this.kernelSize[2] == weights.length;
    OpenCL.devicePool.with(device -> {
      try {
        synchronized (backpropTask) {
          backpropTask.input = input;
          backpropTask.weights = weights;
          backpropTask.output = output;
          backpropTask.outputSize = this.outputSize;
          backpropTask.inputSize = this.inputSize;
          backpropTask.kernelSize = this.kernelSize;
          backpropTask.setExplicit(true);
          backpropTask.put(backpropTask.outputSize);
          backpropTask.put(backpropTask.inputSize);
          backpropTask.put(backpropTask.kernelSize);
          backpropTask.put(backpropTask.weights);
          backpropTask.put(backpropTask.output);
          backpropTask.exe(device);
          backpropTask.get(backpropTask.input);
          backpropTask.input = null;
          backpropTask.weights = null;
          backpropTask.output = null;
          backpropTask.outputSize = null;
          backpropTask.inputSize = null;
          backpropTask.kernelSize = null;
        }
      } catch (Throwable e) {
        throw new RuntimeException("Error with " +this,e);
      }
    });
  }
  
  private void convolve(final double[] input, final double[] weights, final double[] output) {
    OpenCL.devicePool.with(device -> {
      try {
        synchronized (convolveTask) {
          convolveTask.input = input;
          convolveTask.weights = weights;
          convolveTask.output = output;
          convolveTask.outputSize = this.outputSize;
          convolveTask.inputSize = this.inputSize;
          convolveTask.kernelSize = this.kernelSize;
          convolveTask.setExplicit(true);
          convolveTask.put(convolveTask.outputSize);
          convolveTask.put(convolveTask.inputSize);
          convolveTask.put(convolveTask.kernelSize);
          convolveTask.put(convolveTask.input);
          convolveTask.put(convolveTask.weights);
          convolveTask.exe(device);
          convolveTask.get(convolveTask.output);
          convolveTask.input = null;
          convolveTask.weights = null;
          convolveTask.output = null;
          convolveTask.outputSize = null;
          convolveTask.inputSize = null;
          convolveTask.kernelSize = null;
        }
      } catch (Throwable e) {
        throw new RuntimeException("Error with " +this,e);
      }
    });
  }
  
  private void gradient(final double[] input, final double[] weights, final double[] output) {
    OpenCL.devicePool.with(device -> {
      try {
        synchronized (kernelTask) {
          kernelTask.input = input;
          kernelTask.weights = weights;
          kernelTask.output = output;
          kernelTask.outputSize = this.outputSize;
          kernelTask.inputSize = this.inputSize;
          kernelTask.kernelSize = this.kernelSize;
          kernelTask.setExplicit(true);
          kernelTask.put(kernelTask.outputSize);
          kernelTask.put(kernelTask.inputSize);
          kernelTask.put(kernelTask.kernelSize);
          kernelTask.put(kernelTask.input);
          kernelTask.put(kernelTask.output);
          kernelTask.exe(device);
          kernelTask.get(kernelTask.weights);
          kernelTask.input = null;
          kernelTask.weights = null;
          kernelTask.output = null;
          kernelTask.outputSize = null;
          kernelTask.inputSize = null;
          kernelTask.kernelSize = null;
        }
      } catch (Throwable e) {
        throw new RuntimeException("Error with " +this,e);
      }
    });
  }
  
  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Convolve [");
    builder.append(Arrays.toString(this.inputSize));
    builder.append(" x ");
    builder.append(Arrays.toString(this.kernelSize));
    builder.append(" => ");
    builder.append(Arrays.toString(this.outputSize));
    builder.append("]");
    return builder.toString();
  }
  
}
