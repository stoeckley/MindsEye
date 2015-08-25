package com.simiacryptus.mindseye.training;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.learning.DeltaTransaction;
import com.simiacryptus.mindseye.learning.NNResult;
import com.simiacryptus.mindseye.math.LogNDArray;
import com.simiacryptus.mindseye.math.NDArray;

public class GradientDescentTrainer {

  private static final Logger log = LoggerFactory.getLogger(GradientDescentTrainer.class);

  public static double geometricMean(final double[] error) {
    final double geometricMean = Math.exp(DoubleStream.of(error).filter(x -> 0 != x).map(Math::log).average().orElse(Double.POSITIVE_INFINITY));
    return geometricMean;
  }
  
  private List<SupervisedTrainingParameters> currentNetworks = new ArrayList<>();
  private double[] error;

  private double rate = 0.5;

  private boolean verbose = false;

  public GradientDescentTrainer() {
  }

  public GradientDescentTrainer add(final PipelineNetwork net, final NDArray[][] data) {
    return add(new SupervisedTrainingParameters(net, data));
  }

  public GradientDescentTrainer add(final SupervisedTrainingParameters params) {
    this.getCurrentNetworks().add(params);
    return this;
  }

  protected double[] calcError(final List<List<NNResult>> results) {
    final List<List<Double>> rms = IntStream.range(0, this.getCurrentNetworks().size()).parallel()
        .mapToObj(network -> {
          final List<NNResult> result = results.get(network);
          final SupervisedTrainingParameters currentNet = this.getCurrentNetworks().get(network);
          return IntStream.range(0, result.size()).parallel().mapToObj(sample -> {
            final NNResult eval = result.get(sample);
            final NDArray output = currentNet.getIdeal(eval, currentNet.getTrainingData()[sample][1]);
            final double err = eval.errRms(output);
            return Math.pow(err, currentNet.getWeight());
          }).collect(Collectors.toList());
        }).collect(Collectors.toList());
    final double[] err = rms.stream().map(r ->
    r.stream().mapToDouble(x -> x).filter(Double::isFinite).filter(x -> 0 < x).average().orElse(1))
    .mapToDouble(x -> x).toArray();
    return err;
  }

  public GradientDescentTrainer clearMomentum() {
    this.getCurrentNetworks().forEach(x -> x.getNet());
    return this;
  }

  public double error() {
    final double[] error = getError();
    if (null == error) {
      trainSet();
      return error();
    }
    final double returnValue = Math.pow(GradientDescentTrainer.geometricMean(error), totalWeight());
    assert Double.isFinite(returnValue);
    return returnValue;
  }

  protected List<List<NNResult>> evalTrainingData() {
    return this.getCurrentNetworks().parallelStream().map(params -> Stream.of(params.getTrainingData())
        .parallel()
        .map(sample -> {
          final NDArray input = sample[0];
          final NDArray output = sample[1];
          final NNResult eval = params.getNet().eval(input);
          assert eval.data.dim() == output.dim();
          return eval;
        }).collect(Collectors.toList())).collect(Collectors.toList());
  }

  public synchronized double[] getError() {
    // if(null==this.error||0==this.error.length){
    // trainSet();
    // }
    return this.error;
  }

  public double getRate() {
    return this.rate;
  }

  public double[] getLayerRates() {
    return this.getCurrentNetworks().stream()
        .flatMap(n -> n.getNet().layers.stream())
        .distinct()
        .map(l -> l.getVector())
        .filter(l -> null != l)
        .filter(x -> !x.isFrozen())
        .mapToDouble(x -> x.getRate()).toArray();
  }

  public double[] getNetworkRates() {
    return this.getCurrentNetworks().stream()
        .mapToDouble(x -> x.getNet().getRate()).toArray();
  }

  public boolean isVerbose() {
    return this.verbose;
  }

  protected void learn(final List<List<NNResult>> results) {
    // Apply corrections
    IntStream.range(0, this.getCurrentNetworks().size())
    //.parallel()
    .forEach(network -> {
      final List<NNResult> netresults = results.get(network);
      final SupervisedTrainingParameters currentNet = this.getCurrentNetworks().get(network);
      IntStream.range(0, netresults.size())
      //.parallel()
      .forEach(sample -> {
        final NNResult eval = netresults.get(sample);
        final NDArray output = currentNet.getIdeal(eval, currentNet.getTrainingData()[sample][1]);
        NDArray delta2 = eval.delta(output);
        LogNDArray log2 = delta2.log();
        LogNDArray delta = log2.scale(getRate());
        final double factor = currentNet.getWeight();// * product / rmsList[network];
        //log.debug(String.format("%s actual vs %s ideal -> %s delta * %s", eval.data, output, delta, factor));
        if (Double.isFinite(factor)) {
          delta = delta.scale(factor);
        }
        eval.feedback(delta);
      });
    });
  }

  public GradientDescentTrainer setError(final double[] error) {
    this.error = error;
    return this;
  }

  public GradientDescentTrainer setRate(final double dynamicRate) {
    assert Double.isFinite(dynamicRate);
    this.rate = dynamicRate;
    return this;
  }

  public GradientDescentTrainer setVerbose(final boolean verbose) {
    if (verbose) {
      this.verbose = true;
    }
    this.verbose = verbose;
    return this;
  }

  public double totalWeight() {
    return 1 / this.getCurrentNetworks().stream().mapToDouble(p -> p.getWeight()).sum();
  }

  public synchronized double[] trainSet() {
    assert 0 < this.getCurrentNetworks().size();
    final List<List<NNResult>> results = evalTrainingData();
    final double[] calcError = calcError(results);
    if (this.verbose) {
      GradientDescentTrainer.log.debug(String.format("Training with rate %s*%s: (%s)", getRate(), Arrays.toString(getLayerRates()), Arrays.toString(calcError)));
    }
    setError(calcError);
    learn(results);
    this.getCurrentNetworks().stream().forEach(params -> params.getNet().writeDeltas(1));

    final double[] validationError = calcError(evalTrainingData());
    if (GradientDescentTrainer.geometricMean(calcError) < GradientDescentTrainer.geometricMean(validationError)) {
      if (this.verbose) {
        GradientDescentTrainer.log.debug(String.format("Reverting: (%s)", Arrays.toString(calcError)));
      }
      this.getCurrentNetworks().stream().forEach(params -> params.getNet().writeDeltas(-1));
    } else {
      if (this.verbose) {
        GradientDescentTrainer.log.debug(String.format("Validating: (%s)", Arrays.toString(calcError)));
      }
    }

    setError(calcError);
    return calcError;
  }

  public List<SupervisedTrainingParameters> getCurrentNetworks() {
    return currentNetworks;
  }

  public GradientDescentTrainer setCurrentNetworks(List<SupervisedTrainingParameters> currentNetworks) {
    this.currentNetworks = currentNetworks;
    return this;
  }

  public List<NNLayer> getLayers() {
    return getCurrentNetworks().stream().flatMap(x->x.getNet().layers.stream()).distinct().collect(Collectors.toList());
  }

  public List<PipelineNetwork> getNetwork() {
    return currentNetworks.stream().map(SupervisedTrainingParameters::getNet).collect(Collectors.toList());
  }

}
