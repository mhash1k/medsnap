# PharmaBill (MedSnap) — offline AI pharmacy billing for Bangladesh

CSE499A capstone. A fully offline Android app (Kotlin, Jetpack Compose, Material 3, Hilt, Room) that turns a photo, a
voice command or a search into a confirmed pharmacy bill in BDT and updates stock. The tablet-counting model is plugged
in later; until then the app runs in **no-model mode** and counts are entered by hand.

## Install the APK

1. Push to GitHub. The **Android CI** workflow (`.github/workflows/android.yml`) runs the unit tests and builds a debug APK.
2. Open the repo's **Actions** tab, pick the latest green run, and download the **app-debug-apk** artifact (a zip).
3. Unzip it, copy `app-debug.apk` to the phone and open it (allow "Install unknown apps" for your file manager).

Android 8.0+ (minSdk 26). Tested target: budget phones with 3–4 GB RAM.

Building locally: open the folder in Android Studio (it will offer to add the Gradle wrapper), or run `gradle assembleDebug`
with Gradle 8.9 and JDK 17.

## Add or update the counting model

1. Train with `ml/train_yolo_colab.ipynb` (YOLOv8n or YOLO11n, classes `tablet`, `capsule`, `box`; see `ml/LABELING_GUIDE.md`).
2. Copy the exported file as `app/src/main/assets/models/counter.tflite` and write `app/src/main/assets/models/model_meta.json`:
   ```json
   {"version":"v0.1","input_size":640,"class_names":["tablet","capsule","box"],"conf_threshold":0.40,"iou_threshold":0.5}
   ```
3. Bump `versionCode` / `versionName` in `app/build.gradle.kts`, push, install the new APK.
4. Settings shows the model version; every scan stores `model_version`.

At start-up `CountingEngineHolder` uses `TfliteYoloCountingEngine` if the model loads, otherwise `NoModelCountingEngine`.
In **debug builds** Settings → *Load model from storage* lets you pick a `.tflite` (then optionally its `model_meta.json`)
to test without rebuilding; *Use bundled model* switches back. Release builds only use the bundled model.

Training data: with *Collect training data* on, every scan keeps its photo and AI output, and each staff-corrected line is
saved. Settings → *Export dataset (.zip)* writes `images/`, `labels/` (YOLO txt), `data.yaml` and `manifest.csv`.

## Project layout

```
app/src/main/java/com/pharmabill/app/
  domain/   pricing (PriceCalculator, QuantityResolver), flags (FlagEvaluator, BillRules), stock (StockLedger), use cases
  ai/       ocr (ML Kit + TextNormalizer), match (MedicineMatcher, Jaro-Winkler, PhoneticKey), count (engines, NMS, YOLO decode),
            group (DBSCAN grouping, ScanAssembler), voice (SpeechRecognizer, BanglaTransliterator, VoiceParser), image (blur/glare)
  data/     Room (9 tables), repositories, DataStore settings, seed import, backup
  device/   PDF receipts, ESC/POS Bluetooth printing (raster, so Bangla prints), low-stock WorkManager job
  dataset/  training-sample logging and YOLO zip export
  ui/       14 screens + More menu, Bangla/English strings in res/values and res/values-bn
app/src/test/  JVM unit tests (pricing, quantity/forms, flags, matcher, voice parser incl. Bangla script, grouping, NMS,
               negative-stock reconciliation, seed CSV)
ml/            Colab notebook, dataset yaml, labelling guide
```

## Testing checklist → where it lives in code

| Checklist item | Implementation |
| --- | --- |
| First launch, language switch everywhere | `LanguageScreen`, `ShopSetupScreen`, `SeedImporter`; Settings → Language recreates the activity with `LocaleHelper` |
| Piece, cut, full strip, box (1 of 3), syrup, discount; total and stock | `PriceCalculator`, `QuantityResolver`, `BillRepository.confirm`; `PriceCalculatorTest.fullTestBill` |
| Sell with 0 stock, add stock later, reconcile | `StockLedger`, `StockRepository.stockIn`, needs-stock badges; `StockAndSeedTest` |
| Three drafts, close/reopen, confirm the second | Drafts are Room rows (`bill.status = DRAFT`), chip bar on Home and Bill, active draft in DataStore |
| Confirm locked with one yellow line | `BillRules.canConfirm`, Bill screen button "N items need checking", checked again inside the confirm transaction |
| Unknown medicine quick-add from a photo | Scan result → *Quick add* → `QuickAddDialog` pre-filled from OCR text |
| Multi-medicine photo and one-medicine mode | `DetectionGrouper` (DBSCAN) and Camera / Scan / Settings toggle |
| Voice: add, remove, change, stock, price, finish; with/without internet | `VoiceParser`, `VoiceCommandRunner`, Settings voice-pack status and download |
| Thermal print; share PDF | `EscPosPrinter`/`PrinterManager`, `ReceiptPdfGenerator` + FileProvider share sheet |
| Backup, uninstall, reinstall, restore | `BackupManager` (zip of DB + settings), Backup screen |
| Swap model versions; `model_version` on scans | `CountingEngineHolder`, debug loader, `scan.model_version` |
| Airplane mode for everything except voice | No network calls anywhere; only Android speech may need internet |

## Assumptions

Choices made where the blueprint left something open:

- **Prices in the seed are placeholders.** All 210 rows have `price_verified=false`, so a "check price" badge shows until the
  owner edits the medicine. The medicine list is hand-made, not from a licensed source.
- **Money** is `Long` paisa everywhere. Percent discounts are stored in basis points (500 = 5 %). The bill total is rounded
  to the nearest Taka, half up.
- **Loose pieces pricing:** a pieces line is charged as full strips at strip price plus the rest at piece price
  (override if set, else strip price ÷ pieces, rounded once per line). So "1 strip + 4 pcs" = strip price + 4 × piece price.
- **Clear lead** for a green name match = at least 0.05 above the second match; model confidence for a green count = 0.60.
  Both are tunable in Settings with the 0.85 / 0.60 match thresholds.
- **OCR score without a strength in the text:** the strength weight (0.3) is dropped and the rest rescaled, so a brand alone
  can still reach green when only one strength exists; two strengths of the same brand tie and show yellow.
- **Voice matching** uses the mean of Jaro-Winkler and edit-distance similarity (JW alone over-matches short words);
  a phonetic-only match scores 0.84, so it lands yellow and needs a tap. Minimum accepted voice match is 0.72.
- **No-model counts:** a strip medicine found by name but with no detections defaults to one full strip (as pieces) and is
  yellow until checked. With a model, a strip medicine with no tablets detected is red.
- **Red unknown lines** (no medicine) cannot be added to the draft until staff search or quick-add. Yellow lines can be added
  but keep Confirm locked.
- **Detection boxes** use a plain `BBox` class instead of `android.graphics.RectF` so all grouping/NMS logic runs in JVM tests.
- **Draft numbers** are the bill row id ("Draft #12"); confirmed bills get a separate sequential `bill_no` shown as B-000001.
- Added three `bill_line` columns: `candidate_ids` (top-3 suggestions for yellow lines), `display_name` (name snapshot for
  receipts) and `is_box` (box line awaiting a chip). Added `price_verified`, plus `image_width/height` on `scan`.
- **Charts** are drawn with Compose `Canvas` (7-day bar chart) instead of adding the Vico library.
- **Printing** uses a small built-in ESC/POS raster encoder over Bluetooth SPP instead of the DantSu library, so receipts in
  Bangla print correctly on 58 mm printers that have no Bangla font. Pair the printer in Android settings first.
- **GPU delegate** is not enabled (CPU, 4 threads); add `tensorflow-lite-gpu` later if inference is too slow.
- **Dataset labels** come from the model's own boxes (there is no box editor); `manifest.csv` marks photos whose corrected
  count differs from the AI count (`needs_relabel`) so they are relabelled first.
- Photos are deleted after the scan result screen closes when *Collect training data* is off.
- Low-stock check runs every 12 hours (WorkManager) and posts one notification.
- Backup is a `.zip` with the Room database and the settings file; restore replaces both and restarts the app.
- Draft reminder: drafts older than 12 hours (configurable 1–48) show ⏰ and "old draft"; they are never deleted automatically.
- Users table holds one OWNER row; roles and PIN login are 499B.
