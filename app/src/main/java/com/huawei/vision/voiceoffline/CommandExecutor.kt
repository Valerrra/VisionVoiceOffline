package com.huawei.vision.voiceoffline

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

class CommandExecutor(private val context: Context) {

    companion object {
        private const val TAG = "CommandExecutor"
    }

    fun execute(cmd: ParsedCommand): CommandResult {
        val t0 = SystemClock.elapsedRealtime()

        return try {
            val result = when (cmd.type) {
                CommandType.OPEN_APP -> executeOpenApp(cmd)
                CommandType.VOLUME_CONTROL -> executeVolume(cmd)
                CommandType.MEDIA_CONTROL -> executeMedia(cmd)
                CommandType.SEARCH -> executeSearch(cmd)
                CommandType.SYSTEM_NAV -> executeNavigation(cmd)
                CommandType.DICTATION_TEXT -> CommandResult(
                    command = cmd,
                    success = true,
                    statusMessage = "Режим диктовки: выполнение команд отключено",
                    executedAction = "NONE"
                )
                CommandType.UNKNOWN -> CommandResult(
                    command = cmd,
                    success = false,
                    statusMessage = "Команда не распознана: '${cmd.rawText}'",
                    error = "UNKNOWN_OR_AMBIGUOUS_COMMAND"
                )
            }
            val elapsed = SystemClock.elapsedRealtime() - t0
            result.copy(executionTimeMs = elapsed)
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - t0
            Log.e(TAG, "Error executing command: ${cmd.action}", e)
            CommandResult(
                command = cmd,
                success = false,
                statusMessage = "Ошибка выполнения команды: ${e.message}",
                error = e.javaClass.simpleName,
                executionTimeMs = elapsed
            )
        }
    }

    private fun executeOpenApp(cmd: ParsedCommand): CommandResult {
        val appKey = cmd.target ?: return CommandResult(
            command = cmd,
            success = false,
            statusMessage = "Целевое приложение не указано",
            error = "MISSING_APP_TARGET"
        )

        val appAlias = AppRegistry.apps.firstOrNull { it.key == appKey }
            ?: return CommandResult(
                command = cmd,
                success = false,
                statusMessage = "Неизвестный идентификатор приложения: $appKey",
                error = "APP_NOT_REGISTERED"
            )

        // For settings: prefer ACTION_SETTINGS directly
        if (appAlias.key == "settings" || (appAlias.fallbackAction != null && appAlias.candidatePackages.isEmpty())) {
            try {
                val intent = Intent(appAlias.fallbackAction ?: android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return CommandResult(
                    command = cmd,
                    success = true,
                    statusMessage = "Открыты настройки системы (${appAlias.displayName})",
                    executedAction = "START_SETTINGS_ACTION",
                    targetPackage = "settings"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Direct fallback launch failed for ${appAlias.key}", e)
            }
        }

        val resolvedPkg = AppRegistry.resolveInstalledPackage(context, appAlias)

        if (resolvedPkg != null) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(resolvedPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return CommandResult(
                        command = cmd,
                        success = true,
                        statusMessage = "Запущено приложение: ${appAlias.displayName} ($resolvedPkg)",
                        executedAction = "START_ACTIVITY",
                        targetPackage = resolvedPkg
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch package $resolvedPkg, trying fallback action", e)
            }
        }

        // Fallback action if package launch failed or is restricted
        if (appAlias.fallbackAction != null) {
            try {
                val fallbackIntent = Intent(appAlias.fallbackAction).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                return CommandResult(
                    command = cmd,
                    success = true,
                    statusMessage = "Открыто системное действие: ${appAlias.displayName}",
                    executedAction = "START_FALLBACK_ACTION",
                    targetPackage = resolvedPkg
                )
            } catch (e: Exception) {
                Log.w(TAG, "Fallback action failed: ${appAlias.fallbackAction}", e)
            }
        }

        return CommandResult(
            command = cmd,
            success = false,
            statusMessage = "Приложение '${appAlias.displayName}' не установлено на этом телевизоре",
            error = "PACKAGE_NOT_INSTALLED",
            targetPackage = appAlias.candidatePackages.firstOrNull()
        )
    }

    private fun executeVolume(cmd: ParsedCommand): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val direction = when (cmd.action) {
            "UP" -> AudioManager.ADJUST_RAISE
            "DOWN" -> AudioManager.ADJUST_LOWER
            "MUTE" -> AudioManager.ADJUST_MUTE
            "UNMUTE" -> AudioManager.ADJUST_UNMUTE
            else -> return CommandResult(
                command = cmd,
                success = false,
                statusMessage = "Неизвестное действие громкости: ${cmd.action}",
                error = "INVALID_VOLUME_ACTION"
            )
        }

        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)

        val actionDesc = when (cmd.action) {
            "UP" -> "Громкость увеличена"
            "DOWN" -> "Громкость уменьшена"
            "MUTE" -> "Звук отключён (Mute)"
            "UNMUTE" -> "Звук включён (Unmute)"
            else -> cmd.action
        }

        return CommandResult(
            command = cmd,
            success = true,
            statusMessage = actionDesc,
            executedAction = "ADJUST_STREAM_VOLUME_${cmd.action}"
        )
    }

    private fun executeMedia(cmd: ParsedCommand): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val keyCode = when (cmd.action) {
            "PAUSE" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "PLAY" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "PLAY_PAUSE" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "NEXT" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "PREVIOUS" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return CommandResult(
                command = cmd,
                success = false,
                statusMessage = "Неизвестная медиа-команда: ${cmd.action}",
                error = "INVALID_MEDIA_ACTION"
            )
        }

        // Standard Android MediaKey dispatch to active MediaSession
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))

        val desc = when (cmd.action) {
            "PAUSE" -> "Воспроизведение приостановлено (Пауза)"
            "PLAY" -> "Воспроизведение возобновлено (Play)"
            "NEXT" -> "Переключение на следующий трек (Next)"
            "PREVIOUS" -> "Переключение на предыдущий трек (Previous)"
            else -> cmd.action
        }

        return CommandResult(
            command = cmd,
            success = true,
            statusMessage = desc,
            executedAction = "DISPATCH_MEDIA_KEY_${cmd.action}"
        )
    }

    private fun executeSearch(cmd: ParsedCommand): CommandResult {
        // Requirement 8: For SEARCH extract query text and return structured result without integrating into 3rd party apps yet
        val query = (cmd.params["query"] as? String) ?: cmd.rawText
        val searchType = (cmd.params["searchType"] as? String) ?: "general"

        return CommandResult(
            command = cmd,
            success = true,
            statusMessage = "Поисковый запрос структурирован: '$query' [тип: $searchType]",
            executedAction = "STRUCTURED_SEARCH_INTENT"
        )
    }

    private fun executeNavigation(cmd: ParsedCommand): CommandResult {
        return when (cmd.action) {
            "HOME" -> {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(homeIntent)
                CommandResult(
                    command = cmd,
                    success = true,
                    statusMessage = "Переход на главный экран телевизора",
                    executedAction = "GO_HOME"
                )
            }
            "BACK" -> {
                CommandResult(
                    command = cmd,
                    success = true,
                    statusMessage = "Навигация назад (зафиксировано)",
                    executedAction = "NAV_BACK"
                )
            }
            else -> CommandResult(
                command = cmd,
                success = false,
                statusMessage = "Неизвестное действие навигации: ${cmd.action}",
                error = "INVALID_NAV_ACTION"
            )
        }
    }
}
