package me.tonyrice.redstone.http.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.htdigest.HtdigestAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.DigestAuthHandlerImpl;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class ReactHtdigestAuthHandler extends DigestAuthHandlerImpl {

    public static AuthHandler create(Vertx vertx, String filename) {
        AuthHandler authHandler = new ReactHtdigestAuthHandler(new ReactHtdigestAuth(vertx, filename), 3600000);
        return authHandler;
    }

    public ReactHtdigestAuthHandler(HtdigestAuth authProvider, long nonceExpireTimeout) {
        super(authProvider, nonceExpireTimeout);
    }


    @Override
    public void parseCredentials(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {

      super.parseCredentials(context, rs -> {
        if (rs.failed()) {
          String authHeadher = super.authenticateHeader(context);
          context.response().putHeader("WWW-Authenticate", authHeadher);
          handler.handle(Future.failedFuture(new HttpStatusException(403)));
        } else {
          handler.handle(rs);
        }
      });
    }
}