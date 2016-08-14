# Running Instant

This document describes how to compile, configure, and run the Instant
backend and/or the bot shipped alongside it.

## Running a backend

A properly working installation of *Java* (Java 7 at least) is assumed.

For running an own instance, you can use the pre-built
[`Instant.jar`](Instant.jar) file in this repository, which should be always
up-to-date with the latest backend and frontend (unless someone messed it
up).

As of now, the backend accepts exactly one command-line argument, namely
the port number to serve from. *(TODO: Fix that.)*

**TL;DR**: To run the stock backend on port 8080, run `java -jar Instant.jar
8080`, and point your browser to [localhost:8080](http://localhost:8080).

### UNIX-like OS-es

For re-compiling the backend, a simple `make` suffices. Note that, since Git
does (intentionally) not preserve file timestamps, you may have to use
`make -B`.

For assembling a backend with correct current commit information, you can use
`make Instant-run.jar`; to run it, launch `make run`, which takes care of all
that, and spawns an instance on port 8080. Embedding the commit number is
not necessary but for debugging code that depends on it or for proper support
of client updates.

**TL;DR**: Install GNU Make and run `make run` to (re-)compile and run a
properly configured backend on port 8080.

### Windows

If you have a sufficiently sophisticated UNIX compatibility layer (like
Cygwin) installed, the instructions above apply.

For compiling the backend manually, you can perform the following steps
(as necessary); **note** that the result files are outside the source
tree to avoid polluting it.

1. Compile all backend source files in `src\`. **Note** that the source
   tree also contains the static frontend files (so naïvely compiling
   every file in the tree will fail).

     Console commands are not provided for this step since it is bound to
     be messy.

2. Assemble the results into a JAR file.

         src\> jar cvfe ..\Instant.jar Main *

3. *(Only for quickly updating frontend files — Steps 1 and 2 are not
   necessary if the backend itself is unchanged:)*

     Update the JAR with the frontend files.

         src\> jar uvf ..\Instant.jar *

4. Embed the current commit into the JAR file if desired. These commands
   produce the file `Instant-run.jar` to avoid polluting `Instant.jar`
   (which may be checked into a repository) with a random commit hash,
   may be omitted if that is not necessary, and are run in the parent
   directory of the source tree.

         > copy Instant.jar Instant-run.jar
         > echo X-Git-Commit: <COMMIT-HASH> > .git-commit
         > jar ufm Instant-run.jar .git-commit

     (Replace `<COMMIT-HASH>` with the hash of the current commit.)

     Embedding the commit number is not necessary but for debugging code that
     depends on it or for proper support of client updates.

5. Run the JAR (replace `Instant-run.jar` with `Instant.jar` if you have
   skipped Step 4):

         > java -jar Instant-run.jar 8080

     Instant is not available under
     [`http://localhost:8080`](http://localhost:8080/).

**TL;DR**: See above for running a stock backend if this is too messy for
you.

### Automated build environments

To configure the automated build system _du jour_, consider the following
points:

- Source files are provided in the `src/net/instant/` subdirectory, with the
  exception of `src/Main.java` (the latter can be safely skipped if you write
  an own launcher, though).

- Pre-compiled libraries (with custom modifications **(!)**) are stored in
  the `src/org/` subdirectory.

- Static files are located in the `src/pages/` and `src/static/`
  subdirectories; the code expects them to be available as resources in the
  `/pages` and `/static` packages.

- The (default) main class is `Main` in the default package; it is a thin
  wrapper around `net.instant.Main`.

- The code expects (but does not depend on) the manifest variable
  `X-Git-Commit` to store the Git commit of the current build, or some
  alternative fine-grained version indicator. Additional semantics may be
  defined in the future.

### HTTPS

The backend supports plain HTTP exclusively; to provide HTTPS, you have to
use a reverse proxy.

## Running bots

There is a bot shipped alongside Instant, *Scribe*, which stores room logs
independently of browser-based clients.

It depends on a recent version of *Python* (Python 2.7 is confirmed to work,
and Python 3.4 was extensively tested “in the field”); aside from that,
a WebSocket client library (i.e.,
[`websocket-client`](https://pypi.python.org/pypi/websocket-client/)) is
requied.

Scribe supports the following features, each controlled by a command-line
option. Most of them require a single additional argument; refer to the
`--help` message for (minimal) details.

- `--maxlen` — *Maximum log chunk length*: As default (and as the
  browser-based client does), Scribe delivers the entirety of its log
  database if asked to (and the Web client does that); this option caps the
  length of a single log reply to the given length to allow incremental
  loading.

    If logs are stored in memory, their amount is additionally capped to the
    given length to avoid memory leaks in long-running instances.

- `--msgdb` — *Message database*: Stores messages in an SQLite database
  instead of RAM, allowing for potentially unbounded storage.

- `--read-file` — *Scrape messages from logfile*: Scribe formats its logfile
  in a machine-readable way, and indeed supports restoring messages from it.
  Since this may be very time-consuming, users are encouraged to use
  `--msgdb` instead. There may be multiple instances of this option.

    Ancient versions (without `--msgdb`) used this to persist messages across
    restarts; the name of the bot is derived from the very first version only
    noting down messages (which the second version could deliver to clients).

- `--push-logs` — *Push logs*: Can be used to (crudely) transfer logs between
  instances. When specified, Scribe pushes all of its logs to the participant
  with the specified ID unconditionally after initializing the message
  database and reading logfiles (if any). Because of the asynchronous nature
  of the protocol (and the peer-to-peer-based log system), clients happily
  accept any piece of logs offered to them, whether it was requested or not.
  This option may be repeated (although support might be shaky).

- `--dont-stay` — *Do not remain in room*: Lets Scribe exit once it is
  finished updating its message database. Can be useful to grab a snapshot of
  the logs in one room for, e.g., transferring them somewhere else.

- `--dont-pull` — *Do not pull logs*: Prevents Scribe from fetching messages
  from the room as it would normally do.

    If used in conjunction with `--dont-stay`, this will let the current
    instance exit as soon as another instance indicates it is done with
    updating its logs. Intended to provide short-term coverage when the
    "main" instance of Scribe is being restarted.

- `--nick` — *Nickname*: Last but not least, this allows setting a custom
  nick-name for the bot. An empty nickname will make the bot invisible to
  users, although it will still reply with the empty nickname upon request.

Aside from the options, there is a single mandatory command-line argument,
namely the WebSocket URL to connect to. It is the resource `ws` relative
to the (standard) room to connect to, with a `ws` or `wss` scheme; for
example, to connect to the upstream
[&xkcd](https://instant.leet.nu/room/xkcd/), one would specify
`wss://instant.leet.nu/room/xkcd/ws` as the URL.