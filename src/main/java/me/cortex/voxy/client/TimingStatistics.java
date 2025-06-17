package me.cortex.voxy.client;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;

public class TimingStatistics {
    public static double ROLLING_WEIGHT = 0.96;
    private static final ArrayList<TimeSampler> allSamplers = new ArrayList<>();
    public static final class TimeSampler {
        private boolean running;
        private long timestamp;
        private long runtime;

        private double rolling;

        public TimeSampler() {
            TimingStatistics.allSamplers.add(this);
        }

        private void reset() {
            if (this.running) {
                throw new IllegalStateException();
            }
            this.runtime = 0;
        }

        public void start() {
            if (this.running) {
                throw new IllegalStateException();
            }
            this.running = true;
            this.timestamp = System.nanoTime();
        }

        public void stop() {
            if (!this.running) {
                throw new IllegalStateException();
            }
            this.running = false;
            this.runtime += System.nanoTime() - this.timestamp;
        }

        public void subtract(TimeSampler sampler) {
            this.runtime -= sampler.runtime;
        }

        private void update() {
            double time = ((double) (this.runtime / 1000)) / 1000;
            this.rolling = Math.max(this.rolling * ROLLING_WEIGHT + time * (1-ROLLING_WEIGHT), time);
        }

        public double getRolling() {
            return this.rolling;
        }

        public String pVal() {
            return String.format("%6.3f", this.rolling);
        }
    }

    public static void resetSamplers() {
        TimingStatistics.allSamplers.forEach(TimeSampler::reset);
    }

    private static void updateSamplers() {
        TimingStatistics.allSamplers.forEach(TimeSampler::update);
    }

    public static TimeSampler all = new TimeSampler();
    public static TimeSampler main = new TimeSampler();
    public static TimeSampler dynamic = new TimeSampler();
    public static TimeSampler postDynamic = new TimeSampler();

    public static TimeSampler A = new TimeSampler();
    public static TimeSampler B = new TimeSampler();
    public static TimeSampler C = new TimeSampler();
    public static TimeSampler D = new TimeSampler();

    public static TimeSampler E = new TimeSampler();
    public static TimeSampler F = new TimeSampler();
    public static TimeSampler G = new TimeSampler();
    public static TimeSampler H = new TimeSampler();
    public static TimeSampler I = new TimeSampler();


    public static void update() {
        updateSamplers();
    }

}
