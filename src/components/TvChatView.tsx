import React, { useState } from 'react';
import {
  ArrowLeft,
  Send,
  CheckCheck,
  Sparkles,
  ExternalLink,
  Info,
  X,
  Globe
} from 'lucide-react';
import { ChatItem, ChatMessageItem } from '../types';

interface TvChatViewProps {
  chat: ChatItem;
  messages: ChatMessageItem[];
  onBack: () => void;
  onSendMessage: (text: string) => void;
  onNavigateToLink?: (link: string) => void;
}

interface ParsedLink {
  label: string;
  url: string;
  isInternal: boolean;
}

function extractMessageLinks(text: string): ParsedLink[] {
  const results: ParsedLink[] = [];
  const regex = /(https?:\/\/[^\s]+|t\.me\/[a-zA-Z0-9_+/]+|@[a-zA-Z0-9_]{3,})/gi;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(text)) !== null) {
    const raw = match[0].replace(/[.,!?;:)]+$/, '');
    const isInternal =
      raw.startsWith('@') ||
      raw.toLowerCase().includes('t.me/') ||
      raw.toLowerCase().includes('telegram.me/');
    const url = raw.startsWith('@')
      ? `https://t.me/${raw.slice(1)}`
      : raw.startsWith('http')
      ? raw
      : `https://${raw}`;
    if (!results.some((r) => r.url === url)) {
      results.push({ label: raw, url, isInternal });
    }
  }
  return results;
}

export const TvChatView: React.FC<TvChatViewProps> = ({
  chat,
  messages,
  onBack,
  onSendMessage,
  onNavigateToLink,
}) => {
  const [inputText, setInputText] = useState('');
  const [focusedAction] = useState<'input' | 'send' | 'back' | 'quick'>('input');
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [externalPreviewUrl, setExternalPreviewUrl] = useState<string | null>(null);

  const quickPhrases = [
    'Да, отлично!',
    'Смотрю на ТВ',
    'Позже отвечу',
    'Спасибо за инфу',
    'Принято'
  ];

  const handleSend = () => {
    if (inputText.trim()) {
      onSendMessage(inputText.trim());
      setInputText('');
    }
  };

  const handleLinkAction = (link: ParsedLink) => {
    if (link.isInternal) {
      onNavigateToLink?.(link.label);
    } else {
      setExternalPreviewUrl(link.url);
    }
  };

  return (
    <div
      id="tv-chat-view"
      className="relative w-full h-full flex flex-col bg-zinc-950 text-white overflow-hidden select-none"
    >
      {/* Top Chat Header */}
      <div className="flex items-center justify-between px-6 py-3.5 bg-zinc-900/90 border-b border-zinc-800/80 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <button
            id="chat-btn-back"
            type="button"
            onClick={onBack}
            className={`p-2.5 rounded-full transition cursor-pointer ${
              focusedAction === 'back'
                ? 'bg-sky-500 text-white ring-4 ring-sky-400/50'
                : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
            }`}
            title="Назад к списку чатов (Esc / Back)"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>

          <div
            onClick={() => setShowProfileModal(true)}
            className="flex items-center gap-3 px-3 py-1.5 rounded-xl bg-zinc-800/70 hover:bg-sky-500/20 border border-zinc-700/80 hover:border-sky-400 cursor-pointer transition"
          >
            <img
              src={chat.avatarUrl}
              alt={chat.title}
              className="w-9 h-9 rounded-full object-cover border border-zinc-700 shadow-md"
            />
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-sm font-bold text-white tracking-tight">{chat.title}</h2>
                {chat.verified && (
                  <span className="w-4 h-4 rounded-full bg-sky-500 flex items-center justify-center text-[10px] font-bold text-white">
                    ✓
                  </span>
                )}
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-sky-500/20 text-sky-300 font-semibold">
                  Профиль (ОК)
                </span>
              </div>
              <p className="text-[10px] text-zinc-400">
                Нажмите ОК для просмотра описания и ссылок профиля
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setShowProfileModal(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-xs text-sky-400 border border-zinc-700 cursor-pointer"
          >
            <Info className="w-3.5 h-3.5" />
            <span>Ссылки профиля</span>
          </button>
        </div>
      </div>

      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {messages.map((msg) => {
          const links = extractMessageLinks(msg.text);
          return (
            <div
              key={msg.id}
              className={`flex flex-col ${msg.isOutgoing ? 'items-end' : 'items-start'}`}
            >
              <div
                className={`max-w-2xl px-4 py-3 rounded-2xl text-xs leading-relaxed shadow-md ${
                  msg.isOutgoing
                    ? 'bg-sky-600 text-white rounded-br-none'
                    : 'bg-zinc-900 border border-zinc-800/90 text-zinc-100 rounded-bl-none'
                }`}
              >
                {!msg.isOutgoing && (
                  <span className="text-[10px] font-bold text-sky-400 block mb-1">
                    {msg.senderName}
                  </span>
                )}
                <p className="whitespace-pre-wrap">{msg.text}</p>

                {links.length > 0 && (
                  <div className="mt-2.5 pt-2 border-t border-white/10 flex flex-col gap-1.5">
                    {links.map((lnk) => (
                      <button
                        key={lnk.url}
                        type="button"
                        onClick={() => handleLinkAction(lnk)}
                        className="flex items-center justify-between px-3 py-1.5 rounded-lg bg-zinc-950/80 hover:bg-sky-500 text-sky-300 hover:text-black border border-sky-500/40 text-[11px] font-semibold transition cursor-pointer"
                      >
                        <span className="truncate flex items-center gap-1.5">
                          <span>{lnk.isInternal ? '✈️' : '🌐'}</span>
                          <span>{lnk.label}</span>
                        </span>
                        <span className="ml-3 text-[10px] font-bold flex items-center gap-1">
                          {lnk.isInternal ? 'Открыть в TG (ОК)' : 'Открыть сайт (ОК)'}
                          <ExternalLink className="w-3 h-3" />
                        </span>
                      </button>
                    ))}
                  </div>
                )}

                <div
                  className={`mt-1.5 flex items-center justify-end gap-1 text-[10px] ${
                    msg.isOutgoing ? 'text-sky-200' : 'text-zinc-400'
                  }`}
                >
                  <span>{msg.time}</span>
                  {msg.isOutgoing && <CheckCheck className="w-3 h-3 text-sky-200" />}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Profile Modal with Clickable Internal & External Links */}
      {showProfileModal && (
        <div className="absolute inset-0 z-40 bg-black/80 backdrop-blur-sm flex items-center justify-center p-6">
          <div className="w-full max-w-xl bg-zinc-900 border-2 border-sky-400 rounded-2xl p-5 flex flex-col gap-3 shadow-2xl">
            <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
              <div className="flex items-center gap-3">
                <img
                  src={chat.avatarUrl}
                  alt={chat.title}
                  className="w-12 h-12 rounded-full object-cover border-2 border-sky-400"
                />
                <div>
                  <h3 className="text-sm font-bold text-white">{chat.title}</h3>
                  <span className="text-[11px] text-sky-400">
                    {chat.type === 'channel' ? 'Публичный канал Telegram' : 'Диалог Telegram'}
                  </span>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setShowProfileModal(false)}
                className="px-3 py-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-xs text-white font-semibold"
              >
                Закрыть (ОК)
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <div className="p-3 rounded-xl bg-zinc-950 border border-zinc-800">
                <div className="text-[11px] text-sky-400 font-bold mb-1">Описание и ссылки профиля:</div>
                <p className="text-zinc-300 text-xs">
                  Официальный ресурс {chat.title}. Нажмите ОК на любой ссылке ниже для перехода внутри Telegram или открытия веб-страницы.
                </p>
              </div>

              <button
                type="button"
                onClick={() => {
                  setShowProfileModal(false);
                  onNavigateToLink?.('@cybermasters_tv');
                }}
                className="w-full flex items-center justify-between p-3 rounded-xl bg-sky-950/50 hover:bg-sky-500 text-white hover:text-black border border-sky-500/40 font-semibold transition cursor-pointer"
              >
                <span>✈️ Юзернейм: @cybermasters_tv (https://t.me/cybermasters_tv)</span>
                <span className="text-[11px] font-bold">Перейти (ОК)</span>
              </button>

              <button
                type="button"
                onClick={() => {
                  setShowProfileModal(false);
                  setExternalPreviewUrl('https://core.telegram.org/tdlib');
                }}
                className="w-full flex items-center justify-between p-3 rounded-xl bg-zinc-950 hover:bg-sky-500 text-white hover:text-black border border-zinc-700 font-semibold transition cursor-pointer"
              >
                <span>🌐 Внешний сайт: https://core.telegram.org/tdlib</span>
                <span className="text-[11px] font-bold">Открыть в ТВ-браузере (ОК)</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Built-in TV Browser Modal for External Links */}
      {externalPreviewUrl && (
        <div className="absolute inset-0 z-50 bg-black/90 flex items-center justify-center p-4">
          <div className="w-full h-full bg-zinc-900 border-2 border-sky-400 rounded-2xl flex flex-col overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2.5 bg-zinc-950 border-b border-zinc-800">
              <div className="flex items-center gap-2 min-w-0">
                <Globe className="w-4 h-4 text-sky-400 flex-shrink-0" />
                <span className="text-xs font-mono text-sky-300 truncate">{externalPreviewUrl}</span>
              </div>
              <button
                type="button"
                onClick={() => setExternalPreviewUrl(null)}
                className="flex items-center gap-1 px-3 py-1 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold cursor-pointer"
              >
                <X className="w-3.5 h-3.5" />
                <span>Закрыть браузер (ОК)</span>
              </button>
            </div>
            <div className="flex-1 bg-zinc-950 flex flex-col items-center justify-center p-6 text-center">
              <Globe className="w-10 h-10 text-sky-400 mb-3" />
              <h4 className="text-sm font-bold text-white mb-1">
                Встроенный ТВ-браузер (WebView + скролл с пульта ДУ)
              </h4>
              <p className="text-xs text-zinc-400 max-w-md mb-4">
                Открыта внешняя ссылка: <span className="text-sky-300 font-mono">{externalPreviewUrl}</span>
              </p>
              <a
                href={externalPreviewUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="px-4 py-2 rounded-xl bg-sky-500 hover:bg-sky-400 text-black font-bold text-xs transition"
              >
                Открыть страницу в новой вкладке браузера
              </a>
            </div>
          </div>
        </div>
      )}

      {/* Quick Replies for TV Remote Control */}
      <div className="px-6 py-2 bg-zinc-900/40 border-t border-zinc-800/60 flex items-center gap-2 overflow-x-hidden">
        <span className="text-[11px] text-zinc-400 font-mono whitespace-nowrap flex items-center gap-1">
          <Sparkles className="w-3.5 h-3.5 text-amber-400" />
          Быстрый ответ:
        </span>
        <div className="flex items-center gap-2 overflow-x-auto">
          {quickPhrases.map((phrase, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => onSendMessage(phrase)}
              className="px-3 py-1 rounded-full bg-zinc-800/80 hover:bg-sky-600 active:scale-95 text-zinc-300 hover:text-white text-xs border border-zinc-700/60 transition whitespace-nowrap"
            >
              {phrase}
            </button>
          ))}
        </div>
      </div>

      {/* TV Bottom Message Input Bar */}
      <div className="p-4 bg-zinc-900/90 border-t border-zinc-800 flex items-center gap-3">
        <div className="relative flex-1">
          <input
            id="tv-chat-input"
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="Напишите сообщение (пультом, клавиатурой или голосом)..."
            className="w-full py-3 pl-4 pr-10 rounded-xl bg-zinc-950 border border-zinc-700/80 text-white placeholder-zinc-500 text-xs focus:outline-none focus:ring-2 focus:ring-sky-500"
          />
        </div>

        <button
          id="tv-chat-send-btn"
          type="button"
          onClick={handleSend}
          className="flex items-center gap-2 px-5 py-3 rounded-xl bg-sky-500 hover:bg-sky-400 text-white font-semibold text-xs transition active:scale-95 shadow-md shadow-sky-500/20"
        >
          <Send className="w-4 h-4" />
          <span>Отправить</span>
        </button>
      </div>
    </div>
  );
};

