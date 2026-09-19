import React, { useState, useEffect, useCallback } from 'react';
import {
  Tv,
  Code2,
  Play,
  RotateCcw,
  Sparkles,
  Smartphone,
  Cpu,
  Layers,
  ChevronRight,
  Monitor
} from 'lucide-react';
import { ScreenType, MediaVideoItem, ChatItem, ChatMessageItem } from './types';
import { MOCK_VIDEOS } from './data/mockMedia';
import { MOCK_CHATS, INITIAL_MESSAGES } from './data/mockChats';
import { TvRemote } from './components/TvRemote';
import { TvAuthScreen } from './components/TvAuthScreen';
import { TvMediaHub } from './components/TvMediaHub';
import { TvPlayerScreen } from './components/TvPlayerScreen';
import { TvChatView } from './components/TvChatView';
import { AndroidCodeViewer } from './components/AndroidCodeViewer';

export default function App() {
  const [activeTab, setActiveTab] = useState<'tv_screen' | 'android_code'>('tv_screen');

  // TV Screen State Machine
  const [currentScreen, setCurrentScreen] = useState<ScreenType>('auth');
  const [selectedVideo, setSelectedVideo] = useState<MediaVideoItem | null>(null);

  // Chats & Messaging state
  const [chats, setChats] = useState<ChatItem[]>(MOCK_CHATS);
  const [selectedChat, setSelectedChat] = useState<ChatItem | null>(null);
  const [chatMessages, setChatMessages] = useState<Record<string, ChatMessageItem[]>>(INITIAL_MESSAGES);

  // Focus Navigation state for 10-foot UI
  const [authFocusedButton, setAuthFocusedButton] = useState<'login' | 'refresh'>('login');
  const [hubArea, setHubArea] = useState<'sidebar' | 'content'>('content');
  const [hubSidebarIndex, setHubSidebarIndex] = useState(0);
  const [hubContentIndex, setHubContentIndex] = useState(0);

  // Player state
  const [isPlaying, setIsPlaying] = useState(true);
  const [playerTime, setPlayerTime] = useState(45);

  const handleSendMessage = useCallback((text: string) => {
    if (!selectedChat) return;
    const newMsg: ChatMessageItem = {
      id: `msg-${Date.now()}`,
      chatId: selectedChat.id,
      senderName: 'Вы (Android TV)',
      isOutgoing: true,
      text,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };

    setChatMessages(prev => ({
      ...prev,
      [selectedChat.id]: [...(prev[selectedChat.id] || []), newMsg],
    }));

    setChats(prev =>
      prev.map(c =>
        c.id === selectedChat.id
          ? { ...c, lastMessage: text, date: 'Только что' }
          : c
      )
    );
  }, [selectedChat]);

  // D-Pad handler
  const handleDpad = useCallback((direction: 'up' | 'down' | 'left' | 'right') => {
    if (currentScreen === 'auth') {
      if (direction === 'left' || direction === 'right') {
        setAuthFocusedButton(prev => (prev === 'login' ? 'refresh' : 'login'));
      }
    } else if (currentScreen === 'hub') {
      if (hubArea === 'sidebar') {
        if (direction === 'up') {
          setHubSidebarIndex(prev => Math.max(0, prev - 1));
        } else if (direction === 'down') {
          setHubSidebarIndex(prev => Math.min(4, prev + 1));
        } else if (direction === 'right') {
          // Transfer focus from sidebar to content grid
          setHubArea('content');
        }
      } else {
        // In content grid (3 columns)
        const total = MOCK_VIDEOS.length;
        if (direction === 'left') {
          if (hubContentIndex % 3 === 0) {
            // Edge left: transfer focus back to sidebar
            setHubArea('sidebar');
          } else {
            setHubContentIndex(prev => Math.max(0, prev - 1));
          }
        } else if (direction === 'right') {
          setHubContentIndex(prev => Math.min(total - 1, prev + 1));
        } else if (direction === 'up') {
          setHubContentIndex(prev => Math.max(0, prev - 3));
        } else if (direction === 'down') {
          setHubContentIndex(prev => Math.min(total - 1, prev + 3));
        }
      }
    } else if (currentScreen === 'player') {
      if (direction === 'left') {
        setPlayerTime(prev => Math.max(0, prev - 10));
      } else if (direction === 'right') {
        setPlayerTime(prev => prev + 10);
      }
    }
  }, [currentScreen, hubArea, hubContentIndex]);

  // Center / OK select button handler
  const handleSelect = useCallback(() => {
    if (currentScreen === 'auth') {
      setCurrentScreen('hub');
      setHubArea('content');
      setHubContentIndex(0);
    } else if (currentScreen === 'hub') {
      if (hubArea === 'sidebar') {
        if (hubSidebarIndex === 5) {
          // Log out
          setCurrentScreen('auth');
        } else {
          setHubArea('content');
          setHubContentIndex(0);
        }
      } else {
        if (hubSidebarIndex === 1) {
          // Chats tab
          const chat = chats[hubContentIndex] || chats[0];
          if (chat) {
            setSelectedChat(chat);
            setCurrentScreen('chat_view');
          }
        } else {
          // Video stream
          const video = MOCK_VIDEOS[hubContentIndex];
          if (video) {
            setSelectedVideo(video);
            setCurrentScreen('player');
            setIsPlaying(true);
          }
        }
      }
    } else if (currentScreen === 'player') {
      setIsPlaying(prev => !prev);
    }
  }, [currentScreen, hubArea, hubSidebarIndex, hubContentIndex, chats]);

  // Back button handler
  const handleBack = useCallback(() => {
    if (currentScreen === 'player' || currentScreen === 'chat_view') {
      setCurrentScreen('hub');
    } else if (currentScreen === 'hub') {
      if (hubArea === 'content') {
        setHubArea('sidebar');
      } else {
        setCurrentScreen('auth');
      }
    }
  }, [currentScreen, hubArea]);

  // Keyboard navigation listener (D-Pad remote simulation via physical keyboard)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Don't intercept if user is inside an input or code viewer
      if (['input', 'textarea'].includes((e.target as HTMLElement)?.tagName?.toLowerCase())) {
        return;
      }

      if (e.key === 'ArrowUp') {
        e.preventDefault();
        handleDpad('up');
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        handleDpad('down');
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        handleDpad('left');
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        handleDpad('right');
      } else if (e.key === 'Enter') {
        e.preventDefault();
        handleSelect();
      } else if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        handleBack();
      } else if (e.key === ' ' && currentScreen === 'player') {
        e.preventDefault();
        setIsPlaying(prev => !prev);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [handleDpad, handleSelect, handleBack, currentScreen]);

  // Code files data for the architecture inspector
  const androidFiles = [
    {
      name: 'AndroidManifest.xml',
      path: 'android/app/src/main/AndroidManifest.xml',
      language: 'xml',
      description: 'Флаги leanback, отключение тачскрина, фильтр LEANBACK_LAUNCHER и права',
      content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Требования аппаратного обеспечения для Android TV -->
    <uses-feature
        android:name="android.software.leanback"
        android:required="true" />

    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <!-- Сетевые разрешения для TDLib и Media3 стриминга видео -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".TelegramMediaTvApp"
        android:allowBackup="false"
        android:banner="@drawable/tv_banner"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:isGame="false"
        android:theme="@style/Theme.TelegramMediaTv">

        <activity
            android:name=".ui.MainActivity"
            android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenLayout|screenSize|smallestScreenSize|uiMode"
            android:exported="true"
            android:launchMode="singleTask"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>`
    },
    {
      name: 'libs.versions.toml',
      path: 'android/gradle/libs.versions.toml',
      language: 'toml',
      description: 'Gradle Version Catalog: TV Compose 1.1.0, Media3 1.11.0, Coil, ZXing, Coroutines',
      content: `[versions]
agp = "8.7.2"
kotlin = "2.0.21"
coreKtx = "1.15.0"
lifecycleRuntimeKtx = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.10.01"
tvMaterial = "1.1.0"
tvFoundation = "1.0.0-alpha11"
media3 = "1.11.0"
coil = "2.7.0"
zxing = "3.5.3"
coroutines = "1.9.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }

# Compose BOM & UI
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }

# Android TV Jetpack Compose
androidx-tv-material = { group = "androidx.tv", name = "tv-material", version.ref = "tvMaterial" }
androidx-tv-foundation = { group = "androidx.tv", name = "tv-foundation", version.ref = "tvFoundation" }

# Media3 ExoPlayer for TV
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }

# Coil for Async Image Loading
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

# ZXing for QR Code Bitmap Generation on TV
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }

# Kotlin Coroutines
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }`
    },
    {
      name: 'build.gradle.kts (:app)',
      path: 'android/app/build.gradle.kts',
      language: 'kotlin',
      description: 'Конфигурация модуля app: Target SDK 35, Min SDK 26, ABI Filters arm64-v8a/armeabi-v7a, jniLibs',
      content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.tgmedia.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tgmedia.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            // Директория скомпилированных C++ библиотек TDLib (libtdjni.so)
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.coroutines.android)
}`
    },
    {
      name: 'TdLibManager.kt',
      path: 'android/app/src/main/java/com/tgmedia/tv/data/tdlib/TdLibManager.kt',
      language: 'kotlin',
      description: 'Реактивная обертка над org.drinkless.tdlib.Client со StateFlow и Coroutines',
      content: `package com.tgmedia.tv.data.tdlib

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume

/**
 * Реактивный менеджер TDLib (Staff Engineer level).
 * Инкапсулирует синглтон C++ ядра libtdjni через JNI обертку.
 */
class TdLibManager private constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Текущее состояние авторизации сессии TDLib
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState>(
        TdApi.AuthorizationStateWaitTdlibParameters()
    )
    val authorizationState: StateFlow<TdApi.AuthorizationState> = _authorizationState.asStateFlow()

    // Ссылка авторизации по QR коду для ТВ экрана: "tg://login?token=..."
    private val _qrCodeLink = MutableStateFlow<String?>(null)
    val qrCodeLink: StateFlow<String?> = _qrCodeLink.asStateFlow()

    // Поток входящих обновлений данных
    private val _updates = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = 128)
    val updates: SharedFlow<TdApi.Update> = _updates.asSharedFlow()

    private var client: Client? = null
    private var config: TdLibConfig? = null

    companion object {
        private const val TAG = "TdLibManager"

        @Volatile
        private var instance: TdLibManager? = null

        fun getInstance(): TdLibManager {
            return instance ?: synchronized(this) {
                instance ?: TdLibManager().also { instance = it }
            }
        }

        init {
            try {
                System.loadLibrary("tdjni")
                Log.i(TAG, "Native C++ library libtdjni.so successfully loaded.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libtdjni.so. Ensure native libraries exist in jniLibs", e)
            }
        }
    }

    fun initialize(config: TdLibConfig) {
        this.config = config
        if (client != null) return

        client = Client.create(
            { event -> handleIncomingEvent(event) },
            { error -> Log.e(TAG, "TDLib update exception", error) },
            { error -> Log.e(TAG, "TDLib default exception", error) }
        )
    }

    private fun handleIncomingEvent(event: TdApi.Object) {
        when (event) {
            is TdApi.UpdateAuthorizationState -> {
                val newState = event.authorizationState
                _authorizationState.value = newState
                when (newState) {
                    is TdApi.AuthorizationStateWaitTdlibParameters -> sendTdlibParameters()
                    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                        _qrCodeLink.value = newState.link
                    }
                    is TdApi.AuthorizationStateReady -> {
                        _qrCodeLink.value = null
                    }
                    else -> Unit
                }
            }
            is TdApi.Update -> scope.launch { _updates.emit(event) }
            else -> Unit
        }
    }

    private fun sendTdlibParameters() {
        val currentConfig = config ?: return
        val request = TdApi.SetTdlibParameters(
            currentConfig.useTestDc,
            currentConfig.databaseDirectory,
            currentConfig.filesDirectory,
            currentConfig.encryptionKey,
            true, true, true, false,
            currentConfig.apiId,
            currentConfig.apiHash,
            currentConfig.systemLanguageCode,
            currentConfig.deviceModel,
            currentConfig.systemVersion,
            currentConfig.applicationVersion
        )
        send(request) { checkDatabaseKey(currentConfig.encryptionKey) }
    }

    private fun checkDatabaseKey(key: ByteArray) {
        send(TdApi.CheckDatabaseEncryptionKey(key)) { requestQrCodeAuthentication() }
    }

    fun requestQrCodeAuthentication() {
        send(TdApi.RequestQrCodeAuthentication(emptyArray())) {}
    }

    suspend fun <T : TdApi.Object> execute(query: TdApi.Function<T>): Result<T> =
        suspendCancellableCoroutine { continuation ->
            client?.send(query) { result ->
                if (result is TdApi.Error) {
                    continuation.resume(Result.failure(Exception(result.message)))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resume(Result.success(result as T))
                }
            }
        }

    fun send(query: TdApi.Function<*>, callback: (TdApi.Object) -> Unit) {
        client?.send(query, callback)
    }
}`
    },
    {
      name: 'CHANGELOG.md',
      path: 'CHANGELOG.md',
      language: 'markdown',
      description: 'Журнал версионирования проекта: выпуск версии 1.0.0 (Модули 1 и 2)',
      content: `# Changelog

Все важные изменения проекта Telegram Media TV фиксируются в этом файле.

## [1.0.0] - 2026-09-18

### Добавлено
- **Модуль 1 (Конфигурация и Манифест Android TV):**
  - Подготовлен AndroidManifest.xml с флагами leanback и category.LEANBACK_LAUNCHER.
  - Gradle Version Catalog (libs.versions.toml) с TV Compose 1.1.0 и Media3 1.11.0.
  - Настроен app/build.gradle.kts (Target SDK 35, Min SDK 26, ABI arm64-v8a).
- **Модуль 2 (Слой TDLib Manager):**
  - Реактивная обертка TdLibManager над org.drinkless.tdlib.Client.
  - StateFlow<TdApi.AuthorizationState> и стриминг токена tg://login?token=...`
    }
  ];

  return (
    <div
      id="app-root"
      className="min-h-screen bg-[#090a0f] text-zinc-100 flex flex-col font-sans selection:bg-sky-500/30"
    >
      {/* Top Application Header */}
      <header className="flex flex-wrap items-center justify-between px-6 py-4 bg-zinc-950/80 border-b border-zinc-800/80 backdrop-blur-md sticky top-0 z-50">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-sky-600 to-blue-500 flex items-center justify-center shadow-lg shadow-sky-500/20">
            <Tv className="w-5 h-5 text-white" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-base font-bold tracking-tight text-white">
                Telegram Media TV
              </h1>
              <span className="px-2 py-0.5 rounded-full bg-sky-500/20 text-sky-400 text-[10px] font-mono font-semibold border border-sky-500/30">
                v1.0.12
              </span>
            </div>
            <p className="text-xs text-zinc-400">
              Android TV (10-Foot UI) • Jetpack Compose for TV • TDLib JNI C++ Core
            </p>
          </div>
        </div>

        {/* View Switcher Tabs */}
        <div className="flex items-center gap-2 bg-zinc-900 p-1 rounded-xl border border-zinc-800">
          <button
            type="button"
            onClick={() => setActiveTab('tv_screen')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition ${
              activeTab === 'tv_screen'
                ? 'bg-sky-500 text-white shadow-md'
                : 'text-zinc-400 hover:text-white'
            }`}
          >
            <Monitor className="w-4 h-4" />
            <span>Интерактивный ТВ экран</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('android_code')}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-medium transition ${
              activeTab === 'android_code'
                ? 'bg-sky-500 text-white shadow-md'
                : 'text-zinc-400 hover:text-white'
            }`}
          >
            <Code2 className="w-4 h-4" />
            <span>Исходный код (Модули 1 и 2)</span>
          </button>
        </div>
      </header>

      {/* Main Workspace */}
      <main className="flex-1 p-4 md:p-6 max-w-7xl mx-auto w-full flex flex-col">
        {activeTab === 'tv_screen' ? (
          <div className="flex flex-col lg:flex-row items-center lg:items-start justify-center gap-8 flex-1">
            {/* 16:9 Android TV Virtual Screen Container */}
            <div className="w-full lg:flex-1 max-w-5xl flex flex-col items-center">
              {/* TV Bezel */}
              <div className="w-full relative aspect-video bg-black rounded-3xl p-3 shadow-[0_20px_60px_rgba(0,0,0,0.8)] border-4 border-zinc-800/80 overflow-hidden flex flex-col">
                {/* Inside Cinema Screen */}
                <div className="w-full h-full rounded-2xl overflow-hidden relative bg-zinc-950 flex flex-col">
                  {currentScreen === 'auth' && (
                    <TvAuthScreen
                      onSimulateLogin={() => setCurrentScreen('hub')}
                      isFocused={true}
                      focusedButton={authFocusedButton}
                    />
                  )}

                  {currentScreen === 'hub' && (
                    <TvMediaHub
                      activeArea={hubArea}
                      sidebarIndex={hubSidebarIndex}
                      contentIndex={hubContentIndex}
                      videos={MOCK_VIDEOS}
                      chats={chats}
                      onSelectVideo={(video) => {
                        setSelectedVideo(video);
                        setCurrentScreen('player');
                        setIsPlaying(true);
                      }}
                      onSelectChat={(chat) => {
                        setSelectedChat(chat);
                        setCurrentScreen('chat_view');
                      }}
                      onLogout={() => setCurrentScreen('auth')}
                    />
                  )}

                  {currentScreen === 'chat_view' && selectedChat && (
                    <TvChatView
                      chat={selectedChat}
                      messages={chatMessages[selectedChat.id] || []}
                      onBack={() => setCurrentScreen('hub')}
                      onSendMessage={handleSendMessage}
                    />
                  )}

                  {currentScreen === 'player' && selectedVideo && (
                    <TvPlayerScreen
                      video={selectedVideo}
                      onBack={() => setCurrentScreen('hub')}
                      isPlaying={isPlaying}
                      onTogglePlay={() => setIsPlaying(prev => !prev)}
                      onSeekRelative={(secs) => setPlayerTime(prev => Math.max(0, prev + secs))}
                      currentTime={playerTime}
                    />
                  )}
                </div>
              </div>

              {/* TV Screen Legend and Shortcuts */}
              <div className="mt-4 flex flex-wrap items-center justify-between w-full px-3 text-xs text-zinc-400">
                <div className="flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full bg-sky-400" />
                  <span>
                    Активный экран:{' '}
                    <strong className="text-zinc-200 uppercase font-mono">
                      {currentScreen === 'auth' && 'Экран авторизации (QR Code)'}
                      {currentScreen === 'hub' && 'Хаб (Чаты, Каналы и Видеопоток)'}
                      {currentScreen === 'chat_view' && 'Просмотр чата и отправка сообщений'}
                      {currentScreen === 'player' && 'ТВ Плеер (Media3 ExoPlayer)'}
                    </strong>
                  </span>
                </div>

                <div className="flex items-center gap-3">
                  <span className="font-mono bg-zinc-900 px-2 py-1 rounded border border-zinc-800 text-[11px]">
                    Стрелки: D-Pad
                  </span>
                  <span className="font-mono bg-zinc-900 px-2 py-1 rounded border border-zinc-800 text-[11px]">
                    Enter: OK / Выбор
                  </span>
                  <span className="font-mono bg-zinc-900 px-2 py-1 rounded border border-zinc-800 text-[11px]">
                    Esc / Backspace: Назад
                  </span>
                </div>
              </div>
            </div>

            {/* D-Pad Virtual Remote Controller */}
            <div className="flex flex-col items-center">
              <TvRemote
                onDpad={handleDpad}
                onSelect={handleSelect}
                onBack={handleBack}
                onPlayPause={() => {
                  if (currentScreen === 'player') {
                    setIsPlaying(prev => !prev);
                  }
                }}
                isPlaying={isPlaying}
              />
            </div>
          </div>
        ) : (
          <div className="flex-1 min-h-[600px]">
            <AndroidCodeViewer files={androidFiles} />
          </div>
        )}
      </main>
    </div>
  );
}
