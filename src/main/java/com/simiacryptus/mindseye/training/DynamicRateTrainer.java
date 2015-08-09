package com.simiacryptus.mindseye.training;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.learning.DeltaTransaction;

public class DynamicRateTrainer {
  
  private static final Logger log = LoggerFactory.getLogger(DynamicRateTrainer.class);

  public final ChampionTrainer inner;
  private int currentIteration = 0;

  private int lastCalibratedIteration = Integer.MIN_VALUE;
  private double maxRate = 5e4;
  private double minRate = 1e-10;
  private double mutationFactor = .5;
  private double rate = 1.;
  private int recalibrationInterval = 10;
  private boolean verbose = false;
  
  public DynamicRateTrainer() {
    this(new ChampionTrainer());
  }
  
  public DynamicRateTrainer(final ChampionTrainer inner) {
    this.inner = inner;
  }
  
  public void calibrate() {
    List<DeltaTransaction> deltaObjs = null;
    double[] adjustment = null;
    double[] newRate = null;
    boolean inBounds = false;
    try {
      deltaObjs = this.inner.current.currentNetworks.stream()
          .flatMap(n -> n.getNet().layers.stream())
          .filter(l -> l instanceof DeltaTransaction)
          .map(l -> (DeltaTransaction) l)
          .distinct().collect(Collectors.toList());
      final double[] localMin = this.inner.current.copy().clearMomentum().trainLineSearch(deltaObjs.size());
      adjustment = DoubleStream.of(localMin).map(x -> x * this.rate).toArray();
      newRate = DoubleStream.of(adjustment).map(x -> x * this.inner.current.getRate()).toArray();
      inBounds = DoubleStream.of(newRate).anyMatch(r -> this.maxRate > r && this.minRate < r);
    } catch (Exception e) {
      if(verbose) log.debug("Error calibrating",e);
    }
    if (inBounds)
    {
      if (this.verbose) {
        DynamicRateTrainer.log.debug(String.format("Adjusting rate by %s: %s", adjustment, newRate));
      }
      for(int i=0;i<deltaObjs.size();i++)deltaObjs.get(i).setRate(newRate[i]);
      this.inner.train();
      this.inner.updateBest();
      this.lastCalibratedIteration = this.currentIteration;
    } else {
      if (this.verbose) {
        DynamicRateTrainer.log.debug(String.format("Local Optimum reached - gradient not useful (%s). Mutating.", newRate));
      }
      this.inner.revert();
      this.inner.current.mutate(getMutationFactor());
      this.lastCalibratedIteration = this.currentIteration - this.recalibrationInterval;
    }
  }

  public double error() {
    return this.inner.current.error();
  }
  
  public GradientDescentTrainer getBest() {
    return this.inner.getBest();
  }
  
  public double getMaxRate() {
    return this.maxRate;
  }
  
  public double getMinRate() {
    return this.minRate;
  }
  
  public double getMutationFactor() {
    return this.mutationFactor;
  }
  
  public double getRate() {
    return this.rate;
  }
  
  public boolean isVerbose() {
    return this.verbose;
  }
  
  public DynamicRateTrainer setMaxRate(final double maxRate) {
    this.maxRate = maxRate;
    return this;
  }
  
  public DynamicRateTrainer setMinRate(final double minRate) {
    this.minRate = minRate;
    return this;
  }
  
  public void setMutationFactor(final double mutationRate) {
    this.mutationFactor = mutationRate;
  }
  
  public DynamicRateTrainer setRate(final double rate) {
    this.rate = rate;
    return this;
  }
  
  public DynamicRateTrainer setVerbose(final boolean verbose) {
    this.verbose = verbose;
    this.inner.setVerbose(true);
    return this;
  }
  
  public DynamicRateTrainer train() {
    if (this.lastCalibratedIteration < this.currentIteration++ - this.recalibrationInterval) {
      if (this.verbose) {
        DynamicRateTrainer.log.debug("Recalibrating learning rate due to interation schedule");
      }
      calibrate();
    }
    final double lastError = this.inner.current.error();
    this.inner.train();
    final double resultError = this.inner.current.error();
    if (resultError >= lastError)
    {
      if (this.verbose) {
        DynamicRateTrainer.log.debug("Recalibrating learning rate due to non-descending step");
      }
      calibrate();
    }
    // updateRate(lastError, resultError);
    return this;
  }
  
}
