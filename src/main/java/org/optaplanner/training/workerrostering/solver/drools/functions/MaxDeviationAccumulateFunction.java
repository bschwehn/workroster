/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.training.workerrostering.solver.drools.functions;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.kie.api.runtime.rule.AccumulateFunction;

public class MaxDeviationAccumulateFunction implements AccumulateFunction<MaxDeviationAccumulateFunction.LoadBalanceData> {

    protected static class LoadBalanceData implements Serializable {

        private long n;
        private List<Long> entries = new ArrayList<Long>();
        private long sum;
        // the sum of squared deviation from zero
        private long squaredSum;

    }

    @Override
    public LoadBalanceData createContext() {
        return new LoadBalanceData();
    }

    @Override
    public void init(LoadBalanceData data) {
        data.n = 0L;
        data.sum = 0L;
        data.squaredSum = 0L;
    }

    @Override
    public void accumulate(LoadBalanceData data, Object o) {
        long value = (long) o;
        data.n++;
        data.sum += value;
        data.squaredSum += value * value;
        data.entries.add(value);
    }

    @Override
    public boolean supportsReverse() {
        return true;
    }

    @Override
    public void reverse(LoadBalanceData data, Object o) {
        long value = (long) o;
        data.n--;
        data.sum -= value;
        data.squaredSum -= value * value;
        int sizeBefore = data.entries.size();
        data.entries.remove(value);
        if (data.entries.size() != sizeBefore - 1) {
        	throw new IllegalThreadStateException("looks like this does not work");
        }
    }

    @Override
    public Class<LoadBalanceResult> getResultType() {
        return LoadBalanceResult.class;
    }

    @Override
    public LoadBalanceResult getResult(LoadBalanceData data) {
        return new LoadBalanceResult(data.n, data.sum, data.squaredSum, data.entries);
    }

    @Override
    public void writeExternal(ObjectOutput out) {
    }

    @Override
    public void readExternal(ObjectInput in) {
    }

    public static class LoadBalanceResult implements Serializable {

        private final long n;
        private final long sum;
        private final long squaredSum;
        private List<Long> entries; 

        public LoadBalanceResult(long n, long sum, long squaredSum, List<Long> entries) {
            this.n = n;
            this.sum = sum;
            this.squaredSum = squaredSum;
            this.entries = entries;
        }

        public double getMaxDeviation() {
        	double mean = sum/(double)n;
        	double max = 0.0;
        	for (int i=0; i<entries.size(); ++i) {
        		double delta = Math.abs(entries.get(i) - mean);
        		max = Math.max(delta, max);
        	}
        	return max;
        }
        
        public int getMaxDeviationGreaterThan(int max) {
        	double deviation = getMaxDeviation();
        	if (deviation > max) {
        		return (int)(deviation - max);
        	}
        	return 0;
        }
        
        public int getMeanDeviationSquaredSumRootMillis() {
            return (int)getMeanDeviationSquaredSumRoot(1_000.0);
        }

        public long getMeanDeviationSquaredSumRootMicros() {
            return getMeanDeviationSquaredSumRoot(1_000_000.0);
        }

        /**
         * Like standard deviation, but doesn't divide by n.
         * @param scaleMultiplier {@code > 0}
         * @return {@code >= 0}, {@code latexmath:[f(n) = \sqrt{\sum_{i=1}^{n} (x_i - \overline{x})^2}]} multiplied by scaleMultiplier
         */
        public long getMeanDeviationSquaredSumRoot(double scaleMultiplier) {
            // quicklatex.com: f(n) = \sqrt{\sum_{i=1}^{n} (x_i - \overline{x})^2} = \sqrt{\sum_{i=1}^{n} x_i^2 - \frac{(\sum_{i=1}^{n} x_i)^2}{n}}
            double meanDeviationSquaredSum = (double) squaredSum - ((double) (sum * sum) / n);
            return (long) (Math.sqrt(meanDeviationSquaredSum) * scaleMultiplier);
        }
    }

}