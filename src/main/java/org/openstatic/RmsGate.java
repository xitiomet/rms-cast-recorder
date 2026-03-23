package org.openstatic;

import javax.sound.sampled.AudioFormat;

public class RmsGate {
    private final double thresholdDb;

    public RmsGate(double thresholdDb) {
        this.thresholdDb = thresholdDb;
    }

    public Result evaluate(byte[] data, int len, AudioFormat format) {
        double rmsDb = calculateRmsDb(data, len, format);
        boolean open = rmsDb >= this.thresholdDb;
        return new Result(rmsDb, open);
    }

    private static double calculateRmsDb(byte[] data, int len, AudioFormat format) {
        int sampleSize = format.getSampleSizeInBits();
        boolean bigEndian = format.isBigEndian();
        int channels = format.getChannels();
        int frameSize = format.getFrameSize();
        if (sampleSize != 16 || channels <= 0 || frameSize <= 0 || len <= 0) {
            return -100.0;
        }

        int frames = len / frameSize;
        if (frames <= 0) {
            return -100.0;
        }

        double sumSq = 0.0;
        int offset = 0;
        for (int i = 0; i < frames; i++) {
            for (int ch = 0; ch < channels; ch++) {
                int lo = data[offset] & 0xff;
                int hi = data[offset + 1];
                int sample;
                if (bigEndian) {
                    sample = (hi << 8) | lo;
                } else {
                    sample = lo | (hi << 8);
                }
                double norm = sample / 32768.0;
                sumSq += norm * norm;
                offset += 2;
            }
        }
        double rms = Math.sqrt(sumSq / (frames * channels));
        return 20 * Math.log10(rms + 1e-10);
    }

    public static final class Result {
        private final double rmsDb;
        private final boolean open;

        private Result(double rmsDb, boolean open) {
            this.rmsDb = rmsDb;
            this.open = open;
        }

        public double getRmsDb() {
            return rmsDb;
        }

        public boolean isOpen() {
            return open;
        }
    }
}