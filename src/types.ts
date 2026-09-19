export type ScreenType = 'auth' | 'hub' | 'player' | 'chat_view';

export type NavCategory = 'feed' | 'channels' | 'chats' | 'favorites' | 'settings';

export interface ChatItem {
  id: string;
  title: string;
  type: 'channel' | 'group' | 'direct';
  avatarUrl: string;
  lastMessage: string;
  unreadCount: number;
  date: string;
  verified?: boolean;
}

export interface ChatMessageItem {
  id: string;
  chatId: string;
  senderName: string;
  isOutgoing: boolean;
  text: string;
  time: string;
  mediaUrl?: string;
  mediaType?: 'image' | 'video';
}

export interface MediaVideoItem {
  id: string;
  title: string;
  channelName: string;
  channelAvatar: string;
  duration: string; // e.g. "14:20"
  durationSeconds: number;
  date: string;
  thumbnailUrl: string;
  videoUrl: string;
  fileSize: string;
  resolution: string;
  views: string;
}

export interface ChannelItem {
  id: string;
  title: string;
  username: string;
  avatarUrl: string;
  subscribers: string;
  mediaCount: number;
}
