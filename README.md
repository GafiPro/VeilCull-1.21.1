# VeilCull-1.21.1

Server-side Fabric 1.21.1 prank and admin utility commands for private servers.

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
- `/vanish`
- `/vanish <player>`

All commands require permission level 2.

### Fake join / leave

`/fakeleave <player>` always sends the fake leave message. When that player is online, VeilCull also puts them into vanish automatically.

`/fakejoin <player>` always sends the fake join message. When that player is online and currently vanished through VeilCull, their vanish is removed automatically.

For a direct toggle without a fake join/leave message, use `/vanish` or `/vanish <player>`.

Vanish hides the player from the player list for other clients and marks the entity invisible. The server-side player object remains online, so commands and server logic still see the player as connected.

### Fake lag

`/fakelag <player>` enables a fluctuating fake ping that continuously moves between **7000 and 9800 ms**.

`/fakelag <player> <ping>` sets a fixed fake ping.

`/fakelag <player> 0` removes the fake ping.

The server supplies the fake value through the network handler's latency lookup and refreshes the player-list latency packet periodically, so client-side ping displays such as BetterPingDisplay can show the fake value. This changes the reported ping, not the player's actual network connection.

### Interception

Interception is **disabled on first installation**.

Run `/interceptmsg` once to enable it. The server saves the setting and the observer's username to `config/veilcull.json`.

After a restart, the saved state remains. While enabled, chat and system messages sent to players are copied to the configured observer with an `[Intercept]` prefix.

Run `/interceptmsg` again to disable it; that change is saved too.

This is server-side: clients do not need to install the mod.
