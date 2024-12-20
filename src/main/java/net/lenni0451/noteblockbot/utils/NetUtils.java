package net.lenni0451.noteblockbot.utils;

import net.lenni0451.commons.httpclient.HttpClient;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.constants.Headers;
import net.lenni0451.commons.httpclient.requests.HttpRequest;
import net.lenni0451.commons.httpclient.requests.impl.GetRequest;

import java.io.IOException;
import java.util.function.Consumer;

public class NetUtils {

    private static final HttpClient CLIENT = new HttpClient()
            .setHeader(Headers.USER_AGENT, "NoteBlockBot");

    public static HttpResponse get(final String url) throws IOException {
        return get(request -> {}, url);
    }

    public static HttpResponse get(final Consumer<HttpRequest> requestModifier, final String url) throws IOException {
        GetRequest request = CLIENT.get(url);
        requestModifier.accept(request);
        HttpResponse response = request.execute();
        if (response.getStatusCode() / 100 != 2) {
            throw new IOException("Failed to get: " + response.getStatusCode() + " - " + response.getStatusMessage() + ": " + response.getContentAsString());
        } else {
            return response;
        }
    }

}
