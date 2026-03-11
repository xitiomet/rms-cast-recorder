package org.openstatic;

import javax.sound.sampled.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility that connects to a live Icecast/Shoutcast URL, decodes the audio, and
 * writes out "clips" whenever the stream is not silent.  Each clip is written
 * to a WAV file inside a folder named for the current date; the filename is the
 * time when the clip began.
 *
 * The detector is extremely simple: it converts the incoming stream to
 * signed 16‑bit PCM and computes a short‑term RMS value.  When the RMS drops
 * below the configured threshold for a configurable amount of time the
 * collector closes the current clip and waits for new audio.
 */
public class StreamRecorder {
    private final URL streamUrl;
    private final Path baseDir;
    private final double silenceThresholdDb;
    private final double silenceDurationSeconds;
    private volatile boolean running = true;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_DATE;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH-mm-ss");

    public StreamRecorder(URL streamUrl, Path baseDir, double silenceThresholdDb, double silenceDurationSeconds) {
        this.streamUrl = streamUrl;
        this.baseDir = baseDir;
        this.silenceThresholdDb = silenceThresholdDb;
        this.silenceDurationSeconds = silenceDurationSeconds;
    }

    public void stop() {
        running = false;
    }

    public void run() throws Exception {
        while (running) {
            try {
                System.out.println("Connecting to " + streamUrl);
                HttpURLConnection conn = (HttpURLConnection) streamUrl.openConnection();
                conn.setRequestProperty("User-Agent", "rms-cast-recorder/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                try (InputStream raw = new BufferedInputStream(conn.getInputStream())) {
                    AudioInputStream audio = AudioSystem.getAudioInputStream(raw);
                    AudioFormat baseFormat = audio.getFormat();

                    AudioFormat decodedFormat = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            baseFormat.getSampleRate(),
                            16,
                            baseFormat.getChannels(),
                            baseFormat.getChannels() * 2,
                            baseFormat.getSampleRate(),
                            false);

                    try (AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, audio)) {
                        processStream(din, decodedFormat);
                    }
                }
            } catch (Exception e) {
                System.err.println("Connection error: " + e.getMessage());
                e.printStackTrace(System.err);
                Thread.sleep(5000);
            }
        }
    }

    private void processStream(AudioInputStream din, AudioFormat format) throws IOException {
        int frameSize = format.getFrameSize();
        float frameRate = format.getFrameRate();
        long framesForSilence = (long) (silenceDurationSeconds * frameRate);

        byte[] buffer = new byte[frameSize * 1024];
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        long silentFrames = 0;
        long chunkStartTime = 0;

        int n;
        while (running && (n = din.read(buffer)) != -1) {
            boolean isSilent = isSilent(buffer, n, format);
            if (!isSilent) {
                if (chunk.size() == 0) {
                    chunkStartTime = System.currentTimeMillis();
                }
                chunk.write(buffer, 0, n);
                silentFrames = 0;
            } else {
                silentFrames += n / frameSize;
                if (silentFrames >= framesForSilence && chunk.size() > 0) {
                    writeChunk(chunk.toByteArray(), format, chunkStartTime);
                    chunk.reset();
                }
            }
        }
        // end of stream or stopped
        if (chunk.size() > 0) {
            writeChunk(chunk.toByteArray(), format, chunkStartTime);
            chunk.reset();
        }
    }

    private boolean isSilent(byte[] data, int len, AudioFormat format) {
        int sampleSize = format.getSampleSizeInBits();
        boolean bigEndian = format.isBigEndian();
        int channels = format.getChannels();
        if (sampleSize != 16) {
            // we only converted to 16‑bit; other cases should not happen
            return false;
        }
        int frames = len / format.getFrameSize();
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
                    sample = (lo) | (hi << 8);
                }
                double norm = sample / 32768.0;
                sumSq += norm * norm;
                offset += 2;
            }
        }
        double rms = Math.sqrt(sumSq / (frames * channels));
        double db = 20 * Math.log10(rms + 1e-10); // avoid log(0)
        return db < silenceThresholdDb;
    }

    private void writeChunk(byte[] audioData, AudioFormat format, long startTimeMs) {
        try {
            Instant instant = Instant.ofEpochMilli(startTimeMs);
            LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
            LocalTime time = instant.atZone(ZoneId.systemDefault()).toLocalTime();
            Path dateDir = baseDir.resolve(date.format(DATE_FMT));
            Files.createDirectories(dateDir);
            String name = time.format(TIME_FMT) + ".wav";
            Path out = dateDir.resolve(name);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
                 AudioInputStream ais = new AudioInputStream(bais, format, audioData.length / format.getFrameSize())) {
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out.toFile());
            }
            System.out.println("wrote " + out);
        } catch (IOException ioe) {
            System.err.println("failed to write chunk: " + ioe.getMessage());
            ioe.printStackTrace(System.err);
        }
    }
}
