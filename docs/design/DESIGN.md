# Design System Specification: The Monolithic Archive

## 1. Overview & Creative North Star
**Creative North Star: The Digital Architect**
This design system moves away from the "software as a tool" mentality and toward "software as an institution." It is designed to feel like a digital monolith—permanent, authoritative, and immovable. By leveraging a high-contrast, low-light palette, we prioritize the "Editorial Flow" over standard app layouts. We break the traditional grid through intentional asymmetry: heavy-weighted typography on the left balanced by expansive, breathable data fields on the right. This isn't just a database; it is a high-performance environment for the modern judiciary.

## 2. Colors: Tonal Depth over Borders
The core philosophy of this system is **Atmospheric Containment**. We do not use lines to separate ideas; we use the weight of the dark.

*   **The "No-Line" Rule:** 1px solid borders are strictly prohibited for sectioning. Boundaries must be defined solely through background color shifts. For example, a `surface-container-low` (#131315) action bar sitting atop a `surface` (#0e0e0f) background provides all the definition needed.
*   **Surface Hierarchy & Nesting:** Treat the UI as stacked sheets of volcanic rock.
    *   **Level 0 (Foundation):** `surface` (#0e0e0f) for the main application backdrop.
    *   **Level 1 (Sub-sectioning):** `surface-container-low` (#131315) for sidebar or secondary navigation.
    *   **Level 2 (Active Workspaces):** `surface-container` (#19191b) for the primary data entry area.
    *   **Level 3 (High-Focus Modals):** `surface-container-highest` (#252628) for floating panels.
*   **The "Glass & Gradient" Rule:** To evoke a premium, "High-End Editorial" feel, primary actions and floating headers should utilize a `backdrop-blur` of 12px combined with a 40% opaque `surface-variant`.
*   **Signature Textures:** For primary call-to-actions, use a subtle linear gradient from `primary` (#b8c8da) to `primary_container` (#394857) at a 135-degree angle. This adds a "metallic" sheen reminiscent of silver or graphite, providing a tactile, premium feel.

## 3. Typography: The Legal Scale
Typography is our primary tool for authority. We use **Inter** for its neutral, Swiss-inspired legibility.

*   **The Editorial Anchor:** Use `display-lg` (3.5rem) for case numbers or high-level headings, but set them to `outline` (#767578) with a weight of 600. This creates a powerful visual anchor without overwhelming the page.
*   **Data Density:** For technical tables or case logs, switch to a mono-spaced feel by tightening the letter-spacing of `body-sm` (-0.02em).
*   **Hierarchy through Tone:**
    *   **Primary Data:** `on_surface` (#e7e5e8) – High contrast for maximum legibility.
    *   **Supporting Labels:** `on_surface_variant` (#acaaae) – To be used for field labels to reduce visual noise during high-speed entry.
    *   **Metadata:** `outline` (#767578) – For timestamps and non-essential background data.

## 4. Elevation & Depth
In a dark, high-speed environment, traditional shadows feel muddy. We use **Luminous Elevation**.

*   **The Layering Principle:** Depth is achieved by "stacking." A `surface-container-lowest` (#000000) field nested inside a `surface-container` (#19191b) creates an "etched" look, ideal for input fields.
*   **Ambient Shadows:** For floating elements (modals), use a wide-spread shadow: `offset: 0 20px, blur: 40px, color: rgba(0,0,0,0.5)`. This mimics a soft overhead courtroom light.
*   **The "Ghost Border" Fallback:** If a divider is functionally required (e.g., in a complex data grid), use `outline_variant` (#48484b) at **15% opacity**. It should be felt, not seen.
*   **Glassmorphism:** Use `surface_bright` (#2b2c2f) at 60% opacity with a `backdrop-filter: blur(10px)` for utility bars that scroll over content, maintaining a sense of spatial awareness.

## 5. Components: Precision Primitives
Components are sharp (2px–4px radius) to reflect the precision of the law.

*   **Input Fields:** No borders. Use `surface-container-lowest` as the fill. The active state is indicated by a 2px `secondary` (#af9e46) "underline" or "accent bar" on the left edge of the input.
*   **Primary Buttons:** Utilize the `primary` (#b8c8da) token. Use `none` or `sm` (0.125rem) rounding. Sizing should be generous (height: 2.75rem) to ensure "Fitts's Law" compliance during high-speed entry.
*   **Action Chips:** For case statuses (e.g., "Pending", "Closed"), use `tertiary_container` (#ced6f5) with `on_tertiary` (#4a536c) text. Rounding should be `full` to distinguish them from the sharp-edged functional buttons.
*   **Lists & Grids:** Forbid the use of divider lines. Separate rows using `1.5` (0.3rem) of vertical whitespace and a hover state of `surface-bright`.
*   **Chronological Steppers:** For case history, use a vertical line in `outline_variant` at 20% opacity, with active nodes highlighted in `secondary` (#af9e46).

## 6. Do's and Don'ts

### Do
*   **Do** use `secondary` (#af9e46) sparingly. It is a "surgical" accent for final submissions or critical alerts.
*   **Do** utilize `surface-container-lowest` (#000000) for "sunken" elements like search bars to give them a sense of depth and focus.
*   **Do** prioritize `body-lg` for all narrative text to prevent eye strain during long reading sessions.

### Don't
*   **Don't** use pure white (#FFFFFF) for text. Always use `on_surface` (#e7e5e8) to prevent "halation" (the glowing effect of white text on black backgrounds).
*   **Don't** use `xl` or `full` rounding for primary UI containers. It softens the authoritative tone. Keep to `sm` or `md`.
*   **Don't** use standard "drop shadows." Use tonal shifts between surface tiers to define hierarchy.
*   **Don't** crowd the interface. If a screen feels full, increase the spacing scale to `12` or `16` between major sections.