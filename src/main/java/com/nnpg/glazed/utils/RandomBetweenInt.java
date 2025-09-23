package com.nnpg.glazed.utils;

import java.util.Random;

public class RandomBetweenInt {
    private static final Random random = new Random();

    public final int min;
    public final int max;

    public RandomBetweenInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("Min value cannot be greater than max value");
        }
        this.min = min;
        this.max = max;
    }

    public int getRandom() {
        if (min == max) return min;
        return random.nextInt(max - min + 1) + min;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RandomBetweenInt that = (RandomBetweenInt) obj;
        return min == that.min && max == that.max;
    }

    @Override
    public int hashCode() {
        return 31 * min + max;
    }

    @Override
    public String toString() {
        return min + " - " + max;
    }
}