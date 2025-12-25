package com.robinhood.util;

public class ResponseBuilder {
    public static String json(int statusCode, String jsonBody) {
        String statusText = switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 401 -> "Unauthorized";
            case 409 -> "Conflict";
            default -> "Error";
        };
        return "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
               "Content-Type: application/json\r\n" +
               "Content-Length: " + jsonBody.length() + "\r\n" +
               "\r\n" +
               jsonBody;
    }
}
