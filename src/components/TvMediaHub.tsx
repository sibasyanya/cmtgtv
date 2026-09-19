import React from 'react';
import {
  Star,
  Tv,
  Film,
  Settings,
  Clock,
  MessageSquare,
  Play,
  Flame,
  LogOut,
  Send,
  Users,
  CheckCheck
} from 'lucide-react';
import { MediaVideoItem, ChatItem } from '../types';
import { MOCK_CHANNELS } from '../data/mockMedia';

interface TvMediaHubProps {
  activeArea: 'sidebar' | 'content';
  sidebarIndex: number;
  contentIndex: number;
  videos: MediaVideoItem[];
  chats: ChatItem[];
  onSelectVideo: (video: MediaVideoItem) => void;
  onSelectChat: (chat: ChatItem) => void;
  onLogout: () => void;
}

export const TvMediaHub: React.FC<TvMediaHubProps> = ({
  activeArea,
  sidebarIndex,
  contentIndex,
  videos,
  chats,
  onSelectVideo,
  onSelectChat,
  onLogout,
}) => {
  const categories = [
    { id: 'feed', label: 'Видеопоток', icon: Film },
    { id: 'chats', label: 'Чаты и Сообщения', icon: MessageSquare },
    { id: 'channels', label: 'Каналы', icon: Tv },
    { id: 'favorites', label: 'Избранное', icon: Star },
    { id: 'settings', label: 'Настройки', icon: Settings },
  ];

  const currentCategory = categories[sidebarIndex] || categories[0];
  const isChatTab = currentCategory.id === 'chats';

  return (
    <div
      id="tv-media-hub"
      className="relative w-full h-full flex bg-zinc-950 text-white overflow-hidden select-none"
    >
      {/* LEFT PANEL: Collapsible Navigation Rail (Jetpack Compose NavigationDrawer equivalent) */}
      <aside
        id="tv-navigation-rail"
        className={`h-full flex flex-col justify-between py-6 px-4 border-r border-zinc-800/80 transition-all duration-300 ${
          activeArea === 'sidebar' ? 'w-64 bg-zinc-900/95 shadow-2xl' : 'w-20 bg-zinc-950/80'
        }`}
      >
        {/* App TV Brand */}
        <div className="flex items-center gap-3 px-2 mb-6">
          <div className="w-10 h-10 rounded-xl bg-sky-500 flex items-center justify-center flex-shrink-0 shadow-md">
            <Film className="w-5 h-5 text-white" />
          </div>
          {activeArea === 'sidebar' && (
            <div className="overflow-hidden whitespace-nowrap animate-fadeIn">
              <h1 className="text-sm font-bold tracking-tight text-white">TG MEDIA TV</h1>
              <p className="text-[10px] text-zinc-400">Чаты, Каналы и Видео</p>
            </div>
          )}
        </div>

        {/* Categories List */}
        <nav className="flex-1 space-y-2">
          {categories.map((cat, idx) => {
            const Icon = cat.icon;
            const isItemFocused = activeArea === 'sidebar' && sidebarIndex === idx;
            return (
              <div
                key={cat.id}
                className={`flex items-center gap-3.5 px-3 py-3 rounded-xl transition-all duration-200 cursor-pointer ${
                  isItemFocused
                    ? 'bg-sky-500 text-white font-bold ring-4 ring-sky-400/50 scale-[1.03] shadow-lg shadow-sky-500/30'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800/60'
                }`}
              >
                <Icon className="w-5 h-5 flex-shrink-0" />
                {activeArea === 'sidebar' && (
                  <span className="text-xs tracking-wide whitespace-nowrap">{cat.label}</span>
                )}
              </div>
            );
          })}
        </nav>

        {/* User Session Info / Exit */}
        <div className="pt-4 border-t border-zinc-800/80">
          <div
            onClick={onLogout}
            className={`flex items-center gap-3 px-3 py-2.5 rounded-xl cursor-pointer transition ${
              activeArea === 'sidebar' && sidebarIndex === 5
                ? 'bg-rose-600 text-white ring-4 ring-rose-400/50'
                : 'text-zinc-400 hover:text-rose-400 hover:bg-zinc-800/60'
            }`}
          >
            <LogOut className="w-5 h-5 flex-shrink-0" />
            {activeArea === 'sidebar' && (
              <span className="text-xs font-medium whitespace-nowrap">Выйти из TDLib</span>
            )}
          </div>
        </div>
      </aside>

      {/* RIGHT PANEL: Media Grid / Chats List */}
      <main className="flex-1 flex flex-col h-full overflow-hidden p-6">
        {/* Top Header Bar */}
        <header className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-3">
            <span className="px-3 py-1 bg-sky-500/10 text-sky-400 border border-sky-500/30 rounded-full text-xs font-semibold flex items-center gap-1.5">
              <Flame className="w-3.5 h-3.5" />
              {currentCategory.label}
            </span>
            <span className="text-xs text-zinc-400 font-mono">
              {isChatTab ? `${chats.length} чатов` : `${videos.length} медиа`}
            </span>
          </div>

          <div className="flex items-center gap-4 text-xs text-zinc-400 font-mono">
            <span className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-400" />
              TDLib: Ready (API 279933)
            </span>
            <span className="hidden md:inline">Управление: Пульт ДУ / Стрелки клавиатуры</span>
          </div>
        </header>

        {/* TAB 1: CHATS & CHANNELS LIST */}
        {isChatTab ? (
          <div className="flex-1 overflow-y-auto pr-2 space-y-3">
            <div className="text-xs text-zinc-400 mb-2 font-mono">
              Выберите канал или чат кнопкой Enter/OK для чтения и отправки сообщений:
            </div>
            {chats.map((chat, idx) => {
              const isCardFocused = activeArea === 'content' && contentIndex === idx;
              return (
                <div
                  key={chat.id}
                  id={`chat-item-${idx}`}
                  onClick={() => onSelectChat(chat)}
                  className={`flex items-center justify-between p-4 rounded-2xl bg-zinc-900/90 border transition-all duration-200 cursor-pointer ${
                    isCardFocused
                      ? 'border-sky-400 ring-4 ring-sky-400/60 scale-[1.01] shadow-xl bg-zinc-800/90 z-10'
                      : 'border-zinc-800/90 hover:border-zinc-700'
                  }`}
                >
                  <div className="flex items-center gap-4 min-w-0">
                    <img
                      src={chat.avatarUrl}
                      alt={chat.title}
                      className="w-12 h-12 rounded-full object-cover border border-zinc-700 flex-shrink-0"
                    />
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <h3 className="text-sm font-bold text-white truncate">{chat.title}</h3>
                        {chat.verified && (
                          <span className="w-3.5 h-3.5 rounded-full bg-sky-500 text-white text-[9px] flex items-center justify-center font-bold">
                            ✓
                          </span>
                        )}
                        <span className="text-[10px] text-zinc-400 font-mono px-2 py-0.5 rounded bg-zinc-800 border border-zinc-700">
                          {chat.type === 'channel' ? 'Канал' : chat.type === 'group' ? 'Группа' : 'Чат'}
                        </span>
                      </div>
                      <p className="text-xs text-zinc-300 truncate mt-1 max-w-xl">
                        {chat.lastMessage}
                      </p>
                    </div>
                  </div>

                  <div className="flex flex-col items-end gap-1.5 flex-shrink-0 ml-4">
                    <span className="text-[11px] font-mono text-zinc-400">{chat.date}</span>
                    {chat.unreadCount > 0 && (
                      <span className="px-2 py-0.5 rounded-full bg-sky-500 text-white font-bold text-[10px] shadow-sm">
                        {chat.unreadCount}
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          /* TAB 2: VIDEOS & CHANNELS GRID */
          <>
            {/* Channels Chips Bar */}
            <div className="flex items-center gap-3 mb-5 overflow-x-hidden pb-1">
              {MOCK_CHANNELS.map((channel) => (
                <div
                  key={channel.id}
                  className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-zinc-900 border border-zinc-800/90 text-xs text-zinc-300"
                >
                  <img
                    src={channel.avatarUrl}
                    alt={channel.title}
                    className="w-5 h-5 rounded-full object-cover"
                  />
                  <span className="font-medium">{channel.title}</span>
                  <span className="text-[10px] text-zinc-400">{channel.subscribers}</span>
                </div>
              ))}
            </div>

            {/* Media Grid with TV Focus Indication */}
            <div className="flex-1 overflow-y-auto pr-2">
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                {videos.map((video, idx) => {
                  const isCardFocused = activeArea === 'content' && contentIndex === idx;
                  return (
                    <div
                      key={video.id}
                      id={`media-card-${idx}`}
                      onClick={() => onSelectVideo(video)}
                      className={`group relative flex flex-col bg-zinc-900/90 rounded-2xl overflow-hidden border transition-all duration-200 cursor-pointer ${
                        isCardFocused
                          ? 'border-sky-400 ring-4 ring-sky-400/60 scale-[1.03] shadow-2xl shadow-sky-500/20 z-10'
                          : 'border-zinc-800/90 hover:border-zinc-700'
                      }`}
                    >
                      {/* Thumbnail Container */}
                      <div className="relative aspect-video w-full overflow-hidden bg-zinc-950">
                        <img
                          src={video.thumbnailUrl}
                          alt={video.title}
                          className={`w-full h-full object-cover transition-transform duration-300 ${
                            isCardFocused ? 'scale-105' : ''
                          }`}
                        />

                        {/* Gradient Overlay */}
                        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/20" />

                        {/* Duration Badge */}
                        <div className="absolute bottom-2.5 right-2.5 px-2 py-0.5 rounded-md bg-black/80 backdrop-blur-md text-[11px] font-mono font-medium text-white flex items-center gap-1 border border-white/10">
                          <Clock className="w-3 h-3 text-sky-400" />
                          {video.duration}
                        </div>

                        {/* Resolution & Size Pill */}
                        <div className="absolute top-2.5 left-2.5 px-2 py-0.5 rounded-md bg-zinc-900/80 backdrop-blur-md text-[10px] font-mono text-zinc-200 border border-white/10">
                          {video.resolution}
                        </div>

                        {/* Play Button Indicator on Focus */}
                        {isCardFocused && (
                          <div className="absolute inset-0 m-auto w-12 h-12 rounded-full bg-sky-500/90 flex items-center justify-center shadow-lg animate-pulse">
                            <Play className="w-6 h-6 text-white fill-white ml-0.5" />
                          </div>
                        )}
                      </div>

                      {/* Metadata info */}
                      <div className="p-4 flex flex-col justify-between flex-1">
                        <h3 className="text-sm font-semibold text-zinc-100 line-clamp-2 leading-snug group-hover:text-white">
                          {video.title}
                        </h3>

                        <div className="mt-3 pt-3 border-t border-zinc-800/60 flex items-center justify-between text-xs text-zinc-400">
                          <div className="flex items-center gap-2">
                            <img
                              src={video.channelAvatar}
                              alt={video.channelName}
                              className="w-5 h-5 rounded-full object-cover border border-zinc-700"
                            />
                            <span className="font-medium text-zinc-300 truncate max-w-[130px]">
                              {video.channelName}
                            </span>
                          </div>

                          <div className="flex items-center gap-2 font-mono text-[11px]">
                            <span>{video.date}</span>
                            <span>•</span>
                            <span>{video.fileSize}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  );
};
