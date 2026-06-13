package com.ca.tunaro.utils;

import com.google.gson.Gson;

import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;

import se.michaelthelin.spotify.IHttpManager;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.BadGatewayException;
import se.michaelthelin.spotify.exceptions.detailed.BadRequestException;
import se.michaelthelin.spotify.exceptions.detailed.ForbiddenException;
import se.michaelthelin.spotify.exceptions.detailed.InternalServerErrorException;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.exceptions.detailed.ServiceUnavailableException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;

// Replaces SpotifyHttpManager's shared BasicHttpClientConnectionManager with separate
// PoolingHttpClientConnectionManager instances for the caching and non-caching clients.
// The shared Basic manager causes "connection is still allocated" errors when concurrent
// requests hit the same SpotifyApi instance.
public class PoolingSpotifyHttpManager implements IHttpManager {

    private static final Gson GSON = new Gson();

    private final CloseableHttpClient httpClient;
    private final CloseableHttpClient httpClientCaching;

    public PoolingSpotifyHttpManager() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.STRICT)
                .build();

        CacheConfig cacheConfig = CacheConfig.custom()
                .setMaxCacheEntries(1000)
                .setMaxObjectSize(8192)
                .setSharedCache(false)
                .build();

        PoolingHttpClientConnectionManager connManagerRegular = new PoolingHttpClientConnectionManager();
        connManagerRegular.setMaxTotal(10);
        connManagerRegular.setDefaultMaxPerRoute(10);

        PoolingHttpClientConnectionManager connManagerCaching = new PoolingHttpClientConnectionManager();
        connManagerCaching.setMaxTotal(10);
        connManagerCaching.setDefaultMaxPerRoute(10);

        this.httpClient = HttpClients.custom()
                .disableContentCompression()
                .setConnectionManager(connManagerRegular)
                .setDefaultRequestConfig(requestConfig)
                .build();

        this.httpClientCaching = CachingHttpClients.custom()
                .setCacheConfig(cacheConfig)
                .disableContentCompression()
                .setConnectionManager(connManagerCaching)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Override
    public String get(URI uri, Header[] headers) throws IOException, SpotifyWebApiException, ParseException {
        HttpGet request = new HttpGet(uri);
        request.setHeaders(headers);
        String body = getResponseBody(execute(httpClientCaching, request));
        request.reset();
        return body;
    }

    @Override
    public String post(URI uri, Header[] headers, HttpEntity body) throws IOException, SpotifyWebApiException, ParseException {
        HttpPost request = new HttpPost(uri);
        request.setHeaders(headers);
        request.setEntity(body);
        String response = getResponseBody(execute(httpClient, request));
        request.reset();
        return response;
    }

    @Override
    public String put(URI uri, Header[] headers, HttpEntity body) throws IOException, SpotifyWebApiException, ParseException {
        HttpPut request = new HttpPut(uri);
        request.setHeaders(headers);
        request.setEntity(body);
        String response = getResponseBody(execute(httpClient, request));
        request.reset();
        return response;
    }

    @Override
    public String delete(URI uri, Header[] headers, HttpEntity body) throws IOException, SpotifyWebApiException, ParseException {
        HttpDelete request = new HttpDelete(uri);
        request.setHeaders(headers);
        request.setEntity(body);
        String response = getResponseBody(execute(httpClient, request));
        request.reset();
        return response;
    }

    private CloseableHttpResponse execute(CloseableHttpClient client, ClassicHttpRequest request) throws IOException {
        HttpCacheContext context = HttpCacheContext.create();
        CloseableHttpResponse response = client.execute(request, context);
        try {
            CacheResponseStatus status = context.getCacheResponseStatus();
            if (status != null) {
                SpotifyApi.LOGGER.log(Level.CONFIG, "Cache status: " + status);
            }
        } catch (Exception ignored) {}
        return response;
    }

    private String getResponseBody(CloseableHttpResponse response) throws IOException, SpotifyWebApiException, ParseException {
        final String body = response.getEntity() != null
                ? EntityUtils.toString(response.getEntity(), "UTF-8")
                : null;
        String errorMessage = response.getReasonPhrase();

        if (body != null && !body.isEmpty()) {
            try {
                com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(body);
                if (el.isJsonObject()) {
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    if (obj.has("error")) {
                        if (obj.has("error_description")) {
                            errorMessage = obj.get("error_description").getAsString();
                        } else if (obj.get("error").isJsonObject() && obj.getAsJsonObject("error").has("message")) {
                            errorMessage = obj.getAsJsonObject("error").get("message").getAsString();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        switch (response.getCode()) {
            case 400: throw new BadRequestException(errorMessage);
            case 401: throw new UnauthorizedException(errorMessage);
            case 403: throw new ForbiddenException(errorMessage);
            case 404: throw new NotFoundException(errorMessage);
            case 429:
                Header retryAfter = response.getFirstHeader("Retry-After");
                if (retryAfter != null) throw new TooManyRequestsException(errorMessage, Integer.parseInt(retryAfter.getValue()));
                throw new TooManyRequestsException(errorMessage);
            case 500: throw new InternalServerErrorException(errorMessage);
            case 502: throw new BadGatewayException(errorMessage);
            case 503: throw new ServiceUnavailableException(errorMessage);
            default: return body;
        }
    }
}
