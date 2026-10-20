package com.example.flashcardforteens.core.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.flashcardforteens.core.domain.AppLanguage
import com.example.flashcardforteens.core.domain.UiMessageKey
import com.example.flashcardforteens.core.domain.VocabularyStage
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.CN }

/** Apply Chinese UI resources while retaining density, font scale and device configuration. */
@Composable
fun AppLanguageProvider(language: AppLanguage, content: @Composable () -> Unit) {
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  val localizedContext = remember(context, configuration, language) {
    context.withAppLanguage(language, configuration)
  }
  CompositionLocalProvider(
    LocalAppLanguage provides language,
    LocalContext provides localizedContext,
    LocalConfiguration provides localizedContext.resources.configuration,
    // Dialog and popup owners replace LocalContext and LocalConfiguration. Keep the
    // Chinese UI resources explicit so their content inherits it too.
    LocalResources provides localizedContext.resources,
    content = content,
  )
}

/** Standard resource resolution also works directly through Compose stringResource. */
@Composable
@ReadOnlyComposable
fun uiString(@StringRes id: Int, vararg arguments: Any): String =
  if (arguments.isEmpty()) stringResource(id) else stringResource(id, *arguments)

@Composable
@ReadOnlyComposable
fun uiPlural(@PluralsRes id: Int, quantity: Int, vararg arguments: Any): String =
  if (arguments.isEmpty()) pluralStringResource(id, quantity)
  else pluralStringResource(id, quantity, *arguments)

/** For event handlers and other non-composable presentation code. */
fun uiText(context: Context, @StringRes id: Int, language: AppLanguage, vararg arguments: Any): String =
  context.withAppLanguage(language).resources.let { resources ->
    if (arguments.isEmpty()) resources.getString(id) else resources.getString(id, *arguments)
  }

@StringRes
fun UiMessageKey.stringResourceId(): Int = when (this) {
  UiMessageKey.A_QUOTE_MUST_START_A_QUOTED_FIELD -> R.string.ui_a_quote_must_start_a_quoted_field
  UiMessageKey.A_CSV_MAY_CONTAIN_AT_MOST_20_000_WORDS -> R.string.ui_a_csv_may_contain_at_most_20_000_words
  UiMessageKey.A_JSON_UNICODE_ESCAPE_MUST_CONTAIN_FOUR_HEXADECIMAL_DIGITS -> R.string.ui_a_json_unicode_escape_must_contain_four_hexadecimal_digits
  UiMessageKey.A_JSON_ESCAPE_IS_INCOMPLETE -> R.string.ui_a_json_escape_is_incomplete
  UiMessageKey.A_JSON_OBJECT_KEY_MUST_BE_A_DOUBLE_QUOTED_STRING -> R.string.ui_a_json_object_key_must_be_a_double_quoted_string
  UiMessageKey.A_DISTRACTOR_WORD_IS_MISSING_FROM_THE_CSV_AND_SAVED_WORDBOOK -> R.string.ui_a_distractor_word_is_missing_from_the_csv_and_saved_wordbook
  UiMessageKey.A_NEW_DAY_HAS_STARTED_YOUR_DAILY_PLAN_HAS_BEEN_REFRESHED -> R.string.ui_a_new_day_has_started_your_daily_plan_has_been_refreshed
  UiMessageKey.A_QUOTED_FIELD_IS_NOT_CLOSED -> R.string.ui_a_quoted_field_is_not_closed
  UiMessageKey.A_RECORD_HAS_MORE_THAN_64_COLUMNS -> R.string.ui_a_record_has_more_than_64_columns
  UiMessageKey.A_RECORD_IS_LARGER_THAN_32_KIB -> R.string.ui_a_record_is_larger_than_32_kib
  UiMessageKey.A_REQUIRED_COLUMN_IS_MISSING -> R.string.ui_a_required_column_is_missing
  UiMessageKey.AN_OWNED_PRESET_ID_CANNOT_BE_REASSIGNED_TO_A_DIFFERENT_WORD -> R.string.ui_an_owned_preset_id_cannot_be_reassigned_to_a_different_word
  UiMessageKey.AUDIO_IS_CURRENTLY_UNAVAILABLE_YOU_CAN_CONTINUE_STUDYING -> R.string.ui_audio_is_currently_unavailable_you_can_continue_studying
  UiMessageKey.AUDIO_UNAVAILABLE_TAP_NEXT -> R.string.ui_audio_unavailable_tap_next
  UiMessageKey.COLUMN_1_D -> R.string.ui_column_1_d
  UiMessageKey.COLUMN_NAMES_MUST_BE_UNIQUE -> R.string.ui_column_names_must_be_unique
  UiMessageKey.COULDN_T_LOAD_A_WORD_TAP_SHUFFLE_TO_RETRY -> R.string.ui_couldn_t_load_a_word_tap_shuffle_to_retry
  UiMessageKey.DEFAULT_WORDLIST_COULDN_T_BE_LOADED -> R.string.ui_default_wordlist_couldn_t_be_loaded
  UiMessageKey.DISTRACTOR_IDS_MUST_BE_DISTINCT_AND_CANNOT_INCLUDE_THIS_WORD -> R.string.ui_distractor_ids_must_be_distinct_and_cannot_include_this_word
  UiMessageKey.DISTRACTORS_MUST_BE_A_JSON_ARRAY_OF_THREE_WORD_IDS -> R.string.ui_distractors_must_be_a_json_array_of_three_word_ids
  UiMessageKey.ENGLISH_SPEECH_COULD_NOT_BE_LOADED_YOU_CAN_CONTINUE_STUDYING -> R.string.ui_english_speech_could_not_be_loaded_you_can_continue_studying
  UiMessageKey.FINISH_THE_SAVED_STUDY_CARD_THEN_RETRY_THE_DEFAULT_WORDLIST_UPDATE -> R.string.ui_finish_the_saved_study_card_then_retry_the_default_wordlist_update
  UiMessageKey.IDS_MUST_CONTAIN_1_92_ASCII_LETTERS_DIGITS_OR_UNDERSCORES -> R.string.ui_ids_must_contain_1_92_ascii_letters_digits_or_underscores
  UiMessageKey.JSON_ARRAYS_AND_OBJECTS_CANNOT_END_WITH_A_COMMA -> R.string.ui_json_arrays_and_objects_cannot_end_with_a_comma
  UiMessageKey.JSON_BRACKETS_ARE_UNBALANCED -> R.string.ui_json_brackets_are_unbalanced
  UiMessageKey.JSON_BRACKETS_OR_STRINGS_ARE_NOT_CLOSED -> R.string.ui_json_brackets_or_strings_are_not_closed
  UiMessageKey.JSON_ENTRIES_CANNOT_BE_EMPTY -> R.string.ui_json_entries_cannot_be_empty
  UiMessageKey.JSON_NESTING_MUST_NOT_EXCEED_FOUR_LEVELS -> R.string.ui_json_nesting_must_not_exceed_four_levels
  UiMessageKey.JSON_STRINGS_MUST_ESCAPE_CONTROL_CHARACTERS -> R.string.ui_json_strings_must_escape_control_characters
  UiMessageKey.LOCAL_DATA_COULD_NOT_BE_READ_OR_SAVED_PLEASE_RETRY_YOUR_SAVED_PROGRESS_HAS_NOT_BEEN_CLEARED -> R.string.ui_local_data_could_not_be_read_or_saved_please_retry_your_saved_progress_has_not_been_cleared
  UiMessageKey.NO_OFFLINE_ENGLISH_VOICE_IS_AVAILABLE_ON_THIS_DEVICE_YOU_CAN_CONTINUE_STUDYING -> R.string.ui_no_offline_english_voice_is_available_on_this_device_you_can_continue_studying
  UiMessageKey.NO_OFFLINE_MANDARIN_VOICE_IS_AVAILABLE_ON_THIS_DEVICE_YOU_CAN_CONTINUE_STUDYING -> R.string.ui_no_offline_mandarin_voice_is_available_on_this_device_you_can_continue_studying
  UiMessageKey.NO_RELIABLE_QUESTION_IS_AVAILABLE_FOR_THIS_WORD_NO_LEARNING_PROGRESS_WAS_RECORDED -> R.string.ui_no_reliable_question_is_available_for_this_word_no_learning_progress_was_recorded
  UiMessageKey.NO_RELIABLE_QUESTION_IS_AVAILABLE_FOR_THIS_WORD_VIEWING_IT_DOES_NOT_COUNT_AS_LEARNED -> R.string.ui_no_reliable_question_is_available_for_this_word_viewing_it_does_not_count_as_learned
  UiMessageKey.OFFLINE_VOICE_UNAVAILABLE -> R.string.ui_offline_voice_unavailable
  UiMessageKey.ONLY_A_COMMA_OR_NEWLINE_MAY_FOLLOW_A_CLOSING_QUOTE -> R.string.ui_only_a_comma_or_newline_may_follow_a_closing_quote
  UiMessageKey.PREPARING_OFFLINE_SPEECH -> R.string.ui_preparing_offline_speech
  UiMessageKey.PROVIDE_EXACTLY_THREE_WORD_IDS_AS_A_JSON_STRING_ARRAY -> R.string.ui_provide_exactly_three_word_ids_as_a_json_string_array
  UiMessageKey.SOURCE_LICENSES_COULD_NOT_BE_READ_PLEASE_REOPEN_THIS_PAGE -> R.string.ui_source_licenses_could_not_be_read_please_reopen_this_page
  UiMessageKey.SPEECH_COULD_NOT_BE_PLAYED_YOU_CAN_CONTINUE_STUDYING -> R.string.ui_speech_could_not_be_played_you_can_continue_studying
  UiMessageKey.THE_CSV_CACHE_IS_UNAVAILABLE -> R.string.ui_the_csv_cache_is_unavailable
  UiMessageKey.THE_CSV_CONTAINS_NO_WORDS -> R.string.ui_the_csv_contains_no_words
  UiMessageKey.THE_CSV_COULDN_T_BE_IMPORTED_CHOOSE_THE_FILE_AGAIN_TO_RETRY -> R.string.ui_the_csv_couldn_t_be_imported_choose_the_file_again_to_retry
  UiMessageKey.THE_CSV_COULDN_T_BE_READ_CHECK_THAT_IT_USES_UTF_8_THEN_CHOOSE_THE_FILE_AGAIN -> R.string.ui_the_csv_couldn_t_be_read_check_that_it_uses_utf_8_then_choose_the_file_again
  UiMessageKey.THE_CSV_EXCEEDS_32_MIB -> R.string.ui_the_csv_exceeds_32_mib
  UiMessageKey.THE_CSV_FILE_IS_EMPTY -> R.string.ui_the_csv_file_is_empty
  UiMessageKey.THE_CSV_FORMAT_IS_INVALID -> R.string.ui_the_csv_format_is_invalid
  UiMessageKey.THE_CSV_SNAPSHOT_CHANGED -> R.string.ui_the_csv_snapshot_changed
  UiMessageKey.THE_JSON_STRING_CONTAINS_AN_INVALID_ESCAPE -> R.string.ui_the_json_string_contains_an_invalid_escape
  UiMessageKey.THE_BUNDLED_CSV_IS_INVALID -> R.string.ui_the_bundled_csv_is_invalid
  UiMessageKey.THE_BUNDLED_DEFAULT_WORDLIST_VERSION_DOES_NOT_MATCH_ITS_CONTENT -> R.string.ui_the_bundled_default_wordlist_version_does_not_match_its_content
  UiMessageKey.THE_BUNDLED_DEFAULT_WORDLIST_VERSION_IS_INVALID -> R.string.ui_the_bundled_default_wordlist_version_is_invalid
  UiMessageKey.THE_BUNDLED_WORDLIST_COUNT_DOES_NOT_MATCH_ITS_CONTENT -> R.string.ui_the_bundled_wordlist_count_does_not_match_its_content
  UiMessageKey.THE_BUNDLED_WORDLIST_COUNT_IS_INVALID -> R.string.ui_the_bundled_wordlist_count_is_invalid
  UiMessageKey.THE_CORRECT_MEANING_AND_THREE_DISTRACTOR_MEANINGS_MUST_DIFFER -> R.string.ui_the_correct_meaning_and_three_distractor_meanings_must_differ
  UiMessageKey.THE_FIELD_IS_INVALID -> R.string.ui_the_field_is_invalid
  UiMessageKey.THE_FIELD_MUST_CONTAIN_ONE_COMPLETE_JSON_ARRAY -> R.string.ui_the_field_must_contain_one_complete_json_array
  UiMessageKey.THE_FILE_COULD_NOT_BE_READ_CHOOSE_A_VALID_UTF_8_CSV_UP_TO_32_MIB -> R.string.ui_the_file_could_not_be_read_choose_a_valid_utf_8_csv_up_to_32_mib
  UiMessageKey.THE_FILE_MUST_USE_VALID_UTF_8_ENCODING -> R.string.ui_the_file_must_use_valid_utf_8_encoding
  UiMessageKey.THE_NUMBER_OF_FIELDS_DOES_NOT_MATCH_THE_HEADER -> R.string.ui_the_number_of_fields_does_not_match_the_header
  UiMessageKey.THE_PRESET_OWNERSHIP_MAPPING_IS_AMBIGUOUS -> R.string.ui_the_preset_ownership_mapping_is_ambiguous
  UiMessageKey.THE_RESULTING_WORDBOOK_WOULD_CONTAIN_MORE_THAN_20_000_WORDS -> R.string.ui_the_resulting_wordbook_would_contain_more_than_20_000_words
  UiMessageKey.THE_SAVED_WORDBOOK_IS_INCOMPLETE -> R.string.ui_the_saved_wordbook_is_incomplete
  UiMessageKey.THE_SELECTED_CSV_COULDN_T_BE_OPENED -> R.string.ui_the_selected_csv_couldn_t_be_opened
  UiMessageKey.THE_WORDBOOK_CHANGED_OR_THE_CSV_IS_INVALID_CHOOSE_THE_FILE_AGAIN -> R.string.ui_the_wordbook_changed_or_the_csv_is_invalid_choose_the_file_again
  UiMessageKey.THIS_ID_ALREADY_BELONGS_TO_A_DIFFERENT_WORD_OR_PRONUNCIATION -> R.string.ui_this_id_already_belongs_to_a_different_word_or_pronunciation
  UiMessageKey.THIS_CHANGE_COULDN_T_BE_SAVED_TRY_AGAIN -> R.string.ui_this_change_couldn_t_be_saved_try_again
  UiMessageKey.THIS_FIELD_CONTAINS_INVALID_UTF_8_BYTES -> R.string.ui_this_field_contains_invalid_utf_8_bytes
  UiMessageKey.THIS_FIELD_IS_REQUIRED_AND_MUST_FIT_ITS_LENGTH_LIMIT -> R.string.ui_this_field_is_required_and_must_fit_its_length_limit
  UiMessageKey.THIS_PREVIEW_HAS_EXPIRED_CHOOSE_THE_CSV_AGAIN -> R.string.ui_this_preview_has_expired_choose_the_csv_again
  UiMessageKey.THIS_STEP_COULDN_T_BE_COMPLETED_TRY_AGAIN -> R.string.ui_this_step_couldn_t_be_completed_try_again
  UiMessageKey.THIS_WORD_COULDN_T_BE_LOADED_TRY_AGAIN -> R.string.ui_this_word_couldn_t_be_loaded_try_again
  UiMessageKey.THIS_WORD_IS_NO_LONGER_AVAILABLE -> R.string.ui_this_word_is_no_longer_available
  UiMessageKey.USE_EXACTLY_THE_TWELVE_CURRENT_CSV_COLUMNS -> R.string.ui_use_exactly_the_twelve_current_csv_columns
  UiMessageKey.USE_TONE_MARKED_PINYIN_LETTERS_AND_SPACING_SENTENCE_PINYIN_MAY_INCLUDE_PUNCTUATION -> R.string.ui_use_tone_marked_pinyin_letters_and_spacing_sentence_pinyin_may_include_punctuation
  UiMessageKey.WORD_IDS_MUST_BE_UNIQUE_WITHIN_THE_CSV -> R.string.ui_word_ids_must_be_unique_within_the_csv
  UiMessageKey.YOUR_SAVED_PROGRESS_COULDN_T_BE_LOADED_TRY_AGAIN -> R.string.ui_your_saved_progress_couldn_t_be_loaded_try_again
  UiMessageKey.YOUR_SAVED_SETTINGS_COULDN_T_BE_LOADED_TRY_AGAIN -> R.string.ui_your_saved_settings_couldn_t_be_loaded_try_again
  UiMessageKey.YOUR_SETTINGS_COULDN_T_BE_SAVED_TRY_AGAIN -> R.string.ui_your_settings_couldn_t_be_saved_try_again
  UiMessageKey.YOUR_WORDLIST_COULDN_T_BE_LOADED_TRY_AGAIN -> R.string.ui_your_wordlist_couldn_t_be_loaded_try_again
}

@Composable
@ReadOnlyComposable
fun uiString(key: UiMessageKey, vararg arguments: Any): String =
  uiString(key.stringResourceId(), *arguments)

fun uiText(context: Context, key: UiMessageKey, language: AppLanguage, vararg arguments: Any): String =
  uiText(context, key.stringResourceId(), language, *arguments)

@Composable
@ReadOnlyComposable
fun VocabularyStage.localizedLabel(): String = label

@Composable
@ReadOnlyComposable
fun AppLanguage.localizedLabel(): String = uiString(when (this) {
  AppLanguage.EN -> R.string.ui_english
  AppLanguage.CN -> R.string.ui_chinese
})

/** Translate presentation labels only; custom wordlist content stays intact. */
@Composable
@ReadOnlyComposable
fun String.localizedPartOfSpeech(): String {
  if (LocalAppLanguage.current != AppLanguage.CN || isBlank()) return this
  val resources = LocalResources.current
  val labels = split(Regex("\\s*[;/]\\s*")).map { part ->
    partOfSpeechResourceIds[part.trim().lowercase(Locale.ROOT)] ?: return this
  }
  return labels.joinToString(" · ", transform = resources::getString)
}

private val partOfSpeechResourceIds = mapOf(
  "adjective" to R.string.ui_pos_adjective,
  "adjective phrase" to R.string.ui_pos_adjective_phrase,
  "adjective-aspect phrase" to R.string.ui_pos_adjective_aspect_phrase,
  "adverb" to R.string.ui_pos_adverb,
  "adverb pattern" to R.string.ui_pos_adverb_pattern,
  "adverb phrase" to R.string.ui_pos_adverb_phrase,
  "adverbial phrase" to R.string.ui_pos_adverbial_phrase,
  "adverbial time phrase" to R.string.ui_pos_adverbial_time_phrase,
  "auxiliary" to R.string.ui_pos_auxiliary,
  "auxiliary verb phrase" to R.string.ui_pos_auxiliary_verb_phrase,
  "bound adjective" to R.string.ui_pos_bound_adjective,
  "bound form" to R.string.ui_pos_bound_form,
  "bound morpheme" to R.string.ui_pos_bound_morpheme,
  "bound noun" to R.string.ui_pos_bound_noun,
  "bound verb" to R.string.ui_pos_bound_verb,
  "causative verb phrase" to R.string.ui_pos_causative_verb_phrase,
  "classifier" to R.string.ui_pos_classifier,
  "classifier phrase" to R.string.ui_pos_classifier_phrase,
  "clause" to R.string.ui_pos_clause,
  "comparative adverb phrase" to R.string.ui_pos_comparative_adverb_phrase,
  "complement" to R.string.ui_pos_complement,
  "conjunction" to R.string.ui_pos_conjunction,
  "degree complement" to R.string.ui_pos_degree_complement,
  "demonstrative phrase" to R.string.ui_pos_demonstrative_phrase,
  "demonstrative pronoun" to R.string.ui_pos_demonstrative_pronoun,
  "determiner" to R.string.ui_pos_determiner,
  "expression" to R.string.ui_pos_expression,
  "formula" to R.string.ui_pos_formula,
  "idiom" to R.string.ui_pos_idiom,
  "interjection" to R.string.ui_pos_interjection,
  "interrogative adverb" to R.string.ui_pos_interrogative_adverb,
  "interrogative expression" to R.string.ui_pos_interrogative_expression,
  "interrogative phrase" to R.string.ui_pos_interrogative_phrase,
  "interrogative pronoun" to R.string.ui_pos_interrogative_pronoun,
  "interrogative time phrase" to R.string.ui_pos_interrogative_time_phrase,
  "location noun" to R.string.ui_pos_location_noun,
  "location phrase" to R.string.ui_pos_location_phrase,
  "location word" to R.string.ui_pos_location_word,
  "locational noun" to R.string.ui_pos_locational_noun,
  "locational noun phrase" to R.string.ui_pos_locational_noun_phrase,
  "locative expression" to R.string.ui_pos_locative_expression,
  "locative noun" to R.string.ui_pos_locative_noun,
  "locative phrase" to R.string.ui_pos_locative_phrase,
  "locative verb phrase" to R.string.ui_pos_locative_verb_phrase,
  "measure word" to R.string.ui_pos_measure_word,
  "modal phrase" to R.string.ui_pos_modal_phrase,
  "modal verb" to R.string.ui_pos_modal_verb,
  "modal verb phrase" to R.string.ui_pos_modal_verb_phrase,
  "modifier" to R.string.ui_pos_modifier,
  "negative copular phrase" to R.string.ui_pos_negative_copular_phrase,
  "noun" to R.string.ui_pos_noun,
  "noun phrase" to R.string.ui_pos_noun_phrase,
  "number phrase" to R.string.ui_pos_number_phrase,
  "numeral" to R.string.ui_pos_numeral,
  "numeral-classifier phrase" to R.string.ui_pos_numeral_classifier_phrase,
  "numeral-noun phrase" to R.string.ui_pos_numeral_noun_phrase,
  "numeral-time phrase" to R.string.ui_pos_numeral_time_phrase,
  "onomatopoeia" to R.string.ui_pos_onomatopoeia,
  "ordinal number" to R.string.ui_pos_ordinal_number,
  "ordinal-classifier phrase" to R.string.ui_pos_ordinal_classifier_phrase,
  "particle" to R.string.ui_pos_particle,
  "phrase" to R.string.ui_pos_phrase,
  "postnominal phrase" to R.string.ui_pos_postnominal_phrase,
  "postposition" to R.string.ui_pos_postposition,
  "potential complement" to R.string.ui_pos_potential_complement,
  "preposition" to R.string.ui_pos_preposition,
  "prepositional phrase" to R.string.ui_pos_prepositional_phrase,
  "prepositional time phrase" to R.string.ui_pos_prepositional_time_phrase,
  "pronoun" to R.string.ui_pos_pronoun,
  "pronoun phrase" to R.string.ui_pos_pronoun_phrase,
  "proper noun" to R.string.ui_pos_proper_noun,
  "quantity phrase" to R.string.ui_pos_quantity_phrase,
  "resultative-aspect phrase" to R.string.ui_pos_resultative_aspect_phrase,
  "suffix" to R.string.ui_pos_suffix,
  "time adverb" to R.string.ui_pos_time_adverb,
  "time expression" to R.string.ui_pos_time_expression,
  "time localizer" to R.string.ui_pos_time_localizer,
  "time noun" to R.string.ui_pos_time_noun,
  "time noun phrase" to R.string.ui_pos_time_noun_phrase,
  "time phrase" to R.string.ui_pos_time_phrase,
  "time word" to R.string.ui_pos_time_word,
  "title" to R.string.ui_pos_title,
  "verb" to R.string.ui_pos_verb,
  "verb phrase" to R.string.ui_pos_verb_phrase,
  "verb-aspect construction" to R.string.ui_pos_verb_aspect_construction,
  "verb-complement construction" to R.string.ui_pos_verb_complement_construction,
)

private fun Context.withAppLanguage(language: AppLanguage,
  baseConfiguration: Configuration = resources.configuration): Context {
  val locale = if (language == AppLanguage.CN) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
  val configuration = Configuration(baseConfiguration).apply { setLocales(LocaleList(locale)) }
  // Compose finds Activity and result-registry owners by walking the base-context chain.
  return ContextThemeWrapper(this, theme).apply { applyOverrideConfiguration(configuration) }
}
