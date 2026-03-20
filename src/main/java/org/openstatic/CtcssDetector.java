package org.openstatic;

import javax.sound.sampled.AudioFormat;

public final class CtcssDetector {
    private static final double MIN_TONE_HZ = 50.0;
    private static final double MAX_TONE_HZ = 300.0;
    private static final double ANALYSIS_WINDOW_SECONDS = 0.2;
    private static final int OPEN_MATCH_WINDOWS = 2;
    private static final int CLOSE_MISS_WINDOWS = 3;
    private static final double MIN_TARGET_AMPLITUDE = 0.0025;
    private static final double DOMINANCE_RATIO = 1.6;
    private static final double NEIGHBOR_OFFSET_HZ = 4.0;

    private final double sampleRate;
    private final double targetHz;
    private final double lowerNeighborHz;
    private final double upperNeighborHz;
    private final int windowSamples;
    private final double[] sampleWindow;
    private int sampleIndex;
    private int matchWindows;
    private int missWindows;
    private boolean detected;

    public CtcssDetector(AudioFormat format, double targetHz) {
        if (format.getSampleRate() <= 0) {
            throw new IllegalArgumentException("Invalid sample rate for CTCSS detector");
        }

        this.sampleRate = format.getSampleRate();
        this.targetHz = targetHz;
        this.lowerNeighborHz = chooseLowerNeighbor(targetHz);
        this.upperNeighborHz = chooseUpperNeighbor(targetHz);
        this.windowSamples = Math.max(200, (int) Math.round(this.sampleRate * ANALYSIS_WINDOW_SECONDS));
        this.sampleWindow = new double[this.windowSamples];
        this.sampleIndex = 0;
        this.matchWindows = 0;
        this.missWindows = 0;
        this.detected = false;
    }

    public boolean consume(byte[] data, int len, AudioFormat format) {
        if (format.getSampleSizeInBits() != 16) {
            return false;
        }

        int frameSize = format.getFrameSize();
        int channels = format.getChannels();
        boolean bigEndian = format.isBigEndian();
        int frames = len / frameSize;
        int offset = 0;

        for (int i = 0; i < frames; i++) {
            double mixedSample = 0.0;
            for (int ch = 0; ch < channels; ch++) {
                int sample;
                if (bigEndian) {
                    int hi = data[offset];
                    int lo = data[offset + 1] & 0xff;
                    sample = (hi << 8) | lo;
                } else {
                    int lo = data[offset] & 0xff;
                    int hi = data[offset + 1];
                    sample = (hi << 8) | lo;
                }
                mixedSample += sample / 32768.0;
                offset += 2;
            }

            this.sampleWindow[this.sampleIndex++] = mixedSample / channels;
            if (this.sampleIndex >= this.windowSamples) {
                analyzeWindow();
                this.sampleIndex = 0;
            }
        }

        return this.detected;
    }

    private static double chooseLowerNeighbor(double targetHz) {
        double candidate = Math.max(MIN_TONE_HZ, targetHz - NEIGHBOR_OFFSET_HZ);
        if (Math.abs(candidate - targetHz) < 0.001) {
            candidate = Math.min(MAX_TONE_HZ, targetHz + (NEIGHBOR_OFFSET_HZ * 2.0));
        }
        return candidate;
    }

    private static double chooseUpperNeighbor(double targetHz) {
        double candidate = Math.min(MAX_TONE_HZ, targetHz + NEIGHBOR_OFFSET_HZ);
        if (Math.abs(candidate - targetHz) < 0.001) {
            candidate = Math.max(MIN_TONE_HZ, targetHz - (NEIGHBOR_OFFSET_HZ * 2.0));
        }
        return candidate;
    }

    private void analyzeWindow() {
        double mean = 0.0;
        for (int i = 0; i < this.windowSamples; i++) {
            mean += this.sampleWindow[i];
        }
        mean /= this.windowSamples;

        double targetAmp = goertzelAmplitude(this.sampleWindow, this.windowSamples, this.targetHz, this.sampleRate, mean);
        double lowerAmp = goertzelAmplitude(this.sampleWindow, this.windowSamples, this.lowerNeighborHz, this.sampleRate, mean);
        double upperAmp = goertzelAmplitude(this.sampleWindow, this.windowSamples, this.upperNeighborHz, this.sampleRate, mean);

        boolean match = targetAmp >= MIN_TARGET_AMPLITUDE
                && targetAmp >= (lowerAmp * DOMINANCE_RATIO)
                && targetAmp >= (upperAmp * DOMINANCE_RATIO);

        if (match) {
            this.matchWindows++;
            this.missWindows = 0;
        } else {
            this.missWindows++;
            this.matchWindows = 0;
        }

        if (!this.detected && this.matchWindows >= OPEN_MATCH_WINDOWS) {
            this.detected = true;
        } else if (this.detected && this.missWindows >= CLOSE_MISS_WINDOWS) {
            this.detected = false;
        }
    }

    private static double goertzelAmplitude(double[] samples,
                                            int sampleCount,
                                            double targetHz,
                                            double sampleRate,
                                            double mean) {
        double omega = (2.0 * Math.PI * targetHz) / sampleRate;
        double coeff = 2.0 * Math.cos(omega);
        double prev = 0.0;
        double prev2 = 0.0;

        for (int i = 0; i < sampleCount; i++) {
            double centered = samples[i] - mean;
            double next = centered + (coeff * prev) - prev2;
            prev2 = prev;
            prev = next;
        }

        double power = (prev2 * prev2) + (prev * prev) - (coeff * prev * prev2);
        if (power < 0.0) {
            power = 0.0;
        }

        return (2.0 * Math.sqrt(power)) / sampleCount;
    }
}