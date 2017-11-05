package io.undertow.util;

public class Result<T, X extends RuntimeException> {

    private T result;
    private X exception;

    public Result(T result, X exception) {
        this.result = result;
        this.exception = exception;
    }


    public boolean hasResult() {
        return result != null;
    }

    public X getException() {
        return exception;
    }

    public T getOrThrow() throws X {
        if (result == null && exception == null) {
            throw new IllegalStateException("Either result or exception should be non empty.");
        }
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    public static <T, X extends RuntimeException> Result<T, X> from(T result) {
        return new Result<T, X>(result, null);
    }

    public static <T, X extends RuntimeException> Result<T, X> from(X exception) {
        return new Result<T, X>(null, exception);
    }
}
