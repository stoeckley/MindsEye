/*
 * Copyright (c) 2018 by Andrew Charneski.
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

package com.simiacryptus.mindseye.layers.cudnn;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.layers.cudnn.lang.*;
import com.simiacryptus.util.Util;
import jcuda.jcudnn.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * This convolution layer only supports an equal number of input and output bands. It is used as the foundational
 * component for ConvolutionLayer, since the CuDNN api has this restriction (in recent versions).
 */
@SuppressWarnings("serial")
public class SimpleConvolutionLayer extends NNLayer implements LayerPrecision<SimpleConvolutionLayer> {
  
  
  /**
   * The Filter.
   */
  public final Tensor kernel;
  private int paddingX;
  private int paddingY;
  private Precision precision = Precision.Double;
  private int strideX = 1;
  private int strideY = 1;
  
  /**
   * Instantiates a new Convolution layer.
   */
  protected SimpleConvolutionLayer() {
    this(null);
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param width  the width
   * @param height the height
   * @param bands  the bands
   */
  public SimpleConvolutionLayer(final int width, final int height, final int bands) {
    this(new Tensor(width, height, bands));
    assert !false || 0 == (width - 1) % 2 : "Simple kernels must have odd width";
    assert !false || 0 == (height - 1) % 2 : "Simple kernels must have odd height";
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param json      the json
   * @param resources the resources
   */
  protected SimpleConvolutionLayer(final JsonObject json, Map<String, byte[]> resources) {
    super(json);
    kernel = Tensor.fromJson(json.get("filter"), resources);
    strideX = json.get("strideX").getAsInt();
    strideY = json.get("strideY").getAsInt();
    setPaddingX(json.get("paddingX").getAsInt());
    setPaddingY(json.get("paddingY").getAsInt());
    precision = Precision.valueOf(json.get("precision").getAsString());
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param kernel the filter
   */
  protected SimpleConvolutionLayer(final Tensor kernel) {
    super();
    int[] kernelSize = kernel.getDimensions();
    if (kernelSize.length != 3) throw new IllegalArgumentException();
    if (kernelSize[0] <= 0) throw new IllegalArgumentException();
    if (kernelSize[1] <= 0) throw new IllegalArgumentException();
    if (kernelSize[2] <= 0) throw new IllegalArgumentException();
    this.kernel = kernel;
    this.setPaddingX((int) Math.ceil((kernelSize[0] - 1) / 2.0));
    this.setPaddingY((int) Math.ceil((kernelSize[1] - 1) / 2.0));
  
  }
  
  /**
   * From json convolution layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the convolution layer
   */
  public static SimpleConvolutionLayer fromJson(final JsonObject json, Map<String, byte[]> rs) {
    return new SimpleConvolutionLayer(json, rs);
  }
  
  /**
   * Reverse int [ ].
   *
   * @param array the array
   * @return the int [ ]
   */
  public static int[] reverse(int... array) {
    for (int i = 0; i < array.length / 2; i++) {
      int j = array[array.length - (i + 1)];
      array[array.length - (i + 1)] = array[i];
      array[i] = j;
    }
    return array;
  }
  
  /**
   * Add weights convolution layer.
   *
   * @param f the f
   * @return the convolution layer
   */
  public SimpleConvolutionLayer addWeights(final DoubleSupplier f) {
    Util.add(f, kernel.getData());
    return this;
  }
  
  private boolean cmp(final int[] outputSize, final int[] outputDims) {
    if (4 != outputDims.length) return false;
    if (outputSize[0] != outputDims[3]) return false;
    if (outputSize[1] != outputDims[2]) return false;
    return outputSize[2] == outputDims[1];
  }
  
  @Override
  public NNResult eval(final NNResult... inObj) {
    if (!CuDNN.isEnabled()) return getCompatibilityLayer().eval(inObj);
    return CuDNN.run(nncontext -> {
      final int deviceNumber = nncontext.getDeviceNumber();
      if (deviceNumber < 0) return getCompatibilityLayer().eval(inObj);
      //assert Arrays.stream(inObj).flatMapToDouble(input->input.data.stream().flatMapToDouble(x-> Arrays.stream(x.getData()))).allMatch(v->Double.isFinite(v));
      nncontext.initThread();
      final NNResult input = inObj[0];
      final TensorList batch = input.getData();
      final int[] inputSize = batch.getDimensions();
      final int[] kernelSize = kernel.getDimensions();
      final int[] outputSize = getOutputSize(inputSize);
      final int length = batch.length();
    
      try {
      
        final CudaResource<cudnnTensorDescriptor> inputDescriptor = CuDNN.newTensorDescriptor(
          precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, inputSize[2], inputSize[1], inputSize[0]);
        final CudaResource<cudnnFilterDescriptor> filterDescriptor = CuDNN.newFilterDescriptor(
          precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, outputSize[2], inputSize[2], kernelSize[1], kernelSize[0]);
        final CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor = getConvolutionDescriptor();
  
        int[] outputDims = reverse(CuDNN.getOutputDims(inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr()));
        outputDims = IntStream.of(outputDims).limit(3).toArray();
        final CudaResource<cudnnTensorDescriptor> outputDescriptor = CuDNN.newTensorDescriptor(
          precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, outputDims[2], outputDims[1], outputDims[0]);
      
        assert 0 < kernel.getData().length;
        assert kernelSize[0] * kernelSize[1] * kernelSize[2] == kernel.getData().length;
        CudaPtr.MemoryType filterMemoryType = CudaPtr.MemoryType.Device;
        final ManagedCudaPtr filterPtr = new CudaPtr(kernel.getData().length * precision.size, deviceNumber, filterMemoryType).write(precision, kernel.getData()).managed();
        final CudaPtr inputData = CudaPtr.write(deviceNumber, precision, batch);
      
        final CudaPtr outputBuffer = CuDNN.alloc(deviceNumber, Tensor.dim(outputDims) * 1l * length * precision.size, true);
        final int algorithm = nncontext.getForwardAlgorithm(
          inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
        final CudaPtr workSpace = nncontext.allocateForwardWorkspace(deviceNumber,
                                                                     inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), algorithm);
        CuDNN.handle(CuDNN.cudnnConvolutionForward(nncontext.cudnnHandle, precision.getPointer(1.0),
                                                   inputDescriptor.getPtr(), inputData.getPtr(),
                                                   filterDescriptor.getPtr(), filterPtr.getPtr(),
                                                   convolutionDescriptor.getPtr(), algorithm, workSpace.getPtr(), workSpace.size, precision.getPointer(0.0),
                                                   outputDescriptor.getPtr(), outputBuffer.getPtr()));
        Supplier<CudaPtr> workspacePtr = PersistanceMode.Weak.wrap(workSpace);
        //filterPtr.setGpuPersistance(PersistanceMode.Weak);
        TensorList output = new GpuTensorList(outputBuffer, length, outputDims, precision);
        return new NNResult(output) {
        
          public StackTraceElement[] freedBy = null;
          AtomicBoolean hasAccumulated = new AtomicBoolean(false);
        
          @Override
          public void free() {
            free(workspacePtr.get());
            freedBy = Thread.currentThread().getStackTrace();
            Arrays.stream(inObj).forEach(NNResult::free);
            filterPtr.free();
          }
  
          public void free(CudaPtr cudaPtr) {
            if (null != cudaPtr) cudaPtr.finalize();
          }
  
          @Override
          public void accumulate(final DeltaSet<NNLayer> buffer, final TensorList error) {
            if (hasAccumulated.getAndSet(true)) throw new IllegalStateException();
            assert null == freedBy : Arrays.toString(freedBy);
            assert error.length() == batch.length();
            //assert error.stream().flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
            final int length = error.length();
            final TensorList inputBufferTensors = CuDNN.run(nncontext -> {
              int deviceNumber = nncontext.getDeviceNumber();
              final CudaPtr errorPtr = CudaPtr.write(deviceNumber, precision, error);
              if (!isFrozen()) {
                final CudaPtr filterBuffer = CuDNN.alloc(deviceNumber, kernel.getData().length * 1l * precision.size, true);
                try {
                  final int backwardAlgorithm = nncontext.getBackwardFilterAlgorithm(
                    inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
                  final CudaPtr workSpace = nncontext.allocateBackwardFilterWorkspace(deviceNumber,
                                                                                      inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), backwardAlgorithm);
                  CuDNN.handle(CuDNN.cudnnConvolutionBackwardFilter(nncontext.cudnnHandle, precision.getPointer(1.0),
                                                                    inputDescriptor.getPtr(), inputData.getPtr(),
                                                                    outputDescriptor.getPtr(), errorPtr.getPtr(),
                                                                    convolutionDescriptor.getPtr(), backwardAlgorithm, workSpace.getPtr(), workSpace.size, precision.getPointer(0.0),
                                                                    filterDescriptor.getPtr(), filterBuffer.getPtr()));
                  workSpace.finalize();
                } catch (final Throwable e) {
                  throw new ComponentException(String.format("Error in convolution %s x %s => %s", Arrays.toString(inputSize), Arrays.toString(kernelSize), Arrays.toString(outputSize)), e);
                }
                final Tensor weightGradient = CudaPtr.read(filterBuffer, precision, kernel.getDimensions());
                buffer.get(SimpleConvolutionLayer.this, kernel.getData()).addInPlace(weightGradient.getData());
                filterBuffer.finalize();
              }
              GpuTensorList gpuTensorList = null;
              if (input.isAlive()) {
                final CudaPtr inputBuffer = CuDNN.alloc(deviceNumber, Tensor.dim(batch.getDimensions()) * 1l * length * precision.size, true);
                try {
                  final int algorithm = nncontext.getBackwardDataAlgorithm(
                    inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
                  final CudaPtr workSpace = nncontext.allocateBackwardDataWorkspace(deviceNumber,
                                                                                    inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), algorithm);
                  CuDNN.handle(CuDNN.cudnnConvolutionBackwardData(nncontext.cudnnHandle, precision.getPointer(1.0),
                                                                  filterDescriptor.getPtr(), filterPtr.getPtr(),
                                                                  outputDescriptor.getPtr(), errorPtr.getPtr(),
                                                                  convolutionDescriptor.getPtr(), algorithm, workSpace.getPtr(), workSpace.size, precision.getPointer(0.0),
                                                                  inputDescriptor.getPtr(), inputBuffer.getPtr()));
                  workSpace.finalize();
                } catch (final Throwable e) {
                  throw new ComponentException(String.format("Error in convolution %s x %s => %s", Arrays.toString(inputSize), Arrays.toString(kernelSize), Arrays.toString(outputSize)), e);
                }
                gpuTensorList = new GpuTensorList(inputBuffer, length, inputSize, precision);
              }
              errorPtr.finalize();
              return gpuTensorList;
            });
            if (null != inputBufferTensors) {
              input.accumulate(buffer, inputBufferTensors);
              inputBufferTensors.recycle();
            }
            free();
          }
        
          @Override
          public boolean isAlive() {
            return input.isAlive() || !isFrozen();
          }
        };
      } catch (final Throwable e) {
        throw new ComponentException(String.format("Error in convolution %s x %s", Arrays.toString(inputSize), Arrays.toString(kernelSize)), e);
      }
    });
  }
  
  /**
   * Gets convolution descriptor.
   *
   * @return the convolution descriptor
   */
  public CudaResource<cudnnConvolutionDescriptor> getConvolutionDescriptor() {
    return CuDNN.newConvolutions2dDescriptor(cudnnConvolutionMode.CUDNN_CONVOLUTION, precision.code,
                                             paddingY, paddingX,
                                             strideY, strideX,
                                             1, 1);
  }
  
  /**
   * Gets compatibility layer.
   *
   * @return the compatibility layer
   */
  public NNLayer getCompatibilityLayer() {
    return this.as(com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer.class);
  }
  
  @Override
  public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    final JsonObject json = super.getJsonStub();
    json.add("filter", kernel.toJson(resources, dataSerializer));
    json.addProperty("strideX", strideX);
    json.addProperty("strideY", strideY);
    json.addProperty("paddingX", getPaddingX());
    json.addProperty("paddingY", getPaddingY());
    json.addProperty("precision", precision.name());
    return json;
  }
  
  
  /**
   * Get output size int [ ].
   *
   * @param inputSize the input size
   * @return the int [ ]
   */
  public int[] getOutputSize(final int... inputSize) {
    final int[] kernelSize = kernel.getDimensions();
    return IntStream.range(0, kernelSize.length).map(i -> {
      int x;
      if (i == kernelSize.length - 1) {
        //assert kernelSize[i] == inputSize[i];
        x = kernelSize[i] / inputSize[i];
      }
      else {
        int padding;
        if (i == 0) {
          padding = this.paddingX;
        }
        else if (i == 1) {
          padding = this.paddingY;
        }
        else {
          throw new IllegalStateException();
        }
        x = inputSize[i] - (kernelSize[i] - 1) + padding * 2;
      }
      assert 0 < x;
      return x;
    }).toArray();
  }
  
  @Override
  public Precision getPrecision() {
    return precision;
  }
  
  @Override
  public SimpleConvolutionLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }
  
  /**
   * The Stride x.
   *
   * @return the stride x
   */
  public int getStrideX() {
    return strideX;
  }
  
  /**
   * Sets stride x.
   *
   * @param strideX the stride x
   * @return the stride x
   */
  public SimpleConvolutionLayer setStrideX(final int strideX) {
    this.strideX = strideX;
    return this;
  }
  
  /**
   * The Stride y.
   *
   * @return the stride y
   */
  public int getStrideY() {
    return strideY;
  }
  
  /**
   * Sets stride y.
   *
   * @param strideY the stride y
   * @return the stride y
   */
  public SimpleConvolutionLayer setStrideY(final int strideY) {
    this.strideY = strideY;
    return this;
  }
  
  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  public SimpleConvolutionLayer set(final DoubleSupplier f) {
    kernel.coordStream(true).parallel().forEach(c -> {
      kernel.set(c, f.getAsDouble());
    });
    return this;
  }
  
  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  public SimpleConvolutionLayer set(final ToDoubleFunction<Coordinate> f) {
    kernel.coordStream(true).parallel().forEach(c -> {
      kernel.set(c, f.applyAsDouble(c));
    });
    return this;
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList(kernel.getData());
  }
  
  /**
   * Gets padding x.
   *
   * @return the padding x
   */
  public int getPaddingX() {
    return paddingX;
  }
  
  /**
   * Sets padding x.
   *
   * @param paddingX the padding x
   * @return the padding x
   */
  public SimpleConvolutionLayer setPaddingX(int paddingX) {
    this.paddingX = paddingX;
    return this;
  }
  
  /**
   * Gets padding y.
   *
   * @return the padding y
   */
  public int getPaddingY() {
    return paddingY;
  }
  
  /**
   * Sets padding y.
   *
   * @param paddingY the padding y
   * @return the padding y
   */
  public SimpleConvolutionLayer setPaddingY(int paddingY) {
    this.paddingY = paddingY;
    return this;
  }
  
  /**
   * Sets padding xy.
   *
   * @param x the x
   * @param y the y
   * @return the padding xy
   */
  public SimpleConvolutionLayer setPaddingXY(int x, int y) {
    return setPaddingX(x).setPaddingY(y);
  }
  
  /**
   * Sets weights log.
   *
   * @param f the f
   * @return the weights log
   */
  public SimpleConvolutionLayer setWeightsLog(double f) {
    return set(() -> Math.pow(10, f) * (Math.random() - 0.5));
  }
  
  /**
   * Set.
   *
   * @param kernel the kernel
   */
  public void set(Tensor kernel) {
    this.kernel.set(kernel);
  }
}
