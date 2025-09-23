package com.nnpg.glazed.utils;

import java.util.Random;

public class RandomBetweenDouble {
    private static final Random random = new Random();

    public final double min;
    public final double max;

    public RandomBetweenDouble(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("Min value cannot be greater than max value");
        }
        this.min = min;
        this.max = max;
    }

    public double getRandom() {
        if (min == max) return min;
        return min + (max - min) * random.nextDouble();
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RandomBetweenDouble that = (RandomBetweenDouble) obj;
        return Double.compare(that.min, min) == 0 && Double.compare(that.max, max) == 0;
    }

    @Override
    public int hashCode() {
        long minBits = Double.doubleToLongBits(min);
        long maxBits = Double.doubleToLongBits(max);
        return (int) (31 * minBits + maxBits);
    }

    @Override
    public String toString() {
        return String.format("%.2f - %.2f", min, max);
    }
}