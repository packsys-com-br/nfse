package io.github.t3wv.nfse.utils;

public class NFSeHttpResponse<T> {

    private final int statusCode;
    private final T body;

    public NFSeHttpResponse(final int statusCode, final T body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int statusCode() {
        return this.statusCode;
    }

    public T body() {
        return this.body;
    }
}
