<<<<<<< HEAD
# SopMines

Auto-mine plugin for Paper/Spigot `1.16.5+` (built for Java 8).

## Features

- Multiple auto-mines stored as separate files in `plugins/SopMines/mines/`.
- Each auto-mine rotates independently.
- Each auto-mine uses its own allowed mine list via `mines` (IDs from `config.yml`).
- Each mine has its own active duration (`duration-seconds`) before switching.
- Weighted ore palette via `blocks`.
- Optional weighted outer conceal layer via `surface-blocks` + `surface-layer`.
- On-rotation commands via `on-update-commands`.
- PlaceholderAPI support for current/next mine and time until next switch.
- Optimized filling: FAWE/WorldEdit if available, otherwise async plan building + sync batched placement.

## Dependencies

- Required: `SopLib`
- Optional: `PlaceholderAPI`
- Optional: `FastAsyncWorldEdit` / `WorldEdit` (for faster region apply)

## Build

Requirements:

- JDK 8
- Maven 3.8+

Command:

```bash
mvn -DskipTests clean package
```

Output:

- `target/SopMines.jar`

## Config Structure

### `config.yml` (main)

```yml
mines:
  common-small:
    name: '&7Common Small'
    blocks:
      - 'STONE:70'
      - 'COAL_ORE:30'
    surface-blocks:
      - 'STONE:1'
      - 'COBBLESTONE:1'
    surface-layer:
      enabled: false
      thickness: 1
    duration-seconds: 300
```

Mine fields:

- `name` - display name of the mine.
- `blocks` - weighted list in `MATERIAL:WEIGHT` format for main fill.
- `surface-blocks` - weighted list in `MATERIAL:WEIGHT` format for outer layer.
- `surface-layer.enabled` - enables/disables outer conceal layer.
- `surface-layer.thickness` - thickness of the outer layer in blocks.
- `duration-seconds` - how long this mine stays active before rotation.

### `mines/<id>.yml` (auto-mine)

```yml
id: mine-a
enabled: true
name: '&aMine A'
position:
  world: world
  pos1:
    x: 0
    y: 60
    z: 0
  pos2:
    x: 10
    y: 70
    z: 10
mines:
  - common-small
  - rare-medium
allow-consecutive-repeats: false
on-update-commands:
  - '[broadcast] &eAuto mine {automine_display} updated: {current_display}'
```

Auto-mine fields:

- `enabled` - enables/disables this auto-mine.
- `name` - display name of the auto-mine.
- `position` - region for this auto-mine (`world`, `pos1`, `pos2`).
- `mines` - list of mine IDs from `config.yml -> mines`.
- `allow-consecutive-repeats` - if `true`, the same mine may be selected multiple times in a row.
- `on-update-commands` - commands executed on mine switch.

## Commands

Base command: `/sopmines` (alias: `/sm`)  
Permission: `sopmines.admin`

- `/sopmines reload`
- `/sopmines next <automineId>`
- `/sopmines setnext <automineId> <mineId>`
- `/sopmines setcurrent <automineId> <mineId>`
- `/sopmines pos1`
- `/sopmines pos2`
- `/sopmines createmine <id> <durationSeconds> <blocksSpec> [surfaceBlocksSpec] [displayName...]`
- `/sopmines setmineblocks <mineId> <blocksSpec>`
- `/sopmines createautomine <id> <minesCsv>`

Examples:

```text
/sopmines createmine coal-1 300 STONE:70,COAL_ORE:30 STONE:1,COBBLESTONE:1 &7Coal Mine
/sopmines createautomine mine-a coal-1,rare-medium
```

## PlaceholderAPI

Identifier: `%sopmines_...%`

- `%sopmines_current_<automineId>%` - current mine
- `%sopmines_next_<automineId>%` - next mine
- `%sopmines_next_in_<automineId>%` - time until switch

## `on-update-commands`

Supported prefixes:

- `[broadcast] <message>` - sends a chat message to all players
- `[console] <command>` - executes a command as console
- no prefix - also treated as a console command

Available tokens:

- `{automine}`
- `{automine_display}`
- `{current_mine}`
- `{current_display}`
- `{next_mine}`
- `{next_display}`
- `{next_in}`

## Performance

- `performance.use-fawe-if-available: true` - tries FAWE/WE first.
- If FAWE is unavailable, block plan is generated asynchronously.
- World changes are applied synchronously in batches by `performance.blocks-per-tick`.

=======
# SopMines
>>>>>>> 8985e84a461251eebae7e173a350bf2839d0a229
