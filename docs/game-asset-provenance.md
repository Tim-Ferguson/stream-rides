# Game Asset Provenance

## Sprite sheet

- File: `device-setup/ride-starter/res/drawable-nodpi/game_sprites.png`
- SHA-256: `e1719018c38bccb4d83a673b1a6e57e087b4c8f3d0847636441b5da9028c007d`
- Dimensions: 1536 x 1024 RGBA PNG
- Created: 2026-08-10
- Method: generated specifically for SARO with OpenAI image generation, then
  committed as a local bitmap resource
- External source assets: none

The generation request asked for one transparent-background sprite sheet with
six distinct, readable game subjects matching SARO's modes: a drag-racing car,
a flying rider, a mountain-bike climb, a power reactor, a rhythm runner, and an
orbital courier. The result is cropped at runtime using the source rectangles
in `GameSprites.java`.

The project makes this generated asset available with the SARO source under the
repository's MIT License to the extent the project owner has rights to do so.
No claim is made over third-party product names, platform trademarks, or the
output policies of the generation service.
