package org.openstatic;

import javax.sound.sampled.AudioFormat;

/**
 * Single-pole IIR low-pass de-emphasis filter for FM audio.
 *
 * FM broadcast and narrowband FM transmitters apply pre-emphasis (boosting
 * high frequencies) before transmission.  The receiver must apply the inverse
 * de-emphasis curve to restore a flat response.  Without de-emphasis the
 * demodulated audio has excessive high-frequency hiss.
 *
 * The filter is defined by a time constant (tau):
 *   - 75 µs  — ITU Region 2 (Americas, South Korea) and narrowband land-mobile radio
 *   - 50 µs  — ITU Regions 1 &amp; 3 (Europe, Asia, Africa, Oceania)
 *
 * Transfer function: H(s) = 1 / (1 + s·τ)
 * Discretised as a first-order IIR low-pass:
 *   alpha = dt / (τ + dt)       where dt = 1 / sampleRate
 *   y[n]  = y[n-1] + alpha · (x[n] - y[n-1])
 *
 * Operates in-place on 16-bit PCM audio buffers (little- or big-endian,
 * mono or multi-channel).
 */
public final class DeemphasisFilter {

    /** 75 µs — Americas / narrowband FM (default). */
    public static final double TAU_75_US = 75.0e-6;

    /** 50 µs — Europe / Asia. */
    public static final double TAU_50_US = 50.0e-6;

    private final boolean bigEndian;
    private final int channels;
    private final int frameSize;
    private final double alpha;
    private final double[] prevOutput;

    private DeemphasisFilter(boolean bigEndian, int channels, int frameSize, double alpha) {
        this.bigEndian = bigEndian;
        this.channels = channels;
        this.frameSize = frameSize;
        this.alpha = alpha;
        this.prevOutput = new double[channels];
    }

    /**
     * Create a de-emphasis filter for the given audio format and time constant.
     *
     * @param format  PCM audio format (must be 16-bit)
     * @param tauSeconds  de-emphasis time constant in seconds (e.g. {@link #TAU_75_US})
     * @return a new filter instance, or {@code null} if the format is unsupported
     */
    public static DeemphasisFilter forFormat(AudioFormat format, double tauSeconds) {
        if (format == null || format.getSampleSizeInBits() != 16 || format.getChannels() <= 0) {
            return null;
        }
        double sampleRate = format.getSampleRate();
        if (!(sampleRate > 0.0)) {
            return null;
        }
        if (!(tauSeconds > 0.0)) {
            return null;
        }

        double dt = 1.0 / sampleRate;
        double alpha = dt / (tauSeconds + dt);

        int frameSize = format.getFrameSize();
        if (frameSize <= 0) {
            frameSize = format.getChannels() * 2;
        }

        return new DeemphasisFilter(format.isBigEndian(), format.getChannels(), frameSize, alpha);
    }

    /**
     * Apply de-emphasis filtering in-place on the given PCM buffer.
     *
     * @param data    audio sample buffer (16-bit PCM)
     * @param len     number of valid bytes in {@code data}
     * @param format  audio format (used only for frame-size validation)
     */
    public void processInPlace(byte[] data, int len, AudioFormat format) {
        if (data == null || len <= 1 || format.getSampleSizeInBits() != 16 || this.channels <= 0) {
            return;
        }

        int frames = len / this.frameSize;
        int offset = 0;

        for (int frame = 0; frame < frames; frame++) {
            for (int ch = 0; ch < this.channels && offset + 1 < len; ch++) {
                int sample;
                if (this.bigEndian) {
                    int hi = data[offset];
                    int lo = data[offset + 1] & 0xFF;
                    sample = (hi << 8) | lo;
                } else {
                    int lo = data[offset] & 0xFF;
                    int hi = data[offset + 1];
                    sample = (hi << 8) | lo;
                }

                double input = sample;
                double output = this.prevOutput[ch] + this.alpha * (input - this.prevOutput[ch]);
                this.prevOutput[ch] = output;

                int filtered = (int) Math.round(output);
                if (filtered > 32767) {
                    filtered = 32767;
                } else if (filtered < -32768) {
                    filtered = -32768;
                }

                if (this.bigEndian) {
                    data[offset]     = (byte) ((filtered >>> 8) & 0xFF);
                    data[offset + 1] = (byte)  (filtered        & 0xFF);
                } else {
                    data[offset]     = (byte)  (filtered        & 0xFF);
                    data[offset + 1] = (byte) ((filtered >>> 8) & 0xFF);
                }

                offset += 2;
            }
            offset = this.frameSize * (frame + 1);
        }
    }

    /**
     * @return the time constant description for logging
     */
    public static String describeTau(double tauSeconds) {
        return String.format("%.0f µs", tauSeconds * 1.0e6);
    }
}
