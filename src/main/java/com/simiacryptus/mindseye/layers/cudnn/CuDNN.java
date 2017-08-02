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

package com.simiacryptus.mindseye.layers.cudnn;

import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.util.lang.ResourcePool;
import com.simiacryptus.util.lang.StaticResourcePool;
import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.jcudnn.*;
import jcuda.runtime.JCuda;

import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jcuda.jcudnn.JCudnn.*;
import static jcuda.jcudnn.JCudnn.cudnnSetPoolingNdDescriptor;
import static jcuda.jcudnn.cudnnConvolutionFwdPreference.CUDNN_CONVOLUTION_FWD_PREFER_FASTEST;
import static jcuda.jcudnn.cudnnNanPropagation.CUDNN_PROPAGATE_NAN;
import static jcuda.jcudnn.cudnnStatus.CUDNN_STATUS_SUCCESS;
import static jcuda.runtime.JCuda.*;
import static jcuda.runtime.cudaMemcpyKind.cudaMemcpyDeviceToHost;
import static jcuda.runtime.cudaMemcpyKind.cudaMemcpyHostToDevice;

/**
 * The type Cu dnn.
 */
public class CuDNN {
    /**
     * The constant devicePool.
     */
    public static final ResourcePool<CuDNN> devicePool = new ResourcePool<CuDNN>(1) {
        @Override
        public CuDNN create() {
            return new CuDNN();
        }
    };
    /**
     * The constant gpuContexts.
     */
    public static final StaticResourcePool<NNLayer.NNExecutionContext> gpuContexts = new StaticResourcePool<NNLayer.NNExecutionContext>(IntStream.range(0,deviceCount())
            .mapToObj(i->new NNLayer.NNExecutionContext(){
                @Override
                public int getCudaDeviceId() {
                    return i;
                }
            }).collect(Collectors.toList()));

    /**
     * The Cudnn handle.
     */
    public final cudnnHandle cudnnHandle;

    /**
     * Device count int.
     *
     * @return the int
     */
    public static int deviceCount() {
        int[] deviceCount = new int[1];
        handle(cudaGetDeviceCount(deviceCount));
        System.out.println(String.format("Identified %s GPU devices", deviceCount[0]));
        return deviceCount[0];
    }

    /**
     * Instantiates a new Cu dnn.
     */
    protected CuDNN() {
        this.cudnnHandle = new cudnnHandle();
        cudnnCreate(cudnnHandle);
        //cudaSetDevice();
    }

    /**
     * Alloc cu dnn ptr.
     *
     * @param output the output
     * @return the cu dnn ptr
     */
    public static CuDNNPtr alloc(double[] output) {
        return alloc(Sizeof.DOUBLE * output.length);
    }

    /**
     * Create pooling descriptor cu dnn resource.
     *
     * @param mode       the mode
     * @param poolDims   the pool dims
     * @param windowSize the window size
     * @param padding    the padding
     * @param stride     the stride
     * @return the cu dnn resource
     */
    public static CuDNNResource<cudnnPoolingDescriptor> createPoolingDescriptor(int mode, int poolDims, int[] windowSize, int[] padding, int[] stride) {
        cudnnPoolingDescriptor poolingDesc = new cudnnPoolingDescriptor();
        cudnnCreatePoolingDescriptor(poolingDesc);
        cudnnSetPoolingNdDescriptor(poolingDesc,
                mode, CUDNN_PROPAGATE_NAN, poolDims, windowSize,
                padding, stride);
        return new CuDNNResource<cudnnPoolingDescriptor>(poolingDesc, JCudnn::cudnnDestroyPoolingDescriptor);
    }

    /**
     * The type Cu dnn resource.
     *
     * @param <T> the type parameter
     */
    public static class CuDNNResource<T> {

        private final T ptr;
        private final Consumer<T> destructor;
        private boolean finalized = false;

        /**
         * Instantiates a new Cu dnn resource.
         *
         * @param obj        the obj
         * @param destructor the destructor
         */
        protected CuDNNResource(T obj, Consumer<T> destructor) {
            this.ptr = obj;
            this.destructor = destructor;
        }

        /**
         * Is finalized boolean.
         *
         * @return the boolean
         */
        public boolean isFinalized() {
            return finalized;
        }

        @Override
        public synchronized void finalize() {
            if(!this.finalized) {
                if(null != this.destructor) this.destructor.accept(ptr);
                this.finalized = true;
            }
            try {
                super.finalize();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Gets ptr.
         *
         * @return the ptr
         */
        public T getPtr() {
            if(isFinalized()) return null;
            return ptr;
        }
    }

    /**
     * The type Cu dnn ptr.
     */
    public static class CuDNNPtr extends CuDNNResource<Pointer> {
        /**
         * The Size.
         */
        public final long size;

        /**
         * Instantiates a new Cu dnn ptr.
         *
         * @param size the size
         */
        protected CuDNNPtr(long size) {
            super(new Pointer(), JCuda::cudaFree);
            this.size = size;
            try {
                handle(cudaMalloc(this.getPtr(), size));
            } catch (Exception e) {
                try {
                    System.gc(); // Force any dead objects to be finalized
                    handle(cudaMalloc(this.getPtr(), size));
                } catch (Exception e2) {
                    throw new RuntimeException("Error allocating " + size + " bytes", e2);
                }
            }
            handle(cudaMemset(this.getPtr(), 0, size));
        }

        /**
         * Instantiates a new Cu dnn ptr.
         *
         * @param ptr  the ptr
         * @param size the size
         */
        protected CuDNNPtr(Pointer ptr, long size) {
            super(ptr, x->{});
            this.size = size;
        }

        /**
         * Write cu dnn ptr.
         *
         * @param data the data
         * @return the cu dnn ptr
         */
        public CuDNNPtr write(float[] data) {
            if(this.size != data.length * Sizeof.FLOAT) throw new IllegalArgumentException();
            handle(cudaMemcpy(getPtr(), Pointer.to(data), size, cudaMemcpyHostToDevice));
            return this;
        }

        /**
         * Write cu dnn ptr.
         *
         * @param data the data
         * @return the cu dnn ptr
         */
        public CuDNNPtr write(double[] data) {
            if(this.size != data.length * Sizeof.DOUBLE) throw new IllegalArgumentException();
            handle(cudaMemcpy(getPtr(), Pointer.to(data), size, cudaMemcpyHostToDevice));
            return this;
        }

        /**
         * Read cu dnn ptr.
         *
         * @param data the data
         * @return the cu dnn ptr
         */
        public CuDNNPtr read(double[] data) {
            if(this.size != data.length * Sizeof.DOUBLE) throw new IllegalArgumentException(this.size +" != " + data.length * Sizeof.DOUBLE);
            handle(cudaMemcpy(Pointer.to(data), getPtr(), size, cudaMemcpyDeviceToHost));
            return this;
        }

        /**
         * Read cu dnn ptr.
         *
         * @param data the data
         * @return the cu dnn ptr
         */
        public CuDNNPtr read(float[] data) {
            if(this.size != data.length * Sizeof.FLOAT) throw new IllegalArgumentException();
            handle(cudaMemcpy(Pointer.to(data), getPtr(), size, cudaMemcpyDeviceToHost));
            return this;
        }
    }

    /**
     * Get output dims int [ ].
     *
     * @param srcTensorDesc the src tensor desc
     * @param filterDesc    the filter desc
     * @param convDesc      the conv desc
     * @return the int [ ]
     */
    public static int[] getOutputDims(cudnnTensorDescriptor srcTensorDesc, cudnnFilterDescriptor filterDesc, cudnnConvolutionDescriptor convDesc) {
        int[] tensorOuputDims = new int[4];
        handle(cudnnGetConvolutionNdForwardOutputDim(convDesc, srcTensorDesc, filterDesc, tensorOuputDims.length, tensorOuputDims));
        return tensorOuputDims;
    }

    /**
     * Handle.
     *
     * @param returnCode the return code
     */
    public static void handle(int returnCode) {
        if(returnCode != CUDNN_STATUS_SUCCESS) {
            throw new RuntimeException("returnCode = " + cudnnStatus.stringFor(returnCode));
        }
    }

    /**
     * Allocate forward workspace cu dnn ptr.
     *
     * @param srcTensorDesc the src tensor desc
     * @param filterDesc    the filter desc
     * @param convDesc      the conv desc
     * @param dstTensorDesc the dst tensor desc
     * @param algorithm     the algorithm
     * @return the cu dnn ptr
     */
    public CuDNNPtr allocateForwardWorkspace(cudnnTensorDescriptor srcTensorDesc, cudnnFilterDescriptor filterDesc, cudnnConvolutionDescriptor convDesc, cudnnTensorDescriptor dstTensorDesc, int algorithm) {
        long sizeInBytesArray[] = { 0 };
        handle(cudnnGetConvolutionForwardWorkspaceSize(cudnnHandle,
                srcTensorDesc, filterDesc, convDesc, dstTensorDesc,
                algorithm, sizeInBytesArray));
        long workspaceSize = sizeInBytesArray[0];
        return alloc(0<workspaceSize?workspaceSize:0);
    }

    /**
     * Allocate backward filter workspace cu dnn ptr.
     *
     * @param srcTensorDesc the src tensor desc
     * @param filterDesc    the filter desc
     * @param convDesc      the conv desc
     * @param dstTensorDesc the dst tensor desc
     * @param algorithm     the algorithm
     * @return the cu dnn ptr
     */
    public CuDNNPtr allocateBackwardFilterWorkspace(cudnnTensorDescriptor srcTensorDesc, cudnnFilterDescriptor filterDesc, cudnnConvolutionDescriptor convDesc, cudnnTensorDescriptor dstTensorDesc, int algorithm) {
        long sizeInBytesArray[] = { 0 };
        handle(cudnnGetConvolutionBackwardFilterWorkspaceSize(cudnnHandle,
                srcTensorDesc, dstTensorDesc, convDesc, filterDesc,
                algorithm, sizeInBytesArray));
        long workspaceSize = sizeInBytesArray[0];
        return alloc(0<workspaceSize?workspaceSize:0);
    }

    /**
     * Allocate backward data workspace cu dnn ptr.
     *
     * @param inputDesc  the input desc
     * @param filterDesc the filter desc
     * @param convDesc   the conv desc
     * @param outputDesc the output desc
     * @param algorithm  the algorithm
     * @return the cu dnn ptr
     */
    public CuDNNPtr allocateBackwardDataWorkspace(cudnnTensorDescriptor inputDesc, cudnnFilterDescriptor filterDesc, cudnnConvolutionDescriptor convDesc, cudnnTensorDescriptor outputDesc, int algorithm) {
        long sizeInBytesArray[] = { 0 };
        handle(cudnnGetConvolutionBackwardDataWorkspaceSize(cudnnHandle,
                filterDesc, outputDesc, convDesc, inputDesc,
                algorithm, sizeInBytesArray));
        long workspaceSize = sizeInBytesArray[0];
        return alloc(0<workspaceSize?workspaceSize:0);
    }

    /**
     * Gets backward filter algorithm.
     *
     * @param inputDesc  the input desc
     * @param filterDesc the filter desc
     * @param convDesc   the conv desc
     * @param outputDesc the output desc
     * @return the backward filter algorithm
     */
    public int getBackwardFilterAlgorithm(cudnnTensorDescriptor inputDesc, cudnnFilterDescriptor filterDesc, cudnnConvolutionDescriptor convDesc, cudnnTensorDescriptor outputDesc) {
        int algoArray[] = { -1 };
        handle(cudnnGetConvolutionBackwardFilterAlgorithm(cudnnHandle,
                inputDesc, outputDesc, convDesc, filterDesc,
                CUDNN_CONVOLUTION_FWD_PREFER_FASTEST, 0, algoArray));
        return algoArray[0];
    }

    /**
     * Gets backward data algorithm.
     *
     * @param srcTensorDesc the src tensor desc
     * @param filterDesc    the filter desc
     * @param convDesc      the conv desc
     * @param weightDesc    the weight desc
     * @return the backward data algorithm
     */
    public int getBackwardDataAlgorithm(cudnnTensorDescriptor srcTensorDesc, cudnnFilterDescriptor filterDesc, cudnnConvolutionDescriptor convDesc, cudnnTensorDescriptor weightDesc) {
        int algoArray[] = { -1 };
        handle(cudnnGetConvolutionBackwardDataAlgorithm(cudnnHandle,
                filterDesc, srcTensorDesc, convDesc, weightDesc,
                CUDNN_CONVOLUTION_FWD_PREFER_FASTEST, 0, algoArray));
        return algoArray[0];
    }

    /**
     * Gets forward algorithm.
     *
     * @param srcTensorDesc the src tensor desc
     * @param filterDesc    the filter desc
     * @param convDesc      the conv desc
     * @param dstTensorDesc the dst tensor desc
     * @return the forward algorithm
     */
    public int getForwardAlgorithm(cudnnTensorDescriptor srcTensorDesc, cudnnFilterDescriptor filterDesc, cudnnConvolutionDescriptor convDesc, cudnnTensorDescriptor dstTensorDesc) {
        int algoArray[] = { -1 };
        handle(cudnnGetConvolutionForwardAlgorithm(cudnnHandle,
                srcTensorDesc, filterDesc, convDesc, dstTensorDesc,
                CUDNN_CONVOLUTION_FWD_PREFER_FASTEST, 0, algoArray));
        return algoArray[0];
    }

    /**
     * New convolution descriptor cu dnn resource.
     *
     * @param paddingX     the padding x
     * @param paddingY     the padding y
     * @param strideHeight the stride height
     * @param strideWidth  the stride width
     * @param mode         the mode
     * @return the cu dnn resource
     */
    public static CuDNNResource<cudnnConvolutionDescriptor> newConvolutionDescriptor(int paddingX, int paddingY, int strideHeight, int strideWidth, int mode) {
        cudnnConvolutionDescriptor convDesc = new cudnnConvolutionDescriptor();
        handle(cudnnCreateConvolutionDescriptor(convDesc));
        handle(cudnnSetConvolution2dDescriptor(
            convDesc,
            paddingY, // zero-padding height
            paddingX, // zero-padding width
            strideHeight, // vertical filter stride
            strideWidth, // horizontal filter stride
            1, // upscale the input in x-direction
            1, // upscale the input in y-direction
            mode
        ));
        return new CuDNNResource<>(convDesc, JCudnn::cudnnDestroyConvolutionDescriptor);
    }

    /**
     * Get stride int [ ].
     *
     * @param array the array
     * @return the int [ ]
     */
    public static int[] getStride(int[] array) {
        return IntStream.range(0, array.length).map(i->IntStream.range(i+1, array.length).map(ii-> array[ii]).reduce((a, b)->a*b).orElse(1)).toArray();
    }

    /**
     * New filter descriptor cu dnn resource.
     *
     * @param dataType       the data type
     * @param tensorLayout   the tensor layout
     * @param outputChannels the output channels
     * @param inputChannels  the input channels
     * @param height         the height
     * @param width          the width
     * @return the cu dnn resource
     */
    public static CuDNNResource<cudnnFilterDescriptor> newFilterDescriptor(int dataType, int tensorLayout, int outputChannels, int inputChannels, int height, int width) {
        cudnnFilterDescriptor filterDesc = new cudnnFilterDescriptor();
        handle(cudnnCreateFilterDescriptor(filterDesc));
        handle(cudnnSetFilter4dDescriptor(filterDesc, dataType, tensorLayout, outputChannels, inputChannels, height, width));
        return new CuDNNResource<>(filterDesc, JCudnn::cudnnDestroyFilterDescriptor);
    }

    /**
     * New filter descriptor cu dnn resource.
     *
     * @param dataType     the data type
     * @param tensorLayout the tensor layout
     * @param dimensions   the dimensions
     * @return the cu dnn resource
     */
    public static CuDNNResource<cudnnFilterDescriptor> newFilterDescriptor(int dataType, int tensorLayout, int[] dimensions) {
        cudnnFilterDescriptor filterDesc = new cudnnFilterDescriptor();
        handle(cudnnCreateFilterDescriptor(filterDesc));
        handle(cudnnSetFilterNdDescriptor(filterDesc, dataType, tensorLayout, dimensions.length, dimensions));
        return new CuDNNResource<>(filterDesc, JCudnn::cudnnDestroyFilterDescriptor);
    }

    /**
     * New tensor descriptor cu dnn resource.
     *
     * @param dataType     the data type
     * @param tensorLayout the tensor layout
     * @param batchCount   the batch count
     * @param channels     the channels
     * @param height       the height
     * @param width        the width
     * @return the cu dnn resource
     */
    public static CuDNNResource<cudnnTensorDescriptor> newTensorDescriptor(int dataType, int tensorLayout, int batchCount, int channels, int height, int width) {
        cudnnTensorDescriptor desc = new cudnnTensorDescriptor();
        handle(cudnnCreateTensorDescriptor(desc));
        handle(cudnnSetTensor4dDescriptor(desc, tensorLayout, dataType, batchCount, channels, height, width));
        return new CuDNNResource<>(desc, JCudnn::cudnnDestroyTensorDescriptor);
    }

    /**
     * New activation descriptor cu dnn resource.
     *
     * @param mode     the mode
     * @param reluNan  the relu nan
     * @param reluCeil the relu ceil
     * @return the cu dnn resource
     */
    public static CuDNNResource<cudnnActivationDescriptor> newActivationDescriptor(int mode, int reluNan, double reluCeil) {
        cudnnActivationDescriptor desc = new cudnnActivationDescriptor();
        handle(cudnnCreateActivationDescriptor(desc));
        handle(cudnnSetActivationDescriptor(desc, mode, reluNan, reluCeil));
        return new CuDNNResource<>(desc, JCudnn::cudnnDestroyActivationDescriptor);
    }

    /**
     * Alloc cu dnn ptr.
     *
     * @param size the size
     * @return the cu dnn ptr
     */
    public static CuDNNPtr alloc(long size) {
        return new CuDNNPtr(size);
    }

    /**
     * Java ptr cu dnn ptr.
     *
     * @param data the data
     * @return the cu dnn ptr
     */
    public static CuDNNPtr javaPtr(double... data) {
        return new CuDNNPtr(Pointer.to(data), data.length * Sizeof.DOUBLE);
    }

    /**
     * Java ptr cu dnn ptr.
     *
     * @param data the data
     * @return the cu dnn ptr
     */
    public static CuDNNPtr javaPtr(float... data) {
        return new CuDNNPtr(Pointer.to(data), data.length * Sizeof.FLOAT);
    }

    /**
     * Write cu dnn ptr.
     *
     * @param data the data
     * @return the cu dnn ptr
     */
    public static CuDNNPtr write(double... data) {
        return new CuDNNPtr(data.length * Sizeof.DOUBLE).write(data);
    }

    /**
     * Write cu dnn ptr.
     *
     * @param data the data
     * @return the cu dnn ptr
     */
    public static CuDNNPtr write(float... data) {
        return new CuDNNPtr(data.length * Sizeof.FLOAT).write(data);
    }

    @Override
    public void finalize() throws Throwable {
        handle(cudnnDestroy(cudnnHandle));
    }
}
