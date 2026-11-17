# Native host Canvas evidence

All captures are Android View/Canvas output under Robolectric SDK28, with explicitly selected 2D fallback and synthetic isolated app storage. They do not show Filament GPU or physical Fold7. Original reference attachments excluded.

54 PNGs generated locally; nine representative screenshots and their view trees are committed. Full local set remains in the same evidence directory.

- `phone-412x915-font100-home.png`
- `phone-412x915-original-orb.png`
- `phone-412x915-compact.png`
- `phone-412x915-font100-chat.png`
- `fold-landscape-840x649-font100-home.png`
- `fold-portrait-840x1050-font200-home.png`
- `narrow-280x720-font200-home.png`
- `narrow-280x720-font200-chat.png`
- `fold-short-840x320-font200-rail.png`

The narrow 280dp/200% chat screenshot exposes an existing Activity composer problem: input is squeezed, and the send row is clipped. The supplied separate Activity composer patch needs coordinator validation.
