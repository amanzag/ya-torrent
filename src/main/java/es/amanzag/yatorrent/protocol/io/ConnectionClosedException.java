package es.amanzag.yatorrent.protocol.io;

public class ConnectionClosedException extends RuntimeException {

    public ConnectionClosedException() {
    }

    public ConnectionClosedException(String message) {
        super(message);
    }

    public ConnectionClosedException(Throwable cause) {
        super(cause);
    }

    public ConnectionClosedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionClosedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
