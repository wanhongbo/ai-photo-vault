# LumaNox Marketing Design System

> Generated: 2026-06-05
> Last updated: 2026-06-05

## Design Direction

**Style**: Warm launch editorial
**Tone**: Product Hunt-friendly, dense enough for makers, warmer than the dark app UI while keeping privacy and trust as the core message.

## Color Palette

| Use | Color |
| --- | --- |
| Paper background | `#f6f0e8` |
| Strong paper | `#fff9f0` |
| Primary text | `#241d17` |
| Secondary text | `#6c5d50` |
| Product Hunt accent | `#ff5a1f` |
| Deep accent | `#3f2638` |
| Trust accent | `#2f9f7a` |
| Cool contrast | `#a7d8ff` |

## Typography

- **Display**: Space Grotesk, weight 700-800.
- **Body/UI**: DM Sans, weight 500-800.
- Letter spacing remains `0`; scale type by semantic role, not viewport-width formulas for compact controls.

## Layout

- Page max width: `1120px`.
- Mobile page edge: `14px`; desktop edge: `20px`.
- Cards and controls use an 8px base radius, with 14-16px only for large preview/CTA containers.
- Repeated cards keep stable min heights so hover and copy changes do not shift the grid.

## Interaction

- Buttons are at least `44px` tall.
- Hover states use small elevation/color changes only.
- Focus states use visible orange outlines.
- `prefers-reduced-motion` disables transitions.

## Avoid

- Purple-blue gradient hero treatments.
- Generic centered SaaS hero cards.
- Decorative emoji icons.
- Claims like “unhackable” or “military-grade”.
