package com.example.flashcardforteens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.flashcardforteens.core.domain.UiMessageKey
import com.example.flashcardforteens.core.ui.FlashcardToolbar
import com.example.flashcardforteens.core.ui.uiString
import com.example.flashcardforteens.core.ui.R as UiR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LicenseScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val licenses by produceState(LicenseText()) {
    value = withContext(Dispatchers.IO) {
      try {
        val text = listOf("avatars/NOTICE.txt", "level-icons/NOTICE.txt",
          "english-speech/LICENSES.txt", "chinese-speech/LICENSES.txt",
          "learning-sounds/NOTICE.txt", "learning-sounds/CC0.txt").joinToString("\n\n") { name ->
          context.assets.open(name).bufferedReader().use { it.readText() }
        }
        val sections = mutableListOf<String>()
        var start = 0
        Regex("\\r?\\n\\r?\\n").findAll(text).forEach { boundary ->
          val end = boundary.range.last + 1
          if (end - start >= 4_000) {
            sections += text.substring(start, end)
            start = end
          }
        }
        if (start < text.length) sections += text.substring(start)
        LicenseText(sections = sections, loading = false)
      } catch (error: CancellationException) { throw error }
      catch (_: Exception) { LicenseText(loading = false, failed = true) }
    }
  }
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)) {
    item(key = "header", contentType = "header") {
    FlashcardToolbar(uiString(UiR.string.ui_sources_licenses), onBack)
    }
    item(key = "intro", contentType = "intro") {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(uiString(UiR.string.ui_default_vocabulary_bundled_wordlist_csv_dictionary_attribution_and_content_sources_are_listed_below))
    Text(uiString(UiR.string.ui_additional_vocabulary_local_csv_wordlists_imported_in_settings))
    Text(uiString(UiR.string.ui_resource_license_intro))
    }
    }
    if (licenses.loading || licenses.failed) item(key = "status", contentType = "status") {
      Text(if (licenses.loading) uiString(UiR.string.ui_loading)
        else uiString(UiMessageKey.SOURCE_LICENSES_COULD_NOT_BE_READ_PLEASE_REOPEN_THIS_PAGE),
        style = MaterialTheme.typography.bodySmall)
    }
    itemsIndexed(licenses.sections, key = { index, _ -> "license/$index" }, contentType = { _, _ -> "license" }) { _, text ->
      Text(text, style = MaterialTheme.typography.bodySmall)
    }
  }
}

private data class LicenseText(val sections: List<String> = emptyList(), val loading: Boolean = true, val failed: Boolean = false)
