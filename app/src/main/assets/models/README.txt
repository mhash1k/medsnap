Put counter.tflite and model_meta.json here, bump versionCode in app/build.gradle.kts, and rebuild.
Without counter.tflite the app runs in no-model mode (counts are entered by hand).
Example model_meta.json:
{"version":"v0.1","input_size":640,"class_names":["tablet","capsule","box"],"conf_threshold":0.40,"iou_threshold":0.5}
