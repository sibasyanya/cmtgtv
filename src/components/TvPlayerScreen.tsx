import React, { useState, useEffect, useRef } from 'react';
import {
  Play,
  Pause,
  RotateCcw,
  Volume2,
  ArrowLeft,
  FastForward,
  Rewind,
  CheckCircle,
  Wifi,
  Film
} from 'lucide-react';
import { MediaVideoItem } from '../types';

interface TvPlayerScreenProps {
  video: MediaVideoItem;
  onBack: () => void;
  isPlaying: boolean;
  onTogglePlay: () => void;
  onSeekRelative: (seconds: number) => void;
  currentTime: number;
}

export const TvPlayerScreen: React.FC<TvPlayerScreenProps> = ({
  video,
  onBack,
  isPlaying,
  onTogglePlay,
  onSeekRelative,
  currentTime,
}) => {
  const [showControls, setShowControls] = useState(true);
  const hideTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Auto-hide controls after 3 seconds of inactivity
  useEffect(() => {
    if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    setShowControls(true);
    hideTimerRef.current = setTimeout(() => {
      if (isPlaying) {
        setShowControls(false);
      }
    }, 3500);

    return () => {
      if (hideTimerRef.current) clearTimeout(hideTimerRef.current);
    };
  }, [currentTime, isPlaying]);

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const progressPercent = Math.min(100, (currentTime / video.durationSeconds) * 100);

  return (
    <div
      id="tv-player-screen"
      className="relative w-full h-full bg-black flex items-center justify-center overflow-hidden select-none"
    >
      {/* Real HTML5 Video element matching Media3 PlayerView */}
      <video
        id="media3-video-element"
        src={video.videoUrl}
        poster={video.thumbnailUrl}
        className="w-full h-full object-contain"
        autoPlay
        playsInline
      />

      {/* TDLib Stream Download Notification Indicator */}
      <div className="absolute top-6 right-8 flex items-center gap-2 bg-zinc-900/80 backdrop-blur-md px-3.5 py-1.5 rounded-full border border-zinc-700/60 text-xs font-mono text-zinc-300">
        <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
        <span>TDLib.DownloadFile: Priority 32 (Direct Stream)</span>
      </div>

      {/* OSD TV Controls Overlay with Auto-fade */}
      <div
        className={`absolute inset-0 bg-gradient-to-t from-black/90 via-transparent to-black/70 flex flex-col justify-between p-8 transition-opacity duration-300 pointer-events-none ${
          showControls ? 'opacity-100' : 'opacity-0'
        }`}
      >
        {/* Top bar: Back and Title */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button
              id="player-btn-back"
              type="button"
              onClick={onBack}
              className="pointer-events-auto p-3 rounded-full bg-zinc-800/80 hover:bg-zinc-700 text-white border border-zinc-600 transition"
              title="Назад (Back)"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
            <div>
              <h2 className="text-lg font-bold text-white tracking-tight">{video.title}</h2>
              <div className="flex items-center gap-2 text-xs text-zinc-300">
                <span>{video.channelName}</span>
                <span>•</span>
                <span>{video.resolution}</span>
                <span>•</span>
                <span>{video.fileSize}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Center Play/Pause & Rewind Indicator */}
        <div className="flex items-center justify-center gap-8 pointer-events-auto">
          <button
            type="button"
            onClick={() => onSeekRelative(-10)}
            className="p-4 rounded-full bg-zinc-900/80 hover:bg-zinc-800 text-zinc-200 border border-zinc-700 transition active:scale-95"
            title="Перемотка назад на 10 сек (D-Pad Left)"
          >
            <Rewind className="w-6 h-6 text-sky-400" />
            <span className="text-[10px] font-mono block text-center">-10s</span>
          </button>

          <button
            type="button"
            onClick={onTogglePlay}
            className="p-6 rounded-full bg-sky-500 hover:bg-sky-400 text-white shadow-2xl ring-4 ring-sky-400/40 transition active:scale-95"
            title="Пауза / Воспроизведение (D-Pad Center / Space)"
          >
            {isPlaying ? (
              <Pause className="w-8 h-8 fill-white" />
            ) : (
              <Play className="w-8 h-8 fill-white ml-1" />
            )}
          </button>

          <button
            type="button"
            onClick={() => onSeekRelative(10)}
            className="p-4 rounded-full bg-zinc-900/80 hover:bg-zinc-800 text-zinc-200 border border-zinc-700 transition active:scale-95"
            title="Перемотка вперед на 10 сек (D-Pad Right)"
          >
            <FastForward className="w-6 h-6 text-sky-400" />
            <span className="text-[10px] font-mono block text-center">+10s</span>
          </button>
        </div>

        {/* Bottom Timeline & Progress Bar */}
        <div className="space-y-2 pointer-events-auto">
          {/* Seekbar Container */}
          <div className="relative w-full h-3 bg-zinc-800 rounded-full overflow-hidden cursor-pointer">
            <div
              className="h-full bg-gradient-to-r from-sky-500 to-sky-400 rounded-full transition-all duration-100"
              style={{ width: `${progressPercent}%` }}
            />
          </div>

          <div className="flex items-center justify-between text-xs font-mono text-zinc-300">
            <span>{formatTime(currentTime)}</span>
            <span className="text-zinc-400">D-Pad: ◀ -10s | ▶ +10s | ▲/▼ OSD | OK Play/Pause</span>
            <span>{video.duration}</span>
          </div>
        </div>
      </div>
    </div>
  );
};
