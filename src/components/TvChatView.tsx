import React, { useState } from 'react';
import {
  ArrowLeft,
  Send,
  Mic,
  MessageSquare,
  Users,
  CheckCheck,
  Sparkles,
  Smile,
  Hash
} from 'lucide-react';
import { ChatItem, ChatMessageItem } from '../types';

interface TvChatViewProps {
  chat: ChatItem;
  messages: ChatMessageItem[];
  onBack: () => void;
  onSendMessage: (text: string) => void;
}

export const TvChatView: React.FC<TvChatViewProps> = ({
  chat,
  messages,
  onBack,
  onSendMessage,
}) => {
  const [inputText, setInputText] = useState('');
  const [focusedAction, setFocusedAction] = useState<'input' | 'send' | 'back' | 'quick'>('input');

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
            className={`p-2.5 rounded-full transition ${
              focusedAction === 'back'
                ? 'bg-sky-500 text-white ring-4 ring-sky-400/50'
                : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
            }`}
            title="Назад к списку чатов (Esc / Back)"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>

          <img
            src={chat.avatarUrl}
            alt={chat.title}
            className="w-10 h-10 rounded-full object-cover border border-zinc-700 shadow-md"
          />

          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-bold text-white tracking-tight">{chat.title}</h2>
              {chat.verified && (
                <span className="w-4 h-4 rounded-full bg-sky-500 flex items-center justify-center text-[10px] font-bold text-white">
                  ✓
                </span>
              )}
            </div>
            <p className="text-[11px] text-zinc-400">
              {chat.type === 'channel'
                ? 'Канал Telegram'
                : chat.type === 'group'
                ? 'Группа'
                : 'Личный чат'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span className="px-2.5 py-1 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-md text-[11px] font-mono">
            TDLib: Online
          </span>
        </div>
      </div>

      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {messages.map((msg) => {
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
