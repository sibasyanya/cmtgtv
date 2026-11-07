import { ChatItem, ChatMessageItem } from '../types';

export const MOCK_CHATS: ChatItem[] = [
  {
    id: 'chat-1',
    title: 'Cybermasters News & Tech',
    type: 'channel',
    avatarUrl: 'https://images.unsplash.com/photo-1518770660439-4636190af475?w=150&auto=format&fit=crop&q=80',
    lastMessage: 'Вышел новый релиз клиента для Android TV с поддержкой чатов!',
    unreadCount: 3,
    date: '12:45',
    verified: true,
  },
  {
    id: 'chat-2',
    title: 'Кино & Сериалы 4K',
    type: 'channel',
    avatarUrl: 'https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=150&auto=format&fit=crop&q=80',
    lastMessage: 'Подборка лучших научно-фантастических фильмов недели с HDR10+',
    unreadCount: 0,
    date: 'Вчера',
    verified: false,
  },
  {
    id: 'chat-3',
    title: 'Алексей (Android Dev)',
    type: 'direct',
    avatarUrl: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=150&auto=format&fit=crop&q=80',
    lastMessage: 'Проверил сборку TDLib C++ arm64-v8a на телевизоре, видеопоток летает!',
    unreadCount: 1,
    date: '10:15',
    verified: false,
  },
  {
    id: 'chat-4',
    title: 'Smart TV Разработка & Сообщество',
    type: 'group',
    avatarUrl: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=150&auto=format&fit=crop&q=80',
    lastMessage: 'Кто настраивал голосовой ввод через пульт ДУ в Compose for TV?',
    unreadCount: 5,
    date: '09:40',
    verified: false,
  },
  {
    id: 'chat-5',
    title: 'Избранное (Saved Messages)',
    type: 'direct',
    avatarUrl: 'https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=150&auto=format&fit=crop&q=80',
    lastMessage: 'Заметки по проекту TGTV и плейлист видеороликов',
    unreadCount: 0,
    date: '08 сен',
    verified: true,
  }
];

export const INITIAL_MESSAGES: Record<string, ChatMessageItem[]> = {
  'chat-1': [
    {
      id: 'msg-1',
      chatId: 'chat-1',
      senderName: 'Cybermasters News & Tech',
      isOutgoing: false,
      text: 'Добро пожаловать в официальный канал @cybermasters_tv! Документация движка: https://core.telegram.org/tdlib и наш киноканал https://t.me/kino4k_hdr',
      time: '11:20',
    },
    {
      id: 'msg-2',
      chatId: 'chat-1',
      senderName: 'Cybermasters News & Tech',
      isOutgoing: false,
      text: 'Вышел релиз CMTGTV v1.0.45 для Android TV: глобальный поиск по чатам/каналам/сообщениям и интерактивные внутренние и внешние ссылки в сообщениях и профилях!',
      time: '12:45',
    }
  ],
  'chat-3': [
    {
      id: 'msg-10',
      chatId: 'chat-3',
      senderName: 'Алексей',
      isOutgoing: false,
      text: 'Привет! Посмотри обсуждение в группе @smart_tv_dev и репозиторий https://github.com/tdlib/td',
      time: '10:10',
    },
    {
      id: 'msg-11',
      chatId: 'chat-3',
      senderName: 'Вы',
      isOutgoing: true,
      text: 'Привет! Добавили глобальный поиск TDLib и переходы по ссылкам прямо с пульта ТВ.',
      time: '10:12',
    },
    {
      id: 'msg-12',
      chatId: 'chat-3',
      senderName: 'Алексей',
      isOutgoing: false,
      text: 'Проверил сборку TDLib C++ arm64-v8a на телевизоре, видеопоток летает!',
      time: '10:15',
    }
  ]
};

