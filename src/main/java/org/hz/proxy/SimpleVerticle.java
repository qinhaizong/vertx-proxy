package org.hz.proxy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServer;
import org.slf4j.Logger;

/**
 * 简单代理，不对请求内容处理
 */
public class SimpleVerticle extends AbstractVerticle {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SimpleVerticle.class);

    /**
     * 服务端端口
     */
    private int serverPort = 8763;
    /**
     * 被代理服务器端口
     */
    private int proxyPort = 8761;
    /**
     * 被代理主机
     */
    private String proxyHost = "localhost";

    @Override
    public void start() throws Exception {
        HttpClient client = vertx.createHttpClient(new HttpClientOptions());
        HttpServer server = vertx.createHttpServer();
        server.requestHandler(request -> {
            log.info("curl -X{} '{}'", request.method(), request.absoluteURI());
            HttpClientRequest clientRequest = client.request(request.method(), proxyPort, proxyHost, request.uri(), clientResponse -> {
                int statusCode = clientResponse.statusCode();
                log.info("<== statusCode: {}", statusCode);
                MultiMap clientResponseHeaders = clientResponse.headers();
                clientResponseHeaders.forEach(e -> log.info("<== -H\"{}:{}\"", e.getKey(), e.getValue()));
                request.response().setChunked(true).setStatusCode(statusCode).headers().setAll(clientResponseHeaders);
                clientResponse.handler(data -> request.response().write(data))
                        .endHandler((v) -> request.response().end());
            });
            MultiMap requestHeaders = request.headers();
            requestHeaders.forEach(e -> log.info("==> -H\"{}:{}\"", e.getKey(), e.getValue()));
            clientRequest.setChunked(true).headers().setAll(requestHeaders);
            request.handler(buffer -> {
                log.info("==> {}", buffer.toString("UTF-8"));
                clientRequest.write(buffer);
            }).endHandler((v) -> clientRequest.end());
        }).listen(serverPort);
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new SimpleVerticle());
    }
}
