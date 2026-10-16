package run.endive.cm.wasi.p2.io.streams;

import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamError;
import run.endive.cm.wasi.p2.bindings.wasi.io.streams.StreamErrorException;
import run.endive.cm.wasi.p2.io.error.HostError;

/**
 * The two ways a stream operation fails, as the exception the bindings turn into a {@code
 * stream-error}.
 *
 * @see <a href="https://github.com/WebAssembly/WASI/blob/v0.2.12/proposals/io/wit/streams.wit">wasi:io/streams</a>
 */
public final class StreamErrors {

    private StreamErrors() {}

    /** The stream is closed, and every later operation fails the same way. */
    public static StreamErrorException closed() {
        return new StreamErrorException(new StreamError.Closed());
    }

    /** The last operation failed because of {@code cause}, after which the stream is closed. */
    public static StreamErrorException failed(Throwable cause) {
        return new StreamErrorException(new StreamError.LastOperationFailed(new HostError(cause)));
    }
}
