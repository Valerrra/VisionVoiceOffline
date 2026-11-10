package com.huawei.vision.voiceoffline

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
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
                CommandType.CLOSE_APP -> executeCloseApp(cmd)
                CommandType.MINIMIZE_APP -> executeMinimizeApp(cmd)
                CommandType.VOLUME_CONTROL -> executeVolume(cmd)
                CommandType.MEDIA_CONTROL -> executeMedia(cmd)
                CommandType.SEARCH -> executeSearch(cmd)
                CommandType.SYSTEM_BACK, CommandType.SYSTEM_NAV -> executeNavigation(cmd)
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
            val finalResult = result.copy(executionTimeMs = elapsed)

            // Persist to local history
            try {
                CommandHistoryManager.getInstance(context).addEntry(
                    rawText = cmd.rawText,
                    intent = cmd.type.name,
                    target = cmd.target,
                    statusMessage = finalResult.statusMessage,
                    success = finalResult.success,
                    error = finalResult.error
                )
            } catch (he: Exception) {
                Log.w(TAG, "Error saving history entry", he)
            }

            finalResult
        } catch (e: Exception) {
            val elapsed = SystemClock.elapsedRealtime() - t0
            Log.e(TAG, "Error executing command: ${cmd.action}", e)
            val errResult = CommandResult(
                command = cmd,
                success = false,
                statusMessage = "Ошибка выполнения команды: ${e.message}",
                error = e.javaClass.simpleName,
                executionTimeMs = elapsed
            )
            try {
                CommandHistoryManager.getInstance(context).addEntry(
                    rawText = cmd.rawText,
                    intent = cmd.type.name,
                    target = cmd.target,
                    statusMessage = errResult.statusMessage,
                    success = false,
                    error = errResult.error
                )
            } catch (_: Exception) {}
            errResult
        }
    }

    private fun executeOpenApp(cmd: ParsedCommand): CommandResult {
        // If parser already determined app was not found
        if (cmd.action == "NOT_FOUND") {
            val query = (cmd.params["query"] as? String) ?: cmd.target ?: cmd.rawText
            return CommandResult(
                command = cmd,
                success = false,
                statusMessage = "Приложение '$query' не найдено на телевизоре",
                executedAction = "APP_NOT_FOUND",
                error = "PACKAGE_NOT_INSTALLED"
            )
        }

        val appKey = cmd.target ?: return CommandResult(
            command = cmd,
            success = false,
            statusMessage = "Целевое приложение не указано",
            error = "MISSING_APP_TARGET"
        )

        // Find in DynamicAppRegistry
        val appMatch = AppRegistry.findApp(appKey)
        val app = appMatch?.app ?: AppRegistry.getAllApps().firstOrNull { it.key == appKey }

        val targetPkg = (cmd.params["packageName"] as? String) ?: app?.packageName
        val displayName = (cmd.params["displayName"] as? String) ?: app?.appLabel ?: appKey

        // Check ambiguity
        if (cmd.params["isAmbiguous"] == true || appMatch?.isAmbiguous == true) {
            Log.w(TAG, "Ambiguous app match for query '$appKey'")
        }

        // 1. Settings special handling
        if (appKey == "settings" || app?.fallbackAction == Settings.ACTION_SETTINGS) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return CommandResult(
                    command = cmd,
                    success = true,
                    statusMessage = "Открыты настройки системы ($displayName)",
                    executedAction = "START_SETTINGS_ACTION",
                    targetPackage = targetPkg ?: "com.android.tv.settings"
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch Settings via ACTION_SETTINGS", e)
            }
        }

        // 2. Launch via PackageManager launch intent
        if (targetPkg != null) {
            try {
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage(targetPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return CommandResult(
                        command = cmd,
                        success = true,
                        statusMessage = "Запущено приложение: $displayName ($targetPkg)",
                        executedAction = "START_ACTIVITY",
                        targetPackage = targetPkg
                    )
                }

                // 2b. If activityName is known (e.g. from Leanback query), launch explicit component
                if (app?.activityName != null) {
                    val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                        component = ComponentName(targetPkg, app.activityName)
                        addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(explicitIntent)
                    return CommandResult(
                        command = cmd,
                        success = true,
                        statusMessage = "Запущен компонент: $displayName (${app.activityName})",
                        executedAction = "START_COMPONENT",
                        targetPackage = targetPkg
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch package $targetPkg", e)
            }
        }

        // 3. Fallback action if defined
        val fallback = app?.fallbackAction
        if (fallback != null) {
            try {
                val fallbackIntent = Intent(fallback).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                return CommandResult(
                    command = cmd,
                    success = true,
                    statusMessage = "Открыто системное действие: $displayName",
                    executedAction = "START_FALLBACK_ACTION",
                    targetPackage = targetPkg
                )
            } catch (e: Exception) {
                Log.w(TAG, "Fallback action failed: $fallback", e)
            }
        }

        return CommandResult(
            command = cmd,
            success = false,
            statusMessage = "Приложение '$displayName' не установлено на этом телевизоре",
            error = "PACKAGE_NOT_INSTALLED",
            targetPackage = targetPkg
        )
    }

    private fun executeCloseApp(cmd: ParsedCommand): CommandResult {
        // STRICT PROHIBITION: Under NO circumstances should CLOSE_APP launch the app!
        val displayName = (cmd.params["displayName"] as? String) ?: cmd.target ?: "приложения"
        val pkg = (cmd.params["packageName"] as? String)

        if (cmd.action == "NOT_FOUND") {
            val query = (cmd.params["query"] as? String) ?: cmd.target ?: cmd.rawText
            return CommandResult(
                command = cmd,
                success = false,
                statusMessage = "Приложение '$query' не найдено для закрытия",
                executedAction = "CLOSE_NOT_FOUND",
                error = "PACKAGE_NOT_FOUND"
            )
        }

        if (pkg != null) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(pkg)
            } catch (e: Exception) {
                Log.w(TAG, "killBackgroundProcesses failed for $pkg", e)
            }
        }

        // Honest explanation: Android security policy restricts 3rd-party apps from force-stopping foreground apps.
        val msg = if (cmd.action == "CLOSE_CURRENT") {
            "Закрытие текущего приложения ограничено политикой Android. Скажите 'домой' или 'сверни'."
        } else {
            "Закрытие '$displayName' ограничено политикой безопасности Android (требуются права root/системы). Скажите 'домой' или 'сверни'."
        }

        return CommandResult(
            command = cmd,
            success = false,
            statusMessage = msg,
            executedAction = "UNSUPPORTED_CLOSE_APP",
            targetPackage = pkg,
            error = "SECURITY_POLICY_RESTRICTION"
        )
    }

    private fun executeMinimizeApp(cmd: ParsedCommand): CommandResult {
        return navigateToHomeOrMinimize(cmd, isMinimize = true)
    }

    private fun navigateToHomeOrMinimize(cmd: ParsedCommand, isMinimize: Boolean): CommandResult {
        // 1. If AccessibilityService is active, perform system GLOBAL_ACTION_HOME
        if (VisionVoiceAccessibilityService.isRunning) {
            val success = VisionVoiceAccessibilityService.performHome()
            if (success) {
                return CommandResult(
                    command = cmd,
                    success = true,
                    statusMessage = if (isMinimize) "Приложение свёрнуто (Главный экран)" else "Переход на главный экран телевизора",
                    executedAction = "GLOBAL_ACTION_HOME"
                )
            }
        }

        // 2. If running inside an Activity context, attempt moveTaskToBack for minimize
        if (isMinimize && context is android.app.Activity) {
            context.moveTaskToBack(true)
            return CommandResult(
                command = cmd,
                success = true,
                statusMessage = "Приложение свёрнуто в фон (moveTaskToBack)",
                executedAction = "MOVE_TASK_TO_BACK"
            )
        }

        // 3. Fallback: Check if ordinary ACTION_MAIN + CATEGORY_HOME works without special permissions
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            return CommandResult(
                command = cmd,
                success = true,
                statusMessage = if (isMinimize) "Приложение свёрнуто (Главный экран)" else "Переход на главный экран телевизора",
                executedAction = if (isMinimize) "MINIMIZE_TO_HOME" else "GO_HOME"
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Direct CATEGORY_HOME denied: requires huawei.android.permission.ABILITY_FOR_HIVOICE")
        } catch (e: Exception) {
            Log.w(TAG, "Error launching standard HOME intent", e)
        }

        // 4. Return clear user-friendly status explaining AccessibilityService requirement per M4.7 spec
        return CommandResult(
            command = cmd,
            success = false,
            statusMessage = "Для перехода «Домой» включите службу навигации: Настройки -> Специальные возможности -> VisionVoice",
            executedAction = "REQUIRES_ACCESSIBILITY",
            error = "ACCESSIBILITY_SERVICE_NOT_ENABLED"
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
        return com.huawei.vision.voiceoffline.search.SearchCoordinator.processSearchCommand(cmd, context)
    }

    private fun executeNavigation(cmd: ParsedCommand): CommandResult {
        return when (cmd.action) {
            "HOME" -> navigateToHomeOrMinimize(cmd, isMinimize = false)
            "BACK" -> {
                if (VisionVoiceAccessibilityService.isRunning) {
                    val success = VisionVoiceAccessibilityService.performBack()
                    if (success) {
                        return CommandResult(
                            command = cmd,
                            success = true,
                            statusMessage = "Навигация назад (Back)",
                            executedAction = "GLOBAL_ACTION_BACK"
                        )
                    }
                }
                if (context is android.app.Activity) {
                    context.runOnUiThread { context.onBackPressed() }
                    return CommandResult(
                        command = cmd,
                        success = true,
                        statusMessage = "Навигация назад (Back)",
                        executedAction = "ACTIVITY_ON_BACK_PRESSED"
                    )
                }
                CommandResult(
                    command = cmd,
                    success = false,
                    statusMessage = "Для навигации «Назад» включите службу навигации: Настройки -> Специальные возможности -> VisionVoice",
                    executedAction = "REQUIRES_ACCESSIBILITY",
                    error = "ACCESSIBILITY_SERVICE_NOT_ENABLED"
                )
            }
            "RECENTS" -> {
                if (VisionVoiceAccessibilityService.isRunning) {
                    val success = VisionVoiceAccessibilityService.performRecents()
                    if (success) {
                        return CommandResult(
                            command = cmd,
                            success = true,
                            statusMessage = "Список запущенных приложений (Recents)",
                            executedAction = "GLOBAL_ACTION_RECENTS"
                        )
                    }
                }
                CommandResult(
                    command = cmd,
                    success = false,
                    statusMessage = "Для вызова недавних приложений включите службу навигации: Настройки -> Специальные возможности -> VisionVoice",
                    executedAction = "REQUIRES_ACCESSIBILITY",
                    error = "ACCESSIBILITY_SERVICE_NOT_ENABLED"
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