import React, { useState } from 'react';
import {
  Smartphone,
  ArrowRight,
  ShieldCheck,
  Settings,
  RefreshCw,
  Download,
  Plus,
  Trash2,
  Check,
  KeyRound,
  Lock,
  X,
  Server
} from 'lucide-react';

interface TvAuthScreenProps {
  onSimulateLogin: () => void;
  isFocused: boolean;
  focusedButton: 'login' | 'refresh';
}

interface ProxyItem {
  id: string;
  type: 'MTPROTO' | 'SOCKS5' | 'HTTP' | 'WEB';
  host: string;
  port: number;
  secret?: string;
  username?: string;
  password?: string;
  status: 'connected' | 'available' | 'unavailable' | 'checking';
  pingMs?: number;
}

export const TvAuthScreen: React.FC<TvAuthScreenProps> = ({
  onSimulateLogin,
  isFocused,
  focusedButton,
}) => {
  // Auth steps: 'phone' -> 'code' -> 'password'
  const [authStep, setAuthStep] = useState<'phone' | 'code' | 'password'>('phone');
  const [phoneNumber, setPhoneNumber] = useState('+7 912 051-76-38');
  const [authCode, setAuthCode] = useState('');
  const [cloudPassword, setCloudPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Proxy dialog state
  const [showProxyModal, setShowProxyModal] = useState(false);
  const [showAddProxyModal, setShowAddProxyModal] = useState(false);
  const [proxyMode, setProxyMode] = useState<'disabled' | 'system' | 'custom'>('custom');
  const [useIPv6, setUseIPv6] = useState(false);
  const [autoSwitch, setAutoSwitch] = useState(true);
  const [autoSwitchDelay, setAutoSwitchDelay] = useState(10);
  const [selectedProxyId, setSelectedProxyId] = useState<string>('p1');
  const [isScraping, setIsScraping] = useState(false);
  const [scrapeNotification, setScrapeNotification] = useState<string | null>(null);

  // Add Proxy Form state
  const [newProxyType, setNewProxyType] = useState<'MTPROTO' | 'SOCKS5' | 'HTTP' | 'WEB'>('MTPROTO');
  const [newHost, setNewHost] = useState('');
  const [newPort, setNewPort] = useState('443');
  const [newSecret, setNewSecret] = useState('');
  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [testResult, setTestResult] = useState<string | null>(null);
  const [isTestingProxy, setIsTestingProxy] = useState(false);

  const [proxies, setProxies] = useState<ProxyItem[]>([
    {
      id: 'p1',
      type: 'MTPROTO',
      host: 'one.nxrweb.shop',
      port: 443,
      secret: 'dd7287be476993a67190dbcbe946167b3c',
      status: 'connected',
      pingMs: 84
    },
    {
      id: 'p2',
      type: 'MTPROTO',
      host: 'proxy.digitalresistance.dog',
      port: 443,
      secret: 'ee11111111111111111111111111111111',
      status: 'available',
      pingMs: 122
    }
  ]);

  const handlePhoneSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setTimeout(() => {
      setIsSubmitting(false);
      setAuthStep('code');
    }, 600);
  };

  const handleCodeSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setTimeout(() => {
      setIsSubmitting(false);
      onSimulateLogin();
    }, 600);
  };

  const handleScrapeMtproto = () => {
    setIsScraping(true);
    setScrapeNotification(null);
    setTimeout(() => {
      setIsScraping(false);
      const newProxy: ProxyItem = {
        id: `mt-${Date.now()}`,
        type: 'MTPROTO',
        host: 'one.nxrweb.shop',
        port: 443,
        secret: 'dd7287be476993a67190dbcbe946167b3c',
        status: 'connected',
        pingMs: 76
      };
      setProxies(prev => {
        const exists = prev.some(p => p.host === newProxy.host && p.port === newProxy.port);
        return exists ? prev : [newProxy, ...prev];
      });
      setSelectedProxyId(newProxy.id);
      setProxyMode('custom');
      setScrapeNotification('Загружено и проверено рабочих прокси с mtproto.ru: 2');
      setTimeout(() => setScrapeNotification(null), 4000);
    }, 900);
  };

  const handleSaveCustomProxy = () => {
    if (!newHost.trim()) {
      setTestResult('Укажите хост сервера');
      return;
    }
    const item: ProxyItem = {
      id: `custom-${Date.now()}`,
      type: newProxyType,
      host: newHost.trim(),
      port: parseInt(newPort, 10) || 443,
      secret: newSecret.trim(),
      username: newUsername.trim(),
      password: newPassword.trim(),
      status: 'available',
      pingMs: 98
    };
    setProxies(prev => [item, ...prev]);
    setSelectedProxyId(item.id);
    setProxyMode('custom');
    setShowAddProxyModal(false);
    setNewHost('');
    setNewSecret('');
    setTestResult(null);
  };

  const activeProxy = proxies.find(p => p.id === selectedProxyId);

  return (
    <div
      id="tv-auth-screen"
      className="relative flex flex-col items-center justify-center min-h-[560px] h-full w-full bg-[#17212b] rounded-2xl overflow-hidden p-6 select-none border border-[#1e2c3a]"
    >
      {/* Top TV Status Bar */}
      <div className="absolute top-5 left-6 right-6 flex items-center justify-between z-10 bg-[#1e2c3a] px-4 py-2.5 rounded-xl border border-[#2b3a4c]/50">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-[#5288c1] flex items-center justify-center shadow-md">
            <svg className="w-4 h-4 text-white fill-current" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm4.64 6.8c-.15 1.58-.8 5.42-1.13 7.19-.14.75-.42 1-.68 1.03-.58.05-1.02-.38-1.58-.75-.88-.58-1.38-.94-2.23-1.5-.99-.65-.35-1.01.22-1.59.15-.15 2.71-2.48 2.76-2.69a.2.2 0 00-.05-.18c-.06-.05-.14-.03-.21-.02-.09.02-1.49.95-4.22 2.79-.4.27-.76.41-1.08.4-.36-.01-1.04-.2-1.55-.37-.63-.2-1.12-.31-1.08-.66.02-.18.27-.36.74-.55 2.92-1.27 4.86-2.11 5.83-2.51 2.78-1.16 3.35-1.36 3.73-1.36.08 0 .27.02.39.12.1.08.13.19.14.27-.01.06.01.24 0 .38z" />
            </svg>
          </div>
          <div>
            <div className="text-xs font-bold text-[#f5f5f5]">Cybermasters TG TV (CMTGTV)</div>
            <div className="flex items-center gap-1.5 text-[11px] text-emerald-400">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              <span>
                {proxyMode === 'custom' && activeProxy
                  ? `Подключено через MTProto (${activeProxy.host})`
                  : 'TDLib готов к авторизации'}
              </span>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => setShowProxyModal(true)}
          className="flex items-center gap-2 bg-[#242f3d] hover:bg-[#2b3a4c] border border-[#38485c] px-3 py-1.5 rounded-lg text-xs font-medium text-[#79b3e6] transition-colors"
        >
          <Settings className="w-3.5 h-3.5" />
          <span>
            {proxyMode === 'custom' && activeProxy
              ? `Прокси: ${activeProxy.type} (${activeProxy.host}:${activeProxy.port})`
              : proxyMode === 'system'
              ? 'Прокси: Системный'
              : 'Прокси: Отключен'}
          </span>
        </button>
      </div>

      {/* Main Two-Column Auth Container */}
      <div className="w-full max-w-3xl bg-[#242f3d] border border-[#1e2c3a] p-6 rounded-2xl shadow-2xl mt-12">
        {authStep === 'phone' && (
          <form onSubmit={handlePhoneSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6 items-center">
            {/* Left Column: Phone & Action Button */}
            <div className="space-y-4">
              <div>
                <h2 className="text-xl font-bold text-white">Вход по номеру</h2>
                <p className="text-xs text-[#7e8c9b]">
                  Введите номер вашего аккаунта Telegram
                </p>
              </div>

              {/* Presets */}
              <div className="flex items-center gap-1.5 flex-wrap">
                {['+7', '+375', '+380', '+998', '+1'].map(code => (
                  <button
                    type="button"
                    key={code}
                    onClick={() => setPhoneNumber(code)}
                    className={`px-2.5 py-1 rounded-md text-xs font-medium transition-colors ${
                      phoneNumber.startsWith(code) ? 'bg-[#5288c1] text-white font-bold' : 'bg-[#1e2c3a] text-[#7e8c9b]'
                    }`}
                  >
                    {code}
                  </button>
                ))}
              </div>

              <div className="space-y-1.5">
                <label className="text-[11px] text-[#7e8c9b]">Номер телефона (+...)</label>
                <input
                  type="text"
                  value={phoneNumber}
                  onChange={e => setPhoneNumber(e.target.value)}
                  className="w-full bg-[#17212b] border border-[#2b3a4c] focus:border-[#40a7e3] focus:outline-none rounded-xl px-3.5 py-2.5 text-lg font-bold text-left text-white"
                  placeholder="+7 999 123-45-67"
                />
              </div>

              {/* Visible Submit Button */}
              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full py-3 bg-[#5288c1] hover:bg-[#40a7e3] text-white font-bold rounded-xl text-sm transition-all shadow-lg flex items-center justify-center gap-2"
              >
                {isSubmitting ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    <span>Отправка запроса...</span>
                  </>
                ) : (
                  <>
                    <span>Получить код / Войти</span>
                    <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </div>

            {/* Right Column: TV Numeric Keypad */}
            <div className="bg-[#17212b]/60 p-4 rounded-xl border border-[#1e2c3a] flex flex-col items-center">
              <span className="text-[11px] text-[#7e8c9b] mb-2 font-medium">Экранный пульт</span>
              <div className="grid grid-cols-3 gap-2 w-full max-w-[220px]">
                {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫'].map(k => (
                  <button
                    type="button"
                    key={k}
                    onClick={() => {
                      if (k === '⌫') setPhoneNumber(prev => prev.slice(0, -1));
                      else if (k === 'C') setPhoneNumber('+');
                      else setPhoneNumber(prev => prev + k);
                    }}
                    className="bg-[#1e2c3a] hover:bg-[#40a7e3] hover:text-white text-sm font-bold py-2.5 rounded-lg text-[#f5f5f5] transition-colors shadow-sm"
                  >
                    {k}
                  </button>
                ))}
              </div>
            </div>
          </form>
        )}

        {authStep === 'code' && (
          <form onSubmit={handleCodeSubmit} className="grid grid-cols-1 md:grid-cols-2 gap-6 items-center">
            {/* Left Column: Code Inputs & Submit */}
            <div className="space-y-4">
              <div>
                <h2 className="text-xl font-bold text-white">Введите код</h2>
                <p className="text-xs text-[#7e8c9b]">
                  Код отправлен на <strong className="text-white">{phoneNumber}</strong>
                </p>
              </div>

              {/* Code Digits Visual */}
              <div className="flex gap-2">
                {[0, 1, 2, 3, 4].map(idx => (
                  <div
                    key={idx}
                    className={`w-10 h-12 rounded-lg flex items-center justify-center text-xl font-bold bg-[#17212b] border ${
                      authCode.length === idx ? 'border-[#40a7e3]' : 'border-[#2b3a4c]'
                    }`}
                  >
                    {authCode[idx] || ''}
                  </div>
                ))}
              </div>

              <button
                type="submit"
                disabled={authCode.length < 5 || isSubmitting}
                className="w-full py-3 bg-[#5288c1] hover:bg-[#40a7e3] text-white font-bold rounded-xl text-sm transition-all shadow-lg flex items-center justify-center gap-2"
              >
                {isSubmitting ? (
                  <>
                    <RefreshCw className="w-4 h-4 animate-spin" />
                    <span>Вход в аккаунт...</span>
                  </>
                ) : (
                  <>
                    <Check className="w-4 h-4" />
                    <span>Войти в Telegram</span>
                  </>
                )}
              </button>

              <div className="flex items-center justify-between text-xs text-[#40a7e3] pt-1">
                <button type="button" onClick={() => setAuthStep('phone')}>
                  Изменить номер
                </button>
                <button type="button" onClick={() => alert('Код отправлен повторно')}>
                  Повторить отправку
                </button>
              </div>
            </div>

            {/* Right Column: Keypad for Code */}
            <div className="bg-[#17212b]/60 p-4 rounded-xl border border-[#1e2c3a] flex flex-col items-center">
              <span className="text-[11px] text-[#7e8c9b] mb-2 font-medium">Экранный пульт</span>
              <div className="grid grid-cols-3 gap-2 w-full max-w-[220px]">
                {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫'].map(k => (
                  <button
                    type="button"
                    key={k}
                    onClick={() => {
                      if (k === '⌫') setAuthCode(prev => prev.slice(0, -1));
                      else if (k === 'C') setAuthCode('');
                      else if (authCode.length < 5) setAuthCode(prev => prev + k);
                    }}
                    className="bg-[#1e2c3a] hover:bg-[#40a7e3] hover:text-white text-sm font-bold py-2.5 rounded-lg text-[#f5f5f5] transition-colors"
                  >
                    {k}
                  </button>
                ))}
              </div>
            </div>
          </form>
        )}
      </div>

      {/* Official Telegram Proxy Settings Modal */}
      {showProxyModal && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[#17212b] border border-[#242f3d] w-full max-w-xl rounded-2xl p-5 shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
            {/* Modal Header */}
            <div className="flex items-center justify-between pb-3 border-b border-[#242f3d]">
              <h3 className="text-base font-bold text-white">Настройки прокси</h3>
              <button
                type="button"
                onClick={() => setShowProxyModal(false)}
                className="text-[#7e8c9b] hover:text-white p-1"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {scrapeNotification && (
              <div className="mt-2 p-2.5 bg-emerald-500/20 text-emerald-300 rounded-lg text-xs font-medium">
                {scrapeNotification}
              </div>
            )}

            {/* Modal Body */}
            <div className="flex-1 overflow-y-auto py-3 space-y-3 pr-1">
              {/* IPv6 Option */}
              <label className="flex items-center gap-2.5 text-xs text-[#f5f5f5] cursor-pointer">
                <input
                  type="checkbox"
                  checked={useIPv6}
                  onChange={e => setUseIPv6(e.target.checked)}
                  className="rounded border-[#2b3a4c] text-[#5288c1] focus:ring-0"
                />
                <span>Через IPv6 (если возможно)</span>
              </label>

              {/* Modes */}
              <div className="space-y-1.5 pt-1">
                {[
                  { mode: 'disabled', label: 'Отключить прокси' },
                  { mode: 'system', label: 'Использовать системные настройки прокси' },
                  { mode: 'custom', label: 'Использовать собственный прокси' },
                ].map(item => (
                  <label key={item.mode} className="flex items-center gap-2.5 text-xs text-[#f5f5f5] cursor-pointer">
                    <input
                      type="radio"
                      name="proxyMode"
                      checked={proxyMode === item.mode}
                      onChange={() => setProxyMode(item.mode as any)}
                      className="text-[#40a7e3] focus:ring-0"
                    />
                    <span>{item.label}</span>
                  </label>
                ))}
              </div>

              {/* Auto-switch block */}
              {proxyMode === 'custom' && (
                <div className="bg-[#1e2c3a] p-3 rounded-xl space-y-2">
                  <label className="flex items-center gap-2 text-xs font-medium text-white cursor-pointer">
                    <input
                      type="checkbox"
                      checked={autoSwitch}
                      onChange={e => setAutoSwitch(e.target.checked)}
                      className="rounded text-[#40a7e3]"
                    />
                    <span>Автопереключение прокси</span>
                  </label>

                  <div className="flex items-center justify-between gap-1.5">
                    {[5, 10, 15, 30, 60].map(s => (
                      <button
                        type="button"
                        key={s}
                        onClick={() => setAutoSwitchDelay(s)}
                        className={`flex-1 py-1 text-xs rounded-md font-medium transition-colors ${
                          autoSwitchDelay === s ? 'bg-[#40a7e3] text-white' : 'bg-[#242f3d] text-[#7e8c9b]'
                        }`}
                      >
                        {s} с
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Proxy List */}
              <div className="space-y-2 pt-1">
                {proxies.map(p => {
                  const isSelected = selectedProxyId === p.id && proxyMode === 'custom';
                  return (
                    <div
                      key={p.id}
                      onClick={() => {
                        setSelectedProxyId(p.id);
                        setProxyMode('custom');
                      }}
                      className={`p-2.5 rounded-xl border flex items-center justify-between cursor-pointer transition-colors ${
                        isSelected ? 'bg-[#242f3d] border-[#40a7e3]' : 'bg-[#1e2c3a] border-transparent hover:bg-[#242f3d]'
                      }`}
                    >
                      <div className="flex items-center gap-2.5">
                        <div
                          className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center ${
                            isSelected ? 'border-[#40a7e3]' : 'border-[#7e8c9b]'
                          }`}
                        >
                          {isSelected && <div className="w-1.5 h-1.5 rounded-full bg-[#40a7e3]" />}
                        </div>
                        <div>
                          <div className="text-xs font-bold text-white flex items-center gap-2">
                            <span>{p.type}</span>
                            <span className="text-[11px] font-normal text-[#7e8c9b]">
                              {p.host}:{p.port}
                            </span>
                          </div>
                          <div className="text-[11px] text-emerald-400">
                            {p.status === 'connected' ? `подключён (${p.pingMs} мс)` : `доступен (${p.pingMs} мс)`}
                          </div>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={e => {
                          e.stopPropagation();
                          setProxies(prev => prev.filter(item => item.id !== p.id));
                        }}
                        className="text-[#7e8c9b] hover:text-red-400 p-1"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Modal Footer */}
            <div className="pt-3 border-t border-[#242f3d] flex items-center justify-between">
              <button
                type="button"
                onClick={() => setShowProxyModal(false)}
                className="text-xs text-[#40a7e3] font-medium"
              >
                Закрыть
              </button>

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={handleScrapeMtproto}
                  disabled={isScraping}
                  className="bg-[#1e2c3a] hover:bg-[#242f3d] text-[#40a7e3] text-xs font-semibold px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors"
                >
                  {isScraping ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />}
                  <span>Загрузить с mtproto.ru</span>
                </button>

                <button
                  type="button"
                  onClick={() => setShowAddProxyModal(true)}
                  className="bg-[#5288c1] hover:bg-[#40a7e3] text-white text-xs font-semibold px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Добавить прокси</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Add Custom Proxy Modal (Compact with fixed bottom buttons) */}
      {showAddProxyModal && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[#17212b] border border-[#242f3d] w-full max-w-md rounded-2xl p-5 shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between pb-3 border-b border-[#242f3d]">
              <h3 className="text-base font-bold text-white">Добавление прокси</h3>
              <button
                type="button"
                onClick={() => setShowAddProxyModal(false)}
                className="text-[#7e8c9b] hover:text-white p-1"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Scrollable Form Body */}
            <div className="flex-1 overflow-y-auto py-3 space-y-3 pr-1 text-xs">
              {/* Type Chips */}
              <div className="flex gap-2">
                {(['MTPROTO', 'SOCKS5', 'HTTP', 'WEB'] as const).map(t => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setNewProxyType(t)}
                    className={`flex-1 py-1.5 rounded-lg font-semibold transition-colors ${
                      newProxyType === t
                        ? 'bg-[#40a7e3] text-white'
                        : 'bg-[#1e2c3a] text-[#7e8c9b] hover:text-white'
                    }`}
                  >
                    {t}
                  </button>
                ))}
              </div>

              {/* Socket Address */}
              <div className="space-y-1.5">
                <span className="font-bold text-white">Адрес сокета</span>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={newHost}
                    onChange={e => setNewHost(e.target.value)}
                    placeholder="Хост / IP"
                    className="flex-[3] bg-[#1e2c3a] border border-[#2b3a4c] rounded-lg px-3 py-2 text-white focus:outline-none focus:border-[#40a7e3]"
                  />
                  <input
                    type="text"
                    value={newPort}
                    onChange={e => setNewPort(e.target.value)}
                    placeholder="Порт"
                    className="flex-1 bg-[#1e2c3a] border border-[#2b3a4c] rounded-lg px-3 py-2 text-white focus:outline-none focus:border-[#40a7e3]"
                  />
                </div>
              </div>

              {/* Credentials */}
              <div className="space-y-1.5">
                <span className="font-bold text-white">
                  {newProxyType === 'MTPROTO' || newProxyType === 'WEB' ? 'Ключ (Secret)' : 'Учётные данные'}
                </span>
                {newProxyType === 'MTPROTO' || newProxyType === 'WEB' ? (
                  <input
                    type="text"
                    value={newSecret}
                    onChange={e => setNewSecret(e.target.value)}
                    placeholder="Ключ (Secret)"
                    className="w-full bg-[#1e2c3a] border border-[#2b3a4c] rounded-lg px-3 py-2 text-white focus:outline-none focus:border-[#40a7e3]"
                  />
                ) : (
                  <div className="space-y-2">
                    <input
                      type="text"
                      value={newUsername}
                      onChange={e => setNewUsername(e.target.value)}
                      placeholder="Логин"
                      className="w-full bg-[#1e2c3a] border border-[#2b3a4c] rounded-lg px-3 py-2 text-white focus:outline-none focus:border-[#40a7e3]"
                    />
                    <input
                      type="password"
                      value={newPassword}
                      onChange={e => setNewPassword(e.target.value)}
                      placeholder="Пароль"
                      className="w-full bg-[#1e2c3a] border border-[#2b3a4c] rounded-lg px-3 py-2 text-white focus:outline-none focus:border-[#40a7e3]"
                    />
                  </div>
                )}
              </div>

              {testResult && (
                <div className={`p-2 rounded-lg text-xs ${testResult.includes('Доступен') ? 'bg-emerald-500/20 text-emerald-300' : 'bg-red-500/20 text-red-300'}`}>
                  {testResult}
                </div>
              )}
            </div>

            {/* Fixed Bottom Action Row: Always Visible! */}
            <div className="pt-3 border-t border-[#242f3d] flex items-center justify-between">
              <button
                type="button"
                onClick={() => {
                  if (!newHost.trim()) {
                    setTestResult('Введите хост для проверки');
                    return;
                  }
                  setIsTestingProxy(true);
                  setTestResult('Проверка соединения...');
                  setTimeout(() => {
                    setIsTestingProxy(false);
                    setTestResult('Доступен! Пинг: 88 мс');
                  }, 600);
                }}
                disabled={isTestingProxy}
                className="text-xs text-[#40a7e3] font-medium hover:underline"
              >
                {isTestingProxy ? 'Проверка...' : 'Проверить'}
              </button>

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddProxyModal(false)}
                  className="px-3 py-1.5 text-xs text-[#7e8c9b] hover:text-white"
                >
                  Отмена
                </button>
                <button
                  type="button"
                  onClick={handleSaveCustomProxy}
                  className="px-4 py-1.5 bg-[#5288c1] hover:bg-[#40a7e3] text-white text-xs font-bold rounded-lg shadow-md"
                >
                  Сохранить
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
