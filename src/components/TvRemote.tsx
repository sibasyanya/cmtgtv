import React from 'react';
import {
  ArrowUp,
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Circle,
  RotateCcw,
  Play,
  Pause,
  Tv,
  Power
} from 'lucide-react';

interface TvRemoteProps {
  onDpad: (direction: 'up' | 'down' | 'left' | 'right') => void;
  onSelect: () => void;
  onBack: () => void;
  onPlayPause: () => void;
  isPlaying?: boolean;
}

export const TvRemote: React.FC<TvRemoteProps> = ({
  onDpad,
  onSelect,
  onBack,
  onPlayPause,
  isPlaying = false,
}) => {
  return (
    <div
      id="tv-remote-container"
      className="flex flex-col items-center bg-zinc-900/90 text-zinc-100 p-5 rounded-3xl border border-zinc-800 shadow-2xl w-full max-w-[280px]"
    >
      <div className="flex items-center justify-between w-full mb-4 px-1">
        <div className="flex items-center gap-2">
          <Tv className="w-5 h-5 text-sky-400" />
          <span className="text-xs font-semibold tracking-wider text-zinc-400 uppercase">
            Android TV Remote
          </span>
        </div>
        <div className="w-3 h-3 rounded-full bg-emerald-500/80 shadow-[0_0_8px_rgba(16,185,129,0.8)]" />
      </div>

      {/* D-Pad Controller Circle */}
      <div className="relative w-48 h-48 my-3 flex items-center justify-center bg-zinc-950 rounded-full border border-zinc-800 shadow-inner">
        {/* D-Pad UP */}
        <button
          id="remote-btn-up"
          type="button"
          onClick={() => onDpad('up')}
          className="absolute top-2 w-14 h-12 flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800/80 active:scale-95 rounded-t-2xl transition"
          title="D-Pad Вверх (Up Arrow)"
        >
          <ArrowUp className="w-6 h-6" />
        </button>

        {/* D-Pad DOWN */}
        <button
          id="remote-btn-down"
          type="button"
          onClick={() => onDpad('down')}
          className="absolute bottom-2 w-14 h-12 flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800/80 active:scale-95 rounded-b-2xl transition"
          title="D-Pad Вниз (Down Arrow)"
        >
          <ArrowDown className="w-6 h-6" />
        </button>

        {/* D-Pad LEFT */}
        <button
          id="remote-btn-left"
          type="button"
          onClick={() => onDpad('left')}
          className="absolute left-2 w-12 h-14 flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800/80 active:scale-95 rounded-l-2xl transition"
          title="D-Pad Влево (Left Arrow)"
        >
          <ArrowLeft className="w-6 h-6" />
        </button>

        {/* D-Pad RIGHT */}
        <button
          id="remote-btn-right"
          type="button"
          onClick={() => onDpad('right')}
          className="absolute right-2 w-12 h-14 flex items-center justify-center text-zinc-300 hover:text-white hover:bg-zinc-800/80 active:scale-95 rounded-r-2xl transition"
          title="D-Pad Вправо (Right Arrow)"
        >
          <ArrowRight className="w-6 h-6" />
        </button>

        {/* D-Pad CENTER / OK BUTTON */}
        <button
          id="remote-btn-center"
          type="button"
          onClick={onSelect}
          className="w-18 h-18 rounded-full bg-gradient-to-b from-sky-500 to-sky-700 text-white font-bold text-sm flex items-center justify-center shadow-lg hover:from-sky-400 hover:to-sky-600 active:scale-90 transition"
          title="D-Pad Center / OK (Enter)"
        >
          OK
        </button>
      </div>

      {/* Auxiliary Buttons: Back, Play/Pause */}
      <div className="grid grid-cols-2 gap-3 w-full mt-4">
        <button
          id="remote-btn-back"
          type="button"
          onClick={onBack}
          className="flex items-center justify-center gap-2 py-3 px-4 bg-zinc-800/90 hover:bg-zinc-700 active:scale-95 text-zinc-200 rounded-xl font-medium text-xs border border-zinc-700/60 transition"
          title="Назад (Backspace / Esc)"
        >
          <RotateCcw className="w-4 h-4 text-amber-400" />
          <span>BACK</span>
        </button>

        <button
          id="remote-btn-play-pause"
          type="button"
          onClick={onPlayPause}
          className="flex items-center justify-center gap-2 py-3 px-4 bg-zinc-800/90 hover:bg-zinc-700 active:scale-95 text-zinc-200 rounded-xl font-medium text-xs border border-zinc-700/60 transition"
          title="Старт/Пауза (Space)"
        >
          {isPlaying ? (
            <>
              <Pause className="w-4 h-4 text-emerald-400" />
              <span>PAUSE</span>
            </>
          ) : (
            <>
              <Play className="w-4 h-4 text-emerald-400" />
              <span>PLAY</span>
            </>
          )}
        </button>
      </div>

      {/* Keyboard navigation tip */}
      <div className="mt-4 pt-3 border-t border-zinc-800/80 w-full text-center">
        <p className="text-[11px] text-zinc-400 font-mono leading-relaxed">
          Клавиатура: <span className="text-sky-300">Стрелки</span> (D-Pad), <span className="text-sky-300">Enter</span> (OK), <span className="text-sky-300">Esc/Backspace</span> (Назад)
        </p>
      </div>
    </div>
  );
};
