// Re-apply HTML5 video fullscreen after wry regenerates RustWebChromeClient.kt.
val patchWryWebViewFullscreen: () -> Unit = {
    val mainActivity =
        project.fileTree(project.projectDir) {
            include("**/MainActivity.kt")
        }.files.firstOrNull()

    val helperPkg =
        mainActivity
            ?.readLines()
            ?.firstOrNull { it.startsWith("package ") }
            ?.substringAfter("package ")
            ?.trim()
            .orEmpty()
    val helperFqcn =
        if (helperPkg.isNotEmpty()) {
            helperPkg.replace("`", "") + ".WebViewFullscreen"
        } else {
            "WebViewFullscreen"
        }

    project.fileTree(project.projectDir) {
        include("**/RustWebChromeClient.kt")
    }.forEach { file ->
        var text = file.readText()
        if (text.contains("WebViewFullscreen.show")) {
            return@forEach
        }
        if (!text.contains("onShowCustomView") || !text.contains("onCustomViewHidden")) {
            return@forEach
        }

        val showRe =
            Regex(
                """override fun onShowCustomView\(\s*view:\s*View,\s*callback:\s*CustomViewCallback\s*\)\s*\{[^}]*}""",
            )
        val hideRe = Regex("""override fun onHideCustomView\(\)\s*\{[^}]*}""")
        if (!showRe.containsMatchIn(text) || !hideRe.containsMatchIn(text)) {
            logger.warn("WebView fullscreen: stub methods not found in ${file.absolutePath}")
            return@forEach
        }

        text =
            showRe.replace(
                text,
                """override fun onShowCustomView(view: View, callback: CustomViewCallback) {
    WebViewFullscreen.show(activity, view, callback)
  }""",
            )
        text =
            hideRe.replace(
                text,
                """override fun onHideCustomView() {
    WebViewFullscreen.hide()
  }""",
            )

        val chromePkg =
            text.lineSequence()
                .firstOrNull { it.startsWith("package ") }
                ?.substringAfter("package ")
                ?.trim()
                .orEmpty()
                .replace("`", "")
        val helperPkgPlain = helperPkg.replace("`", "")
        val importLine = "import $helperFqcn"
        if (helperPkgPlain.isNotEmpty() && chromePkg != helperPkgPlain && importLine !in text) {
            val lines = text.lines().toMutableList()
            val lastImport = lines.indexOfLast { it.startsWith("import ") }
            val insertAt = if (lastImport >= 0) lastImport + 1 else 1
            lines.add(insertAt, importLine)
            text = lines.joinToString("\n")
            if (!text.endsWith("\n")) {
                text += "\n"
            }
        }

        file.writeText(text)
        logger.lifecycle("Patched WebView fullscreen into ${file.absolutePath}")
    }
}

tasks.configureEach {
    if (name.startsWith("rustBuild")) {
        doLast { patchWryWebViewFullscreen() }
    }
    if (name.startsWith("compile") && name.contains("Kotlin")) {
        doFirst { patchWryWebViewFullscreen() }
    }
}
