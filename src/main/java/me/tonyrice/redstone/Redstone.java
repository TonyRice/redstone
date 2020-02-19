package me.tonyrice.redstone;

import java.time.LocalTime;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;

/**
 * Redstone is a simple interface for curating automated workflows with JSON.
 */
public class Redstone {
    final private Logger logger = LoggerFactory.getLogger(Redstone.class);

    final private Vertx vertx;
    final private WebClient webClient;
    final private JsonObject wireData;

    final private Set<Wire> loadedWires = new LinkedHashSet<Wire>();

    private Wire activeWire = null;

    private Redstone(Vertx vertx, JsonObject wireData) {

        this.vertx = vertx;
        this.webClient = WebClient.create(vertx);
        this.wireData = wireData;

        for (String wireId : wireData.fieldNames()) {

            if (wireId.startsWith("_alias_")) {
                logger.debug("Registered hook alias \"" + wireId.replace("_alias_", "") + "\"");
                continue;
            }
            if (wireId.startsWith("_config_")) {
                logger.debug("Registered config variable \"" + wireId.replace("_config_", "") + "\"");
                continue;
            }

            JsonObject wireEvents = wireData.getJsonObject(wireId);

            Wire wire = new Wire(wireId, wireEvents);

            loadedWires.add(wire);

            logger.info("Loaded wire \"" + wireId + "\".");

        }

        Wire wire = wire(config().getString("default_wire", "not_found"));

        if (wire != null) {

            logger.info("Activating default wire \"" + wire.getId() + "\".");

            wire.activate();
        }
    }

    public JsonObject config() {
        JsonObject configJson = new JsonObject();

        for (String field : wireData.fieldNames()) {
            if (field.startsWith("_config_")) {
                configJson.put(field.replaceFirst("_config_", ""), wireData.getValue(field));
            }
        }

        return configJson;
    }

    public Redstone trip(String hookId) {
        if (activeWire != null){
            activeWire.trip(hookId);
        }
        return this;
    }

    public Wire live() {
        return this.activeWire;
    }

    public Wire wire(String wireId) {
        for (Wire wire : loadedWires) {
            if (wireId.equals(wire.getId())) {
                return wire;
            }
        }
        return null;
    }

    public Set<Wire> wires() {
        return loadedWires;
    }

    public class Wire {

        final private String wireId;
        final private JsonObject config;

        final private LinkedList<Map.Entry<String, Handler<AsyncResult<Void>>>> tripCache = new LinkedList<Map.Entry<String, Handler<AsyncResult<Void>>>>();
        final private Map<String, Hook> hooks = new ConcurrentHashMap<>();

        private boolean activated = false;

        Wire(String wireId, JsonObject config) {
            this.wireId = wireId;
            this.config = config;

            Set<String> wireHookIds = config.fieldNames();

            for (String wireHookId : wireHookIds) {
                if (wireHookId == "default" || wireHookId == "title" || wireHookId == "description") {
                    continue;
                }

                if (config.getValue(wireHookId) instanceof String) {
                    String aliasId = "_alias_" + config.getValue(wireHookId);
                    if (wireData.containsKey(aliasId)) {
                        JsonObject hookAliasConfig = wireData.getJsonObject(aliasId, new JsonObject());
                        Hook hook = new Hook(this, wireHookId, hookAliasConfig);
                        hooks.put(wireHookId, hook);
                    }
                } else {
                    Hook hook = new Hook(this, wireHookId, config.getJsonObject(wireHookId));
                    hooks.put(wireHookId, hook);
                }
            }
        }

        public String getId() {
            return this.wireId;
        }

        public String getTitle() {
            return config.getString("title", wireId);
        }

        public boolean active() {
            return activeWire == this;
        }

        public Wire activate() {
            logger.info("Activating wire \"" + wireId + "\".");


            Wire liveWire = live();

            if (liveWire != null) {
                if (liveWire == this) {
                    return this;
                }
                liveWire.deactivate();
            }

            // Set the active wire to this wire
            activeWire = this;

            return this.trip("activate", rs -> {
                activated = true;

                Iterator i = tripCache.iterator();
                while (i.hasNext()) {
                    Map.Entry<String, Handler<AsyncResult<Void>>> entry = (Entry<String, Handler<AsyncResult<Void>>>) i
                            .next();
                    i.remove();

                    trip(entry.getKey(), entry.getValue());

                }
            });
        }

        public Wire deactivate() {
            logger.info("Deactivating wire \"" + wireId + "\".");

            for (Hook hook : hooks.values()) {
                hook.killTimers();
            }

            if (!activated){
                return this;
            }

            activeWire = null;
            activated = false;

            return this.trip("deactivate");
        }

        public Wire trip(String hookId) {
            return trip(hookId, rs -> {

            });
        }

        public Wire trip(String hookId, Handler<AsyncResult<Void>> handler) {
            if (hooks.containsKey(hookId)) {
                if (!activated && activeWire == this && hookId != "activate") {

                    logger.info("Slowly tripping hook \"" + hookId + "\" on wire \"" + wireId + "\".");

                    tripCache.add(new AbstractMap.SimpleEntry(hookId, handler));
                    return this;
                }

                logger.info("Tripping hook \"" + hookId + "\" on wire \"" + wireId + "\".");
                Hook hook = hooks.get(hookId);
                hook.trigger(handler);
            }

            return this;
        }

        public Hook hook(String hookId) {
            if (hooks.containsKey(hookId)) {
                Hook hook = hooks.get(hookId);
                return hook;
            }

            return null;
        }

        public class Hook {
            final private Wire wire;

            final private String hookId;
            final private JsonObject config;

            final private List<Long> timerIds = new LinkedList<>();

            Hook(Wire wire, String hookId, JsonObject config) {
                this.wire = wire;

                this.hookId = hookId;
                this.config = config;
            }

            public String getId() {
                return this.hookId;
            }

            public Hook trigger() {
                return trigger(result -> {
                });
            }

            public Hook trigger(Handler<AsyncResult<Void>> completeHandler) {
                vertx.executeBlocking(event -> {
                    if (config.containsKey("delay")) {
                        long timeout = config.getLong("delay");
                        logger.info("Triggering delayed hook \"" + hookId + "\"" + " on wire \"" + wireId + "\".");

                        setTimer(timeout, (t) -> {
                            event.complete();
                        });
                        return;
                    }
                    logger.info("Triggering hook \"" + hookId + "\"" + " on wire \"" + wireId + "\".");

                    event.complete();
                }, r -> {
                    logger.info("Running hook \"" + hookId + "\"" + " on wire \"" + wireId + "\".");

                    triggerTimers();
                    triggerOutbound();

                    if(config.containsKey("hook")) {
                        Object hookVal = config.getValue("hook");

                        if (hookVal instanceof JsonObject) {
                            Hook thook = new Hook(wire, hookId + "_inline", (JsonObject) hookVal);
                            thook.trigger();
                        } else {
                            Hook hook = hook(hookVal.toString());
                            if (hook != this) {
                                hook.trigger();
                            }
                        }
                        
                    }

                    if(config.containsKey("wire")) {
                        String wireId = config.getString("wire");

                        Wire wire = wire(wireId);

                        if (wire != null) {
                            wire.activate();
                        }
                    }

                    completeHandler.handle(Future.succeededFuture());
                });

                return this;
            }

            protected Hook killTimers() {
                for (long timerId : timerIds) {
                    vertx.cancelTimer(timerId);
                }
                timerIds.clear();
                return this;
            }

            private void triggerTimers(){
                if (config.containsKey("timer")) {
                    JsonObject timerHook = config.getJsonObject("timer", new JsonObject());

                    long timeout = timerHook.getLong("delay", (long) 5000);
                    timerHook.remove("delay");
                    setTimer(timeout, (t) -> {
                        Hook thook = new Hook(wire, hookId + "_" + t, timerHook);
                        thook.trigger();
                    });
                }

                if (config.containsKey("repeat")) {

                    JsonObject repeatHook = config.getJsonObject("repeat", new JsonObject());

                    long timeout = repeatHook.getLong("delay", (long) 5000);
                    repeatHook.remove("delay");
                    long timerId = vertx.setPeriodic(timeout, (t) -> {
                        Hook thook = new Hook(wire, hookId + "_" + t, repeatHook);
                        thook.trigger();
                    });

                    timerIds.add(timerId);
                }

                if (config.containsKey("schedule")) {
                    JsonObject schedHook = config.getJsonObject("schedule", new JsonObject());

                    String when = schedHook.getString("when");
                    boolean repeat = schedHook.getBoolean("repeat", false);

                    schedHook.remove("when");

                    Handler<Void> schedHandler = new Handler<Void>() {

                        @Override
                        public void handle(Void event) {
                            long timeout = getScheduledTimeout(when);

                            setTimer(timeout, (t) -> {
                                Hook thook = new Hook(wire, hookId + "_" + t,
                                        config.getJsonObject("repeat", new JsonObject()));
                                thook.trigger();

                                if (repeat){
                                    this.handle(null);
                                }
                            });
                        }
                    };

                    schedHandler.handle(null);
                }

                if (config.containsKey("kill_timers")) {
                    JsonArray timers = config.getJsonArray("kill_timers", new JsonArray());

                    for (Object tobj : timers.getList().toArray()) {
                        String hookId = tobj.toString();
                        Hook hook = hooks.getOrDefault(hookId, null);
                        if (hook != null) {
                            hook.killTimers();
                        }
                    }
                }
            }

            private void triggerOutbound() {
                if (config.containsKey("http_get")){

                    Object value = config.getValue("http_get", "https://www.google.com");

                    List<String> urls = new LinkedList<>();
                    if (value instanceof JsonArray) {
                        for (Object obj : ((JsonArray) value).getList()) {
                            urls.add(obj.toString());
                        }
                    } else {
                        urls.add(value.toString());
                    }

                    urls.forEach(url -> {
                        logger.info("Triggering HTTP request on \"" + url + "\" on wire \"" + wireId + "\" triggered by \"" + hookId + "\".");
                        webClient.getAbs(url).send(rs -> {
                            if (rs.failed()) {
                                logger.error("Failed to send request on wire \"" + wireId + "\" triggered by \"" + hookId + "\".");
                            }
                        });
                    });
                }

                if (config.containsKey("ifttt")) {
                    Object value = config.getValue("ifttt", "redstone_event");

                    List<String> events = new LinkedList<>();

                    if (value instanceof JsonArray) {
                        for (Object obj : ((JsonArray) value).getList()) {
                            events.add(obj.toString());
                        }
                    } else {
                        events.add(value.toString());
                    }

                    events.forEach(event -> {
                        logger.info("Triggering IFTTT event \"" + event + "\" on wire \"" + wireId + "\" triggered by \"" + hookId + "\".");

                        String apiKey = config().getString("ifttt_key", "invalid");
                        String iftttUrl = "https://maker.ifttt.com/trigger/" + event
                                + "/with/key/" + apiKey;

                        webClient.getAbs(iftttUrl).ssl(true).send(rs -> {
                            if (rs.failed()) {
                                logger.error("Failed to send request on wire \"" + wireId + "\" triggered by \"" + hookId + "\".");
                            }
                        });
                    });
                }
            }

            private void setTimer(long timeout, Handler<Long> handler) {
                long timerId = vertx.setTimer(timeout, handler);

                timerIds.add(timerId);
            }

            private long getScheduledTimeout(String when) {
                LocalTime time = LocalTime.parse(when);

                Calendar now = Calendar.getInstance();
                Calendar sched = Calendar.getInstance();

                sched.set(Calendar.HOUR, time.getHour());
                sched.set(Calendar.MINUTE, time.getMinute());

                if (sched.before(now)) {
                    sched.add(Calendar.DAY_OF_MONTH, 1);
                }

                return sched.getTime().getTime() - (new Date()).getTime();
            }
        }
    }

    public static void load(Vertx vertx, String loadedWires, Handler<AsyncResult<Redstone>> handler) {
        FileSystem fs = vertx.fileSystem();
        fs.exists(loadedWires, result -> {
            if (result.failed() || !result.result()) {
                handler.handle(Future.failedFuture("The file \"" + loadedWires + "\" does not exist!"));
                return;
            }
            fs.readFile(loadedWires, fr -> {
                if (fr.failed()) {
                    handler.handle(Future.failedFuture("Could not read the file \"" + loadedWires + "\"!"));
                    return;
                }

                vertx.executeBlocking(event -> {
                    event.complete(new Redstone(vertx, new JsonObject(fr.result())));
                }, handler);
            });
        });
    }
}