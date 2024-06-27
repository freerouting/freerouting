# Freerouting daemon & progress update (#313)

## Summary

This PR will upload a folder named `scripts` to save all scripts.

Updated:

- Bash Script `freeroutingd` (Freerouting Daemon) to automatically make a fake X11 desktop to run headless freerouting.
- C++ program `freeroutingp` (Freerouting Progress) translate the log of freerouting to `stderr` stream by pipe.
- A fast test dsn file as `tests/FastTest.dsn`

Set environment variable `JAVA`, you can set the jre what freerouting uses.

Set environment variable `ARG_GUI` can enable GUI (for OS like Windows)

Now we only can write `{"status": "startRoute"}`, `{"status": "routeProgress"}`, `{"status": "routeResult"}`, `{"status": "startOptimize"}` and `{"status": "optimizeResult"}`.

Bugs:

- `{"status": "routeProgress"}` doesn't work in my computer. Because no message `Saving xxx...` sent. Please update log system to output real information in routing, like pass number, etc.
- Sometimes the freerouting will push a dialog like "There is a snapshot. Would you like use this snapshot?". Headless cannot cancel this dialog.
- Sometimes there will be a net exception. But no effect for auto-routing.

like:

```
java.util.concurrent.CompletionException: java.net.ConnectException
        at java.base/java.util.concurrent.CompletableFuture.encodeRelay(CompletableFuture.java:368)
        at java.base/java.util.concurrent.CompletableFuture.completeRelay(CompletableFuture.java:377)
        at java.base/java.util.concurrent.CompletableFuture$UniCompose.tryFire(CompletableFuture.java:1152)
        at java.base/java.util.concurrent.CompletableFuture.postComplete(CompletableFuture.java:510)
        at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(CompletableFuture.java:1773)
        at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
        at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
        at java.base/java.lang.Thread.run(Thread.java:1583)
Caused by: java.net.ConnectException
        at java.net.http/jdk.internal.net.http.common.Utils.toConnectException(Utils.java:1028)
        at java.net.http/jdk.internal.net.http.PlainHttpConnection.connectAsync(PlainHttpConnection.java:227)
        at java.net.http/jdk.internal.net.http.PlainHttpConnection.checkRetryConnect(PlainHttpConnection.java:280)
        at java.net.http/jdk.internal.net.http.PlainHttpConnection.lambda$connectAsync$2(PlainHttpConnection.java:238)
        at java.base/java.util.concurrent.CompletableFuture.uniHandle(CompletableFuture.java:934)
        at java.base/java.util.concurrent.CompletableFuture$UniHandle.tryFire(CompletableFuture.java:911)
        ... 5 more
Caused by: java.nio.channels.ClosedChannelException
        at java.base/sun.nio.ch.SocketChannelImpl.ensureOpen(SocketChannelImpl.java:202)
        at java.base/sun.nio.ch.SocketChannelImpl.beginConnect(SocketChannelImpl.java:786)
        at java.base/sun.nio.ch.SocketChannelImpl.connect(SocketChannelImpl.java:874)
        at java.net.http/jdk.internal.net.http.PlainHttpConnection.lambda$connectAsync$1(PlainHttpConnection.java:210)
        at java.base/java.security.AccessController.doPrivileged(AccessController.java:571)
        at java.net.http/jdk.internal.net.http.PlainHttpConnection.connectAsync(PlainHttpConnection.java:212)
        ... 9 more
```

## freeroutingp

For now, `freeroutingp` is just for test. 

We must update the log system to provide more information, and `freeroutingp` will be more useful.

If we can convert .ses to EasyEDA's API format, we can compatible with it. (See #311)

(For now, I can't, but maybe I can develop a electron plugin for EasyEDA, and it also can make the same effect)

And thanks for @leoheck to provide this idea in #241.


## How to compile the C++ code

1. Boot up your Linux or Windows Subsystem for Linux (WSL)

2. Install compiler tools

```
sudo apt update
sudo apt install build-essential git
```

3. Clone the Freerouting repo (if you haven't already)

```
git clone https://github.com/freerouting/freerouting.git
```

3. Navigate to your Freerouting source directory and its scripts subdirectory

4. Run the compiler

```
make
```

