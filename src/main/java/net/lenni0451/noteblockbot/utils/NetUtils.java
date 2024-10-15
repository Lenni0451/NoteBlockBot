package net.lenni0451.noteblockbot.utils;

import net.lenni0451.commons.httpclient.HttpClient;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.Headers;

import java.io.IOException;

public class NetUtils {

    private static final HttpClient CLIENT = new HttpClient()
            .setHeader(Headers.USER_AGENT, "NoteblockWorldBot");

    public static HttpResponse get(final String url) throws IOException {
        HttpResponse response = CLIENT.get(url).execute();
        if (response.getStatusCode() / 100 != 2) {
            throw new IOException("Failed to get: " + response.getStatusCode() + " - " + response.getStatusMessage());
        } else {
            return response;
        }
    }

}
