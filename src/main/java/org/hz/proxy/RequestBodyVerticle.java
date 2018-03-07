package org.hz.proxy;

import io.netty.util.internal.StringUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;

/**
 * 请求内容整包处理
 */
public class RequestBodyVerticle extends AbstractVerticle {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RequestBodyVerticle.class);

    private static final int ACCEPT_BACKLOG = 2048;

    private static final int SEND_BUFFER_SIZE = 4096;

    private static final int RECEIVE_BUFFER_SIZE = 4096;

    private static final long BODY_LIMIT = 2048;

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
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setAcceptBacklog(ACCEPT_BACKLOG);
        serverOptions.setSendBufferSize(SEND_BUFFER_SIZE);
        serverOptions.setReceiveBufferSize(RECEIVE_BUFFER_SIZE);
        serverOptions.setUsePooledBuffers(true);
        HttpClient client = vertx.createHttpClient(new HttpClientOptions());
        HttpServer server = vertx.createHttpServer(serverOptions);
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create().setBodyLimit(BODY_LIMIT));
        router.route().handler(event -> {
            HttpServerRequest request = event.request();
            HttpServerResponse response = event.response();
            log.info("curl -X{} '{}'", request.method(), request.absoluteURI());
            HttpClientRequest clientRequest = client.request(request.method(), proxyPort, proxyHost, request.uri(), clientResponse -> {
                int statusCode = clientResponse.statusCode();
                log.info("<== StatusCode: {}", statusCode);
                MultiMap clientResponseHeaders = clientResponse.headers();
                clientResponseHeaders.forEach(e -> log.info("<== -H\"{}:{}\"", e.getKey(), e.getValue()));
                response.setChunked(true).setStatusCode(statusCode).headers().setAll(clientResponseHeaders);
                clientResponse.handler(buffer -> response.write(buffer)).endHandler((v) -> response.end());
            });
            MultiMap requestHeaders = request.headers();
            requestHeaders.forEach(e -> log.info("==> -H\"{}:{}\"", e.getKey(), e.getValue()));
            clientRequest.setChunked(true).headers().setAll(requestHeaders);
            String body = event.getBodyAsString();
            if (StringUtil.isNullOrEmpty(body)) {
                clientRequest.end();
            } else {
                log.info("==> Body: {}", body);
                clientRequest.write(body).end();
            }
        });
        server.requestHandler(router::accept).listen(serverPort);
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new RequestBodyVerticle());
    }
}
