# TagHunt

A Minecraft Spigot plugin (1.21.4) for a freeze-tag style minigame.

## Game Overview

- **Hunters** chase and freeze **Runners** by hitting them.
- **Runners** can unfreeze frozen teammates by hitting them.
- Hunters earn points for freezing runners (+5) and winning (+10).
- Runners earn points for unfreezing teammates (+5).
- Hunters are penalized (-5) if they fail a `/challenge` round.

## Commands

| Command | Description |
|---------|-------------|
| `/sethunter <player>` | Assign a player as hunter |
| `/setrunner <player>` | Assign a player as runner |
| `/startgame` | Start / reset the game |
| `/resetall` | Clear all roles and abilities |
| `/resetpoints <all\|player...>` | Reset points |
| `/challenge` | Start a 30-second challenge round |
| `/runkit` | Give the runner kit (includes ability selector) |
| `/huntkit` | Give the hunter kit |
| `/itemdrop` | Drop a special bow at the configured or targeted location |
| `/ability` | Open the ability selection menu |

## Runner Abilities

Runners receive a **⭐ Choose Ability** item with `/runkit` (or use `/ability`) to open a GUI and pick one of three abilities:

### 1. Dash & Double Jump (Feather)
- **Right-click on the ground** → dash forward
- **Right-click in the air** → double jump
- 3-second cooldown

### 2. Grappling Hook (Fishing Rod)
- Cast your fishing rod at a runner **teammate**
- You get pulled towards them
- 8-second cooldown

### 3. Bridge Egg (Egg ×16)
- Throw the egg and it lays OAK_PLANKS in its trail
- Great for crossing gaps quickly
