# VeilCull-1.21.1

Server-side Fabric 1.21.1 prank commands for private servers.

## Commands

- `/sendmsg <player> <message>`
- `/fakejoin <player>`
- `/fakeleave <player>`
- `/fakeadvancement <player> <text>`
- `/fakedeath <player>`
- `/fakelag <player> [ping]`
- `/interceptmsg [player]`

All commands require permission level 2.

### Interception

Interception is disabled by default in `config/veillull.json`.

When enabled, the server copies system messages delivered to the configured target player to the configured observer. The command form `/interceptmsg <player>` sets the target and enables it; `/interceptmsg` toggles the current target.

### Fake lag

`/fakelag <player>` uses 9999 ms by default. The value is refreshed on a server tick so the client-side player-list ping display continues to show the fake latency.

This is server-side: clients do not need to install the mod.
