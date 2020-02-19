# Redstone

## Simple automated workflow curation using JSON and HTTP.

### Automated Workflow Curation? 🤔
Redstone acts as a curator for existing automated workflows powered by systems like IFTTT.
Workflows are easily configured using a simple JSON based configuration.
Each workflow is represented as a `wire` and workflow actions are reprsented in a `hook`.

TLDR; Automated workflows for automated workflows using JSON and HTTP...
 
### System Requirements

* Apache Maven
* JDK 8+
* Linux or Mac OSX

### Running Redstone 

If you don't already have Maven installed and configured, you can find an easy guide [here](https://www.educative.io/edpresso/how-to-install-maven-in-windows-and-linux-unix).

```bash
# Running in Foreground
mvn compile vertx:run

# Running in Background
mvn clean install vertx:start

# Stopping Background Service
mvn vertx:stop

# Compiling a JAR
mvn package
```
Once the server has started, you can login to the Redstone UI over at [http://127.0.0.0.1:9999/login](http://127.0.0.0.1:9999/login) with the default username `redstone` and password `redstone`.

### Wiring Redstone
Configuring Redstone wires is fairly easy. Like I mentioned above, the `wires.json` file houses the entire configuration for the system.

Below is an example of a `wire.json` configuration with two wires to be activated at bed time and in the morning.

```javascript
{
    "bedtime_wire": {
        "title" : "Bed Time",
        "activate": {
            "ifttt" : ["dim_lights", "do_some_stuff", "phone_on_silent"],
            "schedule" : {
                "when" :"08:00",
                "wire" : "daytime_wire"
            }
        }
        "received_email": {
            "ifttt": "digest_emails",
            "http_get": "https://api.mywebsite.com/batch_digest" 
        },
        "my_event": "do_something",
        "slack_message" : {
            "ifttt": "slack_to_email"
        },
        "deactivate": {
            "ifttt" : "disable_something"
        }
    },
    "daytime_wire": {
        "title" : "Day Time",
        "activate": {
            "ifttt" : ["daily_digest", "batch_todos", "phone_off_silent"],
            "schedule" : {
                "when" :"22:00",
                "wire" : "bedtime_wire"
            }
        },
        "slack_message": {
            "ifttt": "slack_sms"
        },
        "my_event": "do_something",
        "received_email" : {
            "ifttt" : ["slack_to_email", "digest_email_summary"]
        }
    },
    "_alias_do_something" : {
        "http_get": "https:/10.10.1.15:8888/my_api" 
    },
    "_config_default_wire": "daytime_wire",
    "_config_http_port" : 9999,
    "_config_http_host" : "127.0.0.1",
    "_config_http_auth" : "httpauth",
    "_config_trip_key"  : "NOTSECURE"
}
````

### Redstone Hooks

A hook is a set of workflow actions that are triggered when a hook is tripped. By default there are two built-in hooks `deactivate` and `activate` that are tripped by Redstone itself.

The following workflow actions are currently supported:

* **timer** - Initiates a workflow action on a timer
* **repeat** - Repeats a workflow action on a timer
* **schedule** - Schedules a workflow action at a later date
* **ifttt** - Triggers an IFTTT event.
* **http_get** - Initiates an http GET request
* **wire** - Activates the specified wire (disables the currently active one)
* **hook** - Trips the specified hook or triggers an inline hook.


Here is an example hook:

```javascript
{
    "motion_hook": {
        "timer": {
            "delay": 60000,
            "ifttt": "trigger_event"
        },
        "repeat": {
            "delay": 15000,
            "ifttt": "trigger_event"
        },
        "ifttt": "trigger_event"
    }
}
```
The above hook will trigger the IFTTT event "trigger_event" immediately, again in 60s, and repeat every 15s.

### Tripping Redstone Hooks

The REST API is used trip redstone hooks, aka initiate a workflow action. 
Hooks are only able to be triggered on the active wire. If a hook does not exist on the active wire, it will not be triggered.

```bash
# Tripping a hook with curl
curl --header "Content-Type: application/json" --request POST \
  --data '{"hook":"my_event","key":"NOTSECURE"}' \
  http://localhost:9999/v1/trip
```

The `_config_trip_key` is used as a simple identifier when receiving trip events. 
Hooks are only tripped via HTTP when the key is passed.

Redstone is not designed to be 100% secure out of the box. 
Any trip events received over HTTP should never be trusted, therefore it is reccomended that you 
configure a proxy with TLS enabled.

### Configuration Variables

As you can see in the example above example, wires are identified by their root key, for example `bedtime_wire`. Any value starting with `_config_` is treated as a configuration variable. 
This design makes it easy to bundle configurations within the `wire.json` file.

Below is a list of currently supported configuration variables

* **_config_default_wire** - The default wire to be activated on startup. (**Note: A single wire is currently allowed to be active.**)
* **_config_http_port** - The http server port to be used. 
* **_config_http_host** - The http server host to be used. 
* **_config_http_auth** - The file to be used for htdigest authentication within the HTTP Server.

### The Redstone UI

At the moment Redstone includes a simple UI for activating wires. The Redstone UI uses HTTP Digest authentication that can be easily configured with the `htdigest` command. You can find instructions on how to install it [here](https://www.npmjs.com/package/htdigest).

```bash
# Create a new htdigest file with the user myuser

htdigest -c httpauth redstone myuser

# Adding new users

htdigest httpauth redstone myuser2
```

#### Contributions

At the moment I have not defined any contribution guidelines because there isn't a need. If anyone wishes to contribute, feel free to submit an issue, PR, or just shoot me an email over at tony@tonyrice.me

All contributions are welcome!

##### TODO
 * Tests
 * Javadocs
 * Everything else
