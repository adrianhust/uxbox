# Developer Guide #

This is a generic "getting started" guide for the uxbox platform. It
intends to explain how to get the development environment up and
running with many additional tips.

The main development environment consists in a docker compose
configuration that starts the external services and the development
container (called **devenv**).

We use tmux script in order to multiplex the single terminal and run
both the backend and frontend in the same container.


## System requirements ##

You should have `docker` and `docker-compose` installed in your system
in order to set up properly the development enviroment.

In debian like linux distributions you can install it executing:

```bash
sudo apt-get install docker docker-compose
```

## Start the devenv ##

**Requires a minimum knowledge of tmux usage in order to use that
development environment.**

For start it, staying in this repository, execute:

```bash
./manage.sh run-devenv
```

This will do the following:

- Build the images if it is not done before.
- Starts all the containers in the background.
- Attaches to the **devenv** container and executes the tmux session.
- The tmux session automatically starts all the necessary services.


## First steps with tmux ##

Now having the the container running and tmux open inside the
container, you are free to execute any commands and open many shells
as you want.

You can create a new shell just pressing the **Ctr+b c** shortcut. And
**Ctrl+b w** for switch between windows, **Ctrl+b &** for kill the
current window.

For more info: https://tmuxcheatsheet.com/


## Inside the tmux session ##

### Styles ###

The styles and many related tasks are executed thanks to gulp and they are
executed in the tmux **window 0**. This is a normal gulp watcher with some
additional tasks.


### Frontend ###

The frontend build process and the http server is located on the tmux
**window 1**. **Shadow-cljs** is used for build and serve the frontend
code. For more information, please refer to `02-Frontend-Developer-Guide.md`.

By default the **window 1** executes the shadow-cljs watch process, that starts
a new JVM/Clojure instance if there is no one running. But you may also execute
`shadow-cljs server`, that starts only the instance. And then, open another
window with `Ctrl+c` and execute `shadow-cljs watch main` there. This way, the
process that watches and recompiles connects to it and it restarts faster when
your code changes.

Finally, you can start a REPL linked to the instance and the current connected
browser, by opening a third window with `Ctrl+c` and running
`shadow-cljs cljs-repl main`.


### Backend ###

The backend related environment is located in the tmux **window 2**,
and you can go directly to it using `ctrl+b 2` shortcut.

By default the backend will be started in non-interactive mode for
convenience but you can just press `Ctrl+c` and execute `./bin/repl`
for start the repl.

On the REPL you have this helper functions:
- `(start)`: start all the environment
- `(stop)`: stops the environment
- `(restart)`: stops, reload and start again.

And many other that are defined in the `tests/user.clj` file.

If some exception is raised when code is reloaded, just use
`(repl/refresh-all)` in order to finish correctly the code swaping and
later use `(restart)` again.

For more information, please refer to: `03-Backend-Guide.md`.

