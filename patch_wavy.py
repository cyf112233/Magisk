import os

filepath = 'app/apk/src/main/java/com/topjohnwu/magisk/ui/MainScreenBars.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Replace the custom imports and add the official LinearWavyProgressIndicator imports
# We imported linearEasing, repeatMode, animateFloat, infiniteRepeatable, rememberInfiniteTransition, tween, Canvas, Offset, Brush, Path, StrokeCap, Stroke, PI, sin.
# Let's remove them or leave them, but clean them up.
# Let's find and replace our imports:
old_import = "import androidx.compose.animation.animateContentSize"
# Let's replace old imports with clean imports containing the official expressive material3 component imports.
content = content.replace("import androidx.compose.animation.core.LinearEasing", "")
content = content.replace("import androidx.compose.animation.core.RepeatMode", "")
content = content.replace("import androidx.compose.animation.core.animateFloat", "")
content = content.replace("import androidx.compose.animation.core.infiniteRepeatable", "")
content = content.replace("import androidx.compose.animation.core.rememberInfiniteTransition", "")
content = content.replace("import androidx.compose.animation.core.tween", "")
content = content.replace("import androidx.compose.foundation.Canvas", "")
content = content.replace("import androidx.compose.ui.geometry.Offset", "")
content = content.replace("import androidx.compose.ui.graphics.Path", "")
content = content.replace("import androidx.compose.ui.graphics.StrokeCap", "")
content = content.replace("import androidx.compose.ui.graphics.drawscope.Stroke", "")
content = content.replace("import kotlin.math.PI", "")
content = content.replace("import kotlin.math.sin", "")

expressive_imports = """import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator"""

content = content.replace(old_import, old_import + "\\n" + expressive_imports)

# 2. Add @OptIn(ExperimentalMaterial3ExpressiveApi::class) to MagiskTopBar
old_topbar_annotation = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MagiskTopBar("""

new_topbar_annotation = """@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MagiskTopBar("""

content = content.replace(old_topbar_annotation, new_topbar_annotation)

# 3. Replace WavyProgressIndicator with LinearWavyProgressIndicator in the Box
old_wavy_call = """        if (processState.running) {
            WavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.BottomCenter)
            )
        }"""

new_wavy_call = """        if (processState.running) {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            )
        }"""

content = content.replace(old_wavy_call, new_wavy_call)

# 4. Remove WavyProgressIndicator Composable function from the bottom of the file
# Let's find the start of the Composable and cut everything from there to the end.
wavy_start = content.find("@Composable\\nfun WavyProgressIndicator")
if wavy_start != -1:
    content = content[:wavy_start]
else:
    # try with other line endings
    wavy_start2 = content.find("@Composable\\r\\nfun WavyProgressIndicator")
    if wavy_start2 != -1:
        content = content[:wavy_start2]

# Clean up empty lines or duplicated carriage returns
content = content.strip() + "\\n"

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)

print("MainScreenBars.kt successfully updated to official LinearWavyProgressIndicator!")
