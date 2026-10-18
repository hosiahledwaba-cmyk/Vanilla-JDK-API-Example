package com.robinhood.util;

import java.nio.charset.StandardCharsets;

public class ResponseBuilder {

    public static String json(int statusCode, String jsonBody) {
        String statusText = switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 429 -> "Too Many Requests";
            default -> "Error";
        };
        if (jsonBody == null) jsonBody = "{}";
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        return "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
               "Content-Type: application/json; charset=utf-8\r\n" +
               "Content-Length: " + bytes.length + "\r\n" +
               "Connection: close\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Access-Control-Allow-Headers: *\r\n" +
               "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
               "\r\n" +
               jsonBody;
    }

    public static String text(int statusCode, String textBody) {
        String statusText = (statusCode == 200) ? "OK" : "Error";
        if (textBody == null) textBody = "";
        byte[] bytes = textBody.getBytes(StandardCharsets.UTF_8);
        return "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
               "Content-Type: text/plain; charset=utf-8\r\n" +
               "Content-Length: " + bytes.length + "\r\n" +
               "Connection: close\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "\r\n" +
               textBody;
    }

    public static String file(String contentType, byte[] data) {
        if (data == null) data = new byte[0];
        String body = new String(data, StandardCharsets.UTF_8);
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: " + contentType + "\r\n" +
               "Content-Length: " + data.length + "\r\n" +
               "Connection: close\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "\r\n" +
               body;
    }

    public static String html(int statusCode, String htmlBody) {
        if (htmlBody == null) htmlBody = "";
        byte[] bytes = htmlBody.getBytes(StandardCharsets.UTF_8);
        return "HTTP/1.1 " + statusCode + " OK\r\n" +
               "Content-Type: text/html; charset=utf-8\r\n" +
               "Content-Length: " + bytes.length + "\r\n" +
               "Connection: close\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "\r\n" +
               htmlBody;
    }
}
