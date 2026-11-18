# Labelling guide (tablet / capsule / box)

Classes, in this order (must match `model_meta.json` and `pills.yaml`):

| id | class   | What to box |
|----|---------|-------------|
| 0  | tablet  | One box per tablet in its blister pocket. **Alu-alu strips**: box each visible pocket bump. |
| 1  | capsule | One box per capsule. |
| 2  | box     | One box around a whole medicine carton. |

Rules
- Box the pocket, not the whole strip. A cut piece of 3 tablets = 3 boxes.
- Label tablets even when the strip shows the foil side and the pockets are visible as bumps.
- Do not label printed pictures of tablets on cartons.
- Partially visible (cut off by the frame) tablets: label if more than half is visible.

Splits: 70 / 20 / 10 (train / val / test). Keep every photo of the same physical strip in the same split.

Augment in Roboflow or Ultralytics: brightness ±25 %, blur up to 1.5 px, rotation ±15°, noise/glare, mosaic.

Photos to collect (15–30 medicines): full strip, cut pieces of 1–9, several pieces together, two medicines together,
box. Vary table / hand / cluttered counter / newspaper, dim light / tube light, angles, distance 15–40 cm.
Shoot with the budget phone used for the demo.

App-exported data: Settings → Export dataset (.zip) gives `images/`, `labels/` (YOLO txt from the model's boxes) and
`manifest.csv`. Rows with `needs_relabel=true` are photos where staff corrected the count: fix those labels first.
