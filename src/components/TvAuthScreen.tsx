import React from 'react';
import { QrCode, Smartphone, ArrowRight, ShieldCheck, RefreshCw } from 'lucide-react';

interface TvAuthScreenProps {
  onSimulateLogin: () => void;
  isFocused: boolean;
  focusedButton: 'login' | 'refresh';
}

export const TvAuthScreen: React.FC<TvAuthScreenProps> = ({
  onSimulateLogin,
  isFocused,
  focusedButton,
}) => {
  // Mock generated QR token representation
  const qrLink = "tg://login?token=AQAAAI5z4L_tv_media_client_token_77019x";

  return (
    <div
      id="tv-auth-screen"
      className="relative w-full h-full flex flex-col items-center justify-center p-8 bg-gradient-to-br from-zinc-950 via-zinc-900 to-black text-white select-none overflow-hidden"
    >
      {/* Subtle TV Ambient Glow */}
      <div className="absolute top-1/4 -left-20 w-80 h-80 bg-sky-600/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute bottom-1/4 -right-20 w-96 h-96 bg-blue-600/10 rounded-full blur-3xl pointer-events-none" />

      {/* Header with App Identity */}
      <div className="flex items-center gap-3 mb-6">
        <div className="w-12 h-12 rounded-2xl bg-sky-500 flex items-center justify-center shadow-lg shadow-sky-500/20">
          <svg className="w-7 h-7 text-white fill-current" viewBox="0 0 24 24">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .38z" />
          </svg>
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-zinc-100">Telegram Media TV</h1>
          <p className="text-xs text-zinc-400">Авторизация через TDLib C++ Core</p>
        </div>
      </div>

      {/* Main 10-foot UI Auth Container */}
      <div className="flex flex-col md:flex-row items-center gap-10 bg-zinc-900/80 border border-zinc-800 p-8 rounded-3xl backdrop-blur-md max-w-4xl shadow-2xl">
        {/* QR Code Canvas with high-contrast TV frame */}
        <div className="flex flex-col items-center">
          <div className="relative p-4 bg-white rounded-2xl shadow-xl border-4 border-white">
            {/* Custom SVG QR Code for high TV clarity */}
            <svg className="w-52 h-52 text-zinc-900" viewBox="0 0 100 100" fill="currentColor">
              {/* Corner 1 */}
              <rect x="5" y="5" width="26" height="26" fill="black" rx="3" />
              <rect x="9" y="9" width="18" height="18" fill="white" rx="2" />
              <rect x="13" y="13" width="10" height="10" fill="black" rx="1" />

              {/* Corner 2 */}
              <rect x="69" y="5" width="26" height="26" fill="black" rx="3" />
              <rect x="73" y="9" width="18" height="18" fill="white" rx="2" />
              <rect x="77" y="13" width="10" height="10" fill="black" rx="1" />

              {/* Corner 3 */}
              <rect x="5" y="69" width="26" height="26" fill="black" rx="3" />
              <rect x="9" y="73" width="18" height="18" fill="white" rx="2" />
              <rect x="13" y="77" width="10" height="10" fill="black" rx="1" />

              {/* Data Blocks */}
              <rect x="36" y="8" width="6" height="6" fill="black" />
              <rect x="48" y="8" width="6" height="6" fill="black" />
              <rect x="36" y="20" width="6" height="6" fill="black" />
              <rect x="54" y="20" width="6" height="6" fill="black" />

              <rect x="8" y="38" width="6" height="6" fill="black" />
              <rect x="20" y="38" width="6" height="6" fill="black" />
              <rect x="38" y="38" width="8" height="8" fill="black" />
              <rect x="52" y="38" width="6" height="6" fill="black" />
              <rect x="64" y="38" width="6" height="6" fill="black" />
              <rect x="78" y="38" width="6" height="6" fill="black" />

              <rect x="38" y="50" width="6" height="6" fill="black" />
              <rect x="50" y="50" width="6" height="6" fill="black" />
              <rect x="68" y="50" width="6" height="6" fill="black" />
              <rect x="82" y="50" width="6" height="6" fill="black" />

              <rect x="38" y="66" width="6" height="6" fill="black" />
              <rect x="52" y="66" width="6" height="6" fill="black" />
              <rect x="68" y="66" width="6" height="6" fill="black" />
              <rect x="80" y="76" width="12" height="12" fill="black" />
              <rect x="46" y="80" width="8" height="8" fill="black" />
            </svg>

            {/* Telegram Icon in Center of QR */}
            <div className="absolute inset-0 m-auto w-10 h-10 bg-white rounded-full flex items-center justify-center shadow-md">
              <div className="w-8 h-8 rounded-full bg-sky-500 flex items-center justify-center">
                <Smartphone className="w-4 h-4 text-white" />
              </div>
            </div>
          </div>

          <div className="mt-3 text-[11px] font-mono text-zinc-400 flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span>TDLib: WaitOtherDeviceConfirmation</span>
          </div>
        </div>

        {/* Step-by-Step Instructions (10-foot typography) */}
        <div className="flex-1 space-y-4">
          <div className="space-y-1">
            <h2 className="text-xl font-bold text-white tracking-tight">Вход через QR-код</h2>
            <p className="text-xs text-zinc-300">
              Быстрая и безопасная авторизация без ввода символов с пульта
            </p>
          </div>

          <div className="space-y-2.5 text-xs text-zinc-300">
            <div className="flex items-start gap-3 bg-zinc-800/60 p-2.5 rounded-xl border border-zinc-700/50">
              <span className="flex-shrink-0 w-6 h-6 rounded-full bg-sky-500/20 text-sky-400 font-bold flex items-center justify-center text-xs">
                1
              </span>
              <p className="leading-snug">
                Откройте <strong className="text-white">Telegram</strong> на смартфоне
              </p>
            </div>

            <div className="flex items-start gap-3 bg-zinc-800/60 p-2.5 rounded-xl border border-zinc-700/50">
              <span className="flex-shrink-0 w-6 h-6 rounded-full bg-sky-500/20 text-sky-400 font-bold flex items-center justify-center text-xs">
                2
              </span>
              <p className="leading-snug">
                Перейдите в <strong className="text-white">Настройки → Устройства</strong>
              </p>
            </div>

            <div className="flex items-start gap-3 bg-zinc-800/60 p-2.5 rounded-xl border border-zinc-700/50">
              <span className="flex-shrink-0 w-6 h-6 rounded-full bg-sky-500/20 text-sky-400 font-bold flex items-center justify-center text-xs">
                3
              </span>
              <p className="leading-snug">
                Нажмите <strong className="text-white">«Подключить устройство»</strong> и наведите камеру
              </p>
            </div>
          </div>

          {/* D-Pad Focusable Action Buttons */}
          <div className="pt-2 flex flex-col sm:flex-row gap-3">
            <button
              id="auth-btn-simulate-ready"
              type="button"
              onClick={onSimulateLogin}
              className={`flex-1 flex items-center justify-center gap-2 py-3 px-4 rounded-xl text-xs font-semibold transition-all ${
                focusedButton === 'login'
                  ? 'bg-sky-500 text-white ring-4 ring-sky-400/50 scale-[1.03] shadow-lg shadow-sky-500/30'
                  : 'bg-zinc-800 text-zinc-200 hover:bg-zinc-700'
              }`}
            >
              <ShieldCheck className="w-4 h-4" />
              <span>Войти (StateReady)</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <p className="text-[10px] text-zinc-400 font-mono break-all">
            Token: {qrLink}
          </p>
        </div>
      </div>
    </div>
  );
};
