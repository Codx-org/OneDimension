# OneDimension

Every world stacked into one. Dig through the floor of the Overworld and you fall out of the
sky of the Nether — still falling, with the ground below you already there. Keep going and
you come out the bottom of that one too, and the bottom of the next, until you arrive back
where you started.

No loading screens. No black between worlds. You can look down through the floor and see
what you are about to fall into.

## How it works

The worlds are joined in a column, top to bottom, and the bottom is joined back to the top —
so the column has no floor and no sky, and a fall through all of it is a fall through all of
it again. By default:

```
Overworld  →  Nether  →  The End  →  Overworld  →  …
```

Whatever dimensions your pack adds are in there too, in the order the game reports them,
and you can put them in any order you like.

## Getting down there

**Bedrock is obsidian.** Every floor the column is joined at would otherwise be sealed by a
block the game will not let you through, so bedrock becomes obsidian as chunks load — still
the hardest thing in the world, still worth the walk back for a diamond pickaxe, and no
longer a wall. Dig through it and keep going.

The End needs nothing at all: step off the island and you are already falling out of the
bottom of it.

## Looking through

Stand near a world's floor and you see the world below through it — its terrain, its light,
its weather — fading back into your own sky as you move away. It is drawn live, not a
picture, and it goes both ways: from below you see the underside of the world above.

Mobs, items and anything else that falls in goes through with you.

## Settings

`config/one-dimension.json`, written on first run with whatever dimensions your game has:

```json
{
  "loop": true,
  "bedrock_becomes": "minecraft:obsidian",
  "order": [
    { "dimension": "minecraft:overworld" },
    { "dimension": "minecraft:the_nether" },
    { "dimension": "minecraft:the_end" }
  ]
}
```

- **order** — top to bottom. Rearrange it, or drop a dimension out of the column entirely.
- **loop** — whether the bottom joins the top. Off, the last world's floor is just a floor.
- **bedrock_becomes** — any block, or empty to leave bedrock exactly as it was.
- Each entry can also carry **leave** and **arrive** heights, if you want a world entered
  somewhere other than its own ceiling. Worth setting where a world's ceiling is solid.

`config/planeshift.json` controls how far you can see through a boundary and how quickly
that view fades out.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- **Planeshift**, which does the seeing and the falling

## Status

Under active development. Falling through the whole column, seeing the world below, and
carrying mobs and items across all work.

Two things to expect: looking *up* at the very top of a world shows your own sky rather than
the world above it — falling up through it still works, it is only the view that is missing —
and a bare shaft dug through the Overworld will fill with water at sea level unless you line
it.
