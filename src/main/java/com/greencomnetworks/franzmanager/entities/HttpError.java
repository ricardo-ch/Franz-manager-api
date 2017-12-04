package com.greencomnetworks.franzmanager.entities;

import com.fasterxml.jackson.annotation.JsonCreator;


public class HttpError {
    public final Integer code;
    public final String message;

    @JsonCreator
    public HttpError(Integer code, String message) {
        this.message = message;
        this.code = code;
    }

    @Override
    public String toString() {
        return "HttpError: [" + code + "] " + message;
    }
}
