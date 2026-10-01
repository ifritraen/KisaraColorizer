package com.raen.kisaracolorizer.data.model

enum class ModelVariant(
    val id: String,
    val displayName: String,
    val fileName: String,
    val minSizeBytes: Long,
    val downloadUrls: List<String>,
) {
    MANGA_COLORIZER_V2(
        id = "manga_colorizer_v2",
        displayName = "Manga Colorizer v2 (Studio FP32)",
        fileName = "manga_colorization_v2_fp32.onnx",
        minSizeBytes = 80L * 1024 * 1024, // ~123MB FP32
        downloadUrls = listOf(
            "https://github.com/ifritraen/color_model/releases/download/v0.1/manga_colorization_v2_fp32.onnx",
            "https://huggingface.co/ifritraen/manga-colorization-v2-fp32/resolve/main/manga_colorization_v2_fp32.onnx",
        ),
    ),
    DEOLDIFY(
        id = "deoldify",
        displayName = "DeOldify Artistic (Venera)",
        fileName = "deoldify_artistic.onnx",
        minSizeBytes = 50L * 1024 * 1024, // ~254MB
        downloadUrls = listOf(
            "https://huggingface.co/facefusion/models-3.0.0/resolve/main/deoldify.onnx",
            "https://github.com/instant-high/deoldify-onnx/releases/download/deoldify-onnx/deoldify.onnx",
        ),
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom External ONNX Model",
        fileName = "custom_model.onnx",
        minSizeBytes = 8L * 1024 * 1024,
        downloadUrls = emptyList(),
    );

    companion object {
        fun fromId(id: String): ModelVariant {
            return entries.firstOrNull { it.id == id } ?: MANGA_COLORIZER_V2
        }
    }
}
