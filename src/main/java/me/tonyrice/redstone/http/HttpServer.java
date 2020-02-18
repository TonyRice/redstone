package me.tonyrice.redstone.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import me.tonyrice.redstone.Redstone;
import me.tonyrice.redstone.Redstone.Wire;
import me.tonyrice.redstone.http.auth.ReactHtdigestAuthHandler;

/**
 * An HTTP Server exposing the Redstone API.
 */
public class HttpServer extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new HttpServer());
  }

  private final Logger logger = LoggerFactory.getLogger(HttpServer.class);

  @Override
  public void start() {

    logger.info("Initializing Redstone HTTP Server...");

    Redstone.load(vertx, "./wires.json", result -> {

      if (result.failed()) {
        logger.fatal("Failed to initialize Redstone", result.cause());
        System.exit(0);
        return;
      }

      Redstone redstone = result.result();

      Router router = Router.router(vertx);

      SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
      AuthHandler authHandler = ReactHtdigestAuthHandler.create(vertx, 
        redstone.config().getString("http_auth", "httpauth"));

      router.route().handler(sessionHandler);
      router.route().handler(BodyHandler.create());

      /**
       * Inject the user into the session!
       */
      router.route().handler(rc -> {
        Session session = rc.session();
        Object userObj = session.get("user");

        if (userObj != null) {
          User user = (User) userObj;
          rc.setUser(user);
        }

        rc.next();
      });

      /**
       * Handle Login
       */
      router.get("/v1/login").handler(authHandler).handler(rc -> {
        Session session = rc.session();
        session.put("user", rc.user());

        rc.response().end("ok");
      });

      /**
       * Handle Logout
       */
      router.get("/v1/logout").handler(rc -> {
        Session session = rc.session();
        rc.clearUser();
        session.put("user", null);

        rc.response().end("ok");
      });

      /**
       * Handle Re-authorization
       */
      router.get("/v1/auth").handler(authHandler).handler(rc -> {
        rc.response().end(rc.user().principal().encode());
      });

      /**
       * Retrieve list of wires and meta data
       */
      router.get("/v1/wires").handler(authHandler).handler(rc -> {
        JsonArray wires = new JsonArray();

        redstone.wires().forEach(wire -> {
          JsonObject wireDat = new JsonObject().put("id", wire.getId()).put("title", wire.getTitle()).put("active",
              wire.active());
          wires.add(wireDat);
        });

        rc.response().end(wires.encode());
      });


      /**
       * Retrieve wire data.
       */
      router.get("/v1/wires/:wireId").handler(rc -> {
        String wireId = rc.request().getParam("wireId");
        Wire wire = redstone.wire(wireId);

        if (wire != null) {
          JsonObject wireDat = new JsonObject().put("id", wire.getId()).put("title", wire.getTitle()).put("active",
              wire.active());
          rc.response().end(wireDat.encode());
          return;
        }

        rc.response().setStatusCode(404).end();
      });

      /**
       * Activate a wire.
       */
      router.get("/v1/wires/:wireId/activate").handler(authHandler).handler(rc -> {
        String wireId = rc.request().getParam("wireId");
        Wire wire = redstone.wire(wireId);

        if (wire != null) {
          wire.activate();
          rc.response().end("OK");
          return;
        }
        rc.response().setStatusCode(404).end();

      });

      /**
       * Disable a wire.
       */
      router.get("/v1/wires/:wireId/activate").handler(authHandler).handler(rc -> {
        String wireId = rc.request().getParam("wireId");
        Wire wire = redstone.wire(wireId);

        if (wire != null) {
          wire.activate();
          rc.response().end("OK");
          return;
        }
        rc.response().setStatusCode(404).end();
      });

      String tripKey = redstone.config().getString("trip_key", "redstone");

      /**
       * Trip a hook!
       */
      router.post("/v1/trip").handler(rc -> {
        JsonObject body = rc.getBodyAsJson();

        String tripHook = body.getString("hook", "hook");
        String reqTripKey = body.getString("key", "error");

        if (reqTripKey.equals(tripKey)) {
          redstone.live().trip(tripHook);
        }

        rc.response().end("OK");
      });

      /**
       * Serve the default React app.
       */
      router.get().handler(StaticHandler.create()).handler(ctx -> {
        ctx.response().sendFile("webroot/index.html");
      });

      int http_port = redstone.config().getInteger("http_port", 8888);

      vertx.createHttpServer().requestHandler(router).listen(http_port);

      logger.info("Redstone HTTP Server listening on port " + http_port + ".");
    });
  }
}
