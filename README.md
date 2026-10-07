# VeilCull-1.21.1

Server-side Fabric 1.21.1 prank commands for private servers.

## Commands

- `/sendmsg <player> <message>`
- `/fakejoin <player>`
- `/fakeleave <player>`
- `/fakeadvancement <player> <text>`
- `/fakedeath <player>`
- `/fakelag <player>`
- `/fakelag <player> <ping>`
- `/fakelag <player> 0`
- `/interceptmsg`

All commands require permission level 2.

### Fake lag

`/fakelag <player>` enables a fluctuating fake ping that continuously moves between **7000 and 9800 ms**.

`/fakelag <player> <ping>` sets a fixed fake ping.

`/fakelag <player> 0` removes the fake ping.

The server refreshes the player-list latency packets so client-side ping displays such as BetterPingDisplay can show the fake value. This changes the reported ping, not the player's actual network connection.

### Interception

Interception is **disabled on first installation**.

Run `/interceptmsg` once to enable it. The server then saves the setting and the name of the player who enabled it to `config/veilcull.json`.

After a restart, interception remains enabled automatically when that observer is online. Running `/interceptmsg` again disables it and saves that change.

Interception is global: while enabled, chat and system messages sent to players are copied to the configured observer with an `[Intercept]` prefix. Broadcasted messages are deduplicated so they do not appear once per recipient.

This is server-side: clients do not need to install the mod.
