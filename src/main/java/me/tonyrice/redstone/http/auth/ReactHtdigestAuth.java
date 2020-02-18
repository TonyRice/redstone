package me.tonyrice.redstone.http.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.htdigest.impl.HtdigestAuthImpl;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class ReactHtdigestAuth extends HtdigestAuthImpl {

    public ReactHtdigestAuth(Vertx vertx, String htdigestFile) {
        super(vertx, htdigestFile);
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> handler) {

      super.authenticate(authInfo, rs -> {
        if (rs.failed()) {
          handler.handle(Future.failedFuture(new HttpStatusException(403)));
        } else {
          handler.handle(rs);
        }
      });
    }
}