package com.simiacryptus.mindseye.training;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.deltas.DeltaBuffer;
import com.simiacryptus.mindseye.deltas.DeltaFlushBuffer;
import com.simiacryptus.mindseye.deltas.NNResult;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.net.dag.DAGNetwork;
import com.simiacryptus.mindseye.net.dag.EvaluationContext;
import com.simiacryptus.mindseye.net.dag.LazyResult;
import com.simiacryptus.mindseye.training.TrainingContext.TerminationCondition;
import com.simiacryptus.mindseye.util.Util;

import groovy.lang.Tuple2;

public class GradientDescentTrainer {

  private static final Logger log = LoggerFactory.getLogger(GradientDescentTrainer.class);

  private int[] constraintSet;
  private double error = Double.POSITIVE_INFINITY;
  private NDArray[][] masterTrainingData = null;
  private DAGNetwork net = null;
  private double rate = 0.3;
  private double[] rates = null;
  private double temperature = 0.0;
  private int[] trainingSet;
  private int[] validationSet;
  private boolean verbose = false;
  LazyResult predictionNode;

  protected DeltaBuffer calcDelta(final TrainingContext trainingContext, final NDArray[][] data) {
    final List<NNResult> netresults = eval(trainingContext, data);
    final DeltaBuffer buffer = new DeltaBuffer();
    IntStream.range(0, data.length).parallel().forEach(sample -> {
      final NNResult actualOutput = netresults.get(sample);
      final NDArray delta = new NDArray(new int[]{1},new double[]{-1.}).scale(getRate());
      actualOutput.feedback(delta, buffer);
    });
    return buffer;
  }

  public List<NNResult> eval(final TrainingContext trainingContext, final NDArray[][] trainingData) {
    return eval(trainingContext, trainingData, true);
  }

  public List<NNResult> eval(final TrainingContext trainingContext, final NDArray[][] trainingData, final boolean parallel) {
    IntStream stream = IntStream.range(0, trainingData.length);
    if (parallel) {
      stream = stream.parallel();
    }
    return stream.mapToObj(i -> {
      trainingContext.evaluations.increment();
      final NNResult eval = getNet().eval(trainingData[i]);
      return new Tuple2<>(eval, i);
    }).sorted(java.util.Comparator.comparing(x -> x.getSecond())).map(x -> x.getFirst()).collect(Collectors.toList());
  }

  protected ValidationResults evalValidationData(final TrainingContext trainingContext) {
    return evalValidationData(trainingContext, getValidationData(trainingContext));
  }

  public static class ValidationResults {
    public final List<NDArray> outputs;
    public final List<Tuple2<Double, Double>> stats;
    public final double rms;
    public ValidationResults(List<NDArray> outputs, List<Tuple2<Double, Double>> stats, double rms) {
      super();
      this.outputs = outputs;
      this.stats = stats;
      this.rms = rms;
    }
  }
  
  protected ValidationResults evalValidationData(final TrainingContext trainingContext, final NDArray[][] validationSet) {
    final List<NNResult> eval = eval(trainingContext, validationSet);
    final List<NDArray> finaloutput = eval.stream().map(x -> x.data).collect(Collectors.toList());
    final List<NDArray> predictoroutput = eval.stream().map(x -> {
      NNResult[] predictionResult = x.evaluationContext.cache.get(predictionNode.key);
      assert(null != predictionResult);
      return predictionResult[0].data;
    }).collect(Collectors.toList());
    List<Tuple2<Double, Double>> stats = Util.stats(trainingContext, validationSet, finaloutput, predictoroutput);
    double rms = stats.stream().mapToDouble(x->x.getSecond()).average().getAsDouble();
    setError(rms);
    return new ValidationResults(finaloutput, stats, rms);
  }

  public final NDArray[][] getConstraintData(final TrainingContext trainingContext) {
    return getTrainingData(getConstraintSet());
  }

  public int[] getConstraintSet() {
    if (null == this.constraintSet)
      return null;
    if (0 == this.constraintSet.length)
      return null;
    return this.constraintSet;
  }

  public synchronized double getError() {
    return this.error;
  }

  public NDArray[][] getMasterTrainingData() {
    return this.masterTrainingData;
  }

  public DAGNetwork getNet() {
    return this.net;
  }

  public double getRate() {
    return this.rate;
  }

  public double[] getRates() {
    return this.rates;
  }

  public double getTemperature() {
    return this.temperature;
  }

  public NDArray[][] getTrainingData(final int[] activeSet) {
    if (null != activeSet)
      return IntStream.of(activeSet).mapToObj(i -> getMasterTrainingData()[i]).toArray(i -> new NDArray[i][]);
    return getMasterTrainingData();
  }

  public final NDArray[][] getTrainingData(final TrainingContext trainingContext) {
    return getTrainingData(getTrainingSet());
  }

  public int[] getTrainingSet() {
    if (null == this.trainingSet)
      return null;
    if (0 == this.trainingSet.length)
      return null;
    return this.trainingSet;
  }

  public final NDArray[][] getValidationData(final TrainingContext trainingContext) {
    if (null != getValidationSet())
      return IntStream.of(getValidationSet()).mapToObj(i -> getMasterTrainingData()[i]).toArray(i -> new NDArray[i][]);
    return getMasterTrainingData();
  }

  public int[] getValidationSet() {
    if (null == this.validationSet)
      return null;
    if (0 == this.validationSet.length)
      return null;
    return this.validationSet;
  }

  public DeltaBuffer getVector(final TrainingContext trainingContext) {
    final DeltaBuffer primary = calcDelta(trainingContext, getTrainingData(getTrainingSet()));
    if (isVerbose()) {
      // log.debug(String.format("Primary Delta: %s", primary));
    }
    final DeltaBuffer constraint = calcDelta(trainingContext, getConstraintData(trainingContext)).unitV();
    if (isVerbose()) {
      // log.debug(String.format("Constraint Delta: %s", constraint));
    }
    final double dotProductConstraint = primary.dotProduct(constraint);
    if (dotProductConstraint < 0) {
      if (isVerbose()) {
        // log.debug(String.format("Removing component: %s",
        // dotProductConstraint));
      }
      return primary.add(constraint.scale(-dotProductConstraint));
    } else {
      if (isVerbose()) {
        // log.debug(String.format("Preserving component: %s",
        // dotProductConstraint));
      }
      return primary;
    }
  }

  public boolean isVerbose() {
    return this.verbose;
  }

  public synchronized void setConstraintSet(final int[] activeSet) {
    this.constraintSet = activeSet;
  }

  public GradientDescentTrainer setError(final double error) {
    //log.debug(String.format("Error: %s -> %s", this.error, error));
    this.error = error;
    return this;
  }

  public GradientDescentTrainer setMasterTrainingData(final NDArray[][] trainingData) {
    this.masterTrainingData = trainingData;
    return this;
  }

  public GradientDescentTrainer setNet(final DAGNetwork net) {
    this.net = net;
    return this;
  }

  public GradientDescentTrainer setRate(final double dynamicRate) {
    assert Double.isFinite(dynamicRate);
    this.rate = dynamicRate;
    return this;
  }

  public void setRates(final double[] rates) {
    this.rates = Arrays.copyOf(rates, rates.length);
  }

  public GradientDescentTrainer setTemperature(final double temperature) {
    this.temperature = temperature;
    return this;
  }

  public synchronized void setTrainingSet(final int[] activeSet) {
    this.trainingSet = activeSet;
  }

  public synchronized void setValidationSet(final int[] activeSet) {
    this.validationSet = activeSet;
  }

  public GradientDescentTrainer setVerbose(final boolean verbose) {
    if (verbose) {
      this.verbose = true;
    }
    this.verbose = verbose;
    return this;
  }

  public Double step(final TrainingContext trainingContext) throws TerminationCondition {
    final long startMs = System.currentTimeMillis();
    final double prevError = evalValidationData(trainingContext).rms;
    final double[] rates = getRates();
    if (null == rates)
      return Double.POSITIVE_INFINITY;
    final DeltaBuffer buffer = getVector(trainingContext);
    assert null != rates && rates.length == buffer.vector().size();
    final List<DeltaFlushBuffer> deltas = buffer.vector();
    assert null != rates && rates.length == deltas.size();
    IntStream.range(0, deltas.size()).forEach(i -> deltas.get(i).write(rates[i]));
    ;
    final double validationError = evalValidationData(trainingContext).rms;
    if (prevError == validationError) {
      if (this.verbose) {
        GradientDescentTrainer.log.debug(String.format("Static: (%s)", prevError));
      }
    } else if (!Util.thermalStep(prevError, validationError, getTemperature())) {
      if (this.verbose) {
        GradientDescentTrainer.log.debug(String.format("Reverting delta: (%s -> %s) - %s", prevError, validationError, validationError - prevError));
      }
      IntStream.range(0, deltas.size()).forEach(i -> deltas.get(i).write(-rates[i]));
      evalValidationData(trainingContext);
      return 0.;
    } else {
      if (this.verbose) {
        GradientDescentTrainer.log.debug(String.format("Validated delta: (%s -> %s) - %s", prevError, validationError, validationError - prevError));
      }
      setError(validationError);
    }
    trainingContext.gradientSteps.increment();
    if (this.verbose) {
      GradientDescentTrainer.log
          .debug(String.format("Trained Error: %s with rate %s*%s in %.03fs", validationError, getRate(), Arrays.toString(rates), (System.currentTimeMillis() - startMs) / 1000.));
    }
    return validationError - prevError;
  }

  public void setPredictionNode(LazyResult node) {
    this.predictionNode = node;
  }

}
