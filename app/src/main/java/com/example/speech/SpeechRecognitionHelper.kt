package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class SpeechRecognitionHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var previousTranscriptAccumulator = ""

    fun startListening() {
        _errorMessage.value = null
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorMessage.value = "Speech recognition service is unavailable. Text input is supported."
            return
        }

        try {
            stopListening()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        _rmsDb.value = (rmsdB + 2f).coerceAtLeast(0f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
                            SpeechRecognizer.ERROR_NO_MATCH -> null // normal pause
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null // normal pause
                            else -> "Recognition issue ($error)"
                        }
                        if (msg != null) _errorMessage.value = msg
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val newPart = matches[0]
                            previousTranscriptAccumulator = if (previousTranscriptAccumulator.isBlank()) {
                                newPart
                            } else {
                                "$previousTranscriptAccumulator $newPart"
                            }
                            _transcript.value = previousTranscriptAccumulator
                        }
                        _isListening.value = false
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partial = matches[0]
                            _transcript.value = if (previousTranscriptAccumulator.isBlank()) {
                                partial
                            } else {
                                "$previousTranscriptAccumulator $partial"
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _errorMessage.value = "Could not initialize speech: ${e.message}"
            _isListening.value = false
        }
    }

    fun appendManualText(text: String) {
        previousTranscriptAccumulator = if (previousTranscriptAccumulator.isBlank()) text else "$previousTranscriptAccumulator $text"
        _transcript.value = previousTranscriptAccumulator
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        _isListening.value = false
    }

    fun reset() {
        stopListening()
        previousTranscriptAccumulator = ""
        _transcript.value = ""
        _errorMessage.value = null
        _rmsDb.value = 0f
    }
}
