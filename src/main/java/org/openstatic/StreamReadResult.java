package org.openstatic;

final class StreamReadResult {
    final byte[] data;
    final int length;
    final boolean endOfStream;
    final Exception error;

    private StreamReadResult(byte[] data, int length, boolean endOfStream, Exception error) {
        this.data = data;
        this.length = length;
        this.endOfStream = endOfStream;
        this.error = error;
    }

    static StreamReadResult data(byte[] data, int length) {
        return new StreamReadResult(data, length, false, null);
    }

    static StreamReadResult eof() {
        return new StreamReadResult(null, 0, true, null);
    }

    static StreamReadResult error(Exception error) {
        return new StreamReadResult(null, 0, false, error);
    }
}