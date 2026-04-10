package org.openstatic;

import javax.sound.sampled.AudioFormat;

/**
 * Voice band-pass filter using cascaded 2nd-order Butterworth biquad sections.
 *
 * Implements a high-pass at 300 Hz and a low-pass at 3400 Hz, each as a
 * 2nd-order Butterworth biquad (12 dB/octave rolloff).  This provides much
 * steeper attenuation of out-of-band noise compared to 1st-order IIR filters
 * (6 dB/octave), significantly reducing FM hiss above 3.4 kHz and low-frequency
 * hum below 300 Hz.
 *
 * Operates in-place on 16-bit PCM audio buffers (little- or big-endian,
 * mono or multi-channel).
 */
public final class VoiceBandPassFilter {

    private static final double DEFAULT_LOW_CUTOFF_HZ = 300.0;
    private static final double DEFAULT_HIGH_CUTOFF_HZ = 3400.0;

    private final boolean bigEndian;
    private final int channels;
    private final int frameSize;

    // High-pass biquad coefficients (normalised: a0 = 1)
    private final double hpB0, hpB1, hpB2;
    private final double hpA1, hpA2;

    // Low-pass biquad coefficients (normalised: a0 = 1)
    private final double lpB0, lpB1, lpB2;
    private final double lpA1, lpA2;

    // Per-channel state (Direct Form II Transposed: two delay elements per biquad)
    private final double[] hpZ1;
    private final double[] hpZ2;
    private final double[] lpZ1;
    private final double[] lpZ2;

    private VoiceBandPassFilter(boolean bigEndian, int channels, int frameSize,
                                double hpB0, double hpB1, double hpB2,
                                double hpA1, double hpA2,
                                double lpB0, double lpB1, double lpB2,
                                double lpA1, double lpA2) {
        this.bigEndian = bigEndian;
        this.channels = channels;
        this.frameSize = frameSize;
        this.hpB0 = hpB0;
        this.hpB1 = hpB1;
        this.hpB2 = hpB2;
        this.hpA1 = hpA1;
        this.hpA2 = hpA2;
        this.lpB0 = lpB0;
        this.lpB1 = lpB1;
        this.lpB2 = lpB2;
        this.lpA1 = lpA1;
        this.lpA2 = lpA2;
        this.hpZ1 = new double[channels];
        this.hpZ2 = new double[channels];
        this.lpZ1 = new double[channels];
        this.lpZ2 = new double[channels];
    }

    /**
     * Create a voice band-pass filter for the given audio format.
     *
     * @param format  PCM audio format (must be 16-bit)
     * @return a new filter instance, or {@code null} if the format is unsupported
     */
    public static VoiceBandPassFilter forFormat(AudioFormat format) {
        if (format == null || format.getSampleSizeInBits() != 16 || format.getChannels() <= 0) {
            return null;
        }
        double sampleRate = format.getSampleRate();
        if (!(sampleRate > 0.0)) {
            return null;
        }

        double lowCut = Math.max(50.0, Math.min(DEFAULT_LOW_CUTOFF_HZ, sampleRate * 0.45));
        double highCut = Math.max(lowCut + 100.0, Math.min(DEFAULT_HIGH_CUTOFF_HZ, sampleRate * 0.49));
        if (highCut <= lowCut) {
            return null;
        }

        int fs = format.getFrameSize();
        if (fs <= 0) {
            fs = format.getChannels() * 2;
        }

        // --- 2nd-order Butterworth high-pass (bilinear transform) ---
        double wHP = Math.tan(Math.PI * lowCut / sampleRate);
        double wHP2 = wHP * wHP;
        double sqrt2 = Math.sqrt(2.0);
        double hpNorm = 1.0 / (1.0 + sqrt2 * wHP + wHP2);
        double hpB0 =  hpNorm;
        double hpB1 = -2.0 * hpNorm;
        double hpB2 =  hpNorm;
        double hpA1 =  2.0 * (wHP2 - 1.0) * hpNorm;
        double hpA2 =  (1.0 - sqrt2 * wHP + wHP2) * hpNorm;

        // --- 2nd-order Butterworth low-pass (bilinear transform) ---
        double wLP = Math.tan(Math.PI * highCut / sampleRate);
        double wLP2 = wLP * wLP;
        double lpNorm = 1.0 / (1.0 + sqrt2 * wLP + wLP2);
        double lpB0 = wLP2 * lpNorm;
        double lpB1 = 2.0 * wLP2 * lpNorm;
        double lpB2 = wLP2 * lpNorm;
        double lpA1 = 2.0 * (wLP2 - 1.0) * lpNorm;
        double lpA2 = (1.0 - sqrt2 * wLP + wLP2) * lpNorm;

        return new VoiceBandPassFilter(format.isBigEndian(), format.getChannels(), fs,
                hpB0, hpB1, hpB2, hpA1, hpA2,
                lpB0, lpB1, lpB2, lpA1, lpA2);
    }

    /**
     * Apply the band-pass filter in-place on the given PCM buffer.
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
                // Read 16-bit sample
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

                // High-pass biquad (Direct Form II Transposed)
                double hpOut = hpB0 * input + hpZ1[ch];
                hpZ1[ch] = hpB1 * input - hpA1 * hpOut + hpZ2[ch];
                hpZ2[ch] = hpB2 * input - hpA2 * hpOut;

                // Low-pass biquad (Direct Form II Transposed)
                double lpOut = lpB0 * hpOut + lpZ1[ch];
                lpZ1[ch] = lpB1 * hpOut - lpA1 * lpOut + lpZ2[ch];
                lpZ2[ch] = lpB2 * hpOut - lpA2 * lpOut;

                // Clamp and write back
                int filtered = (int) Math.round(lpOut);
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
}
