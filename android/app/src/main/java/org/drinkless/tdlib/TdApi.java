//
// TDLib API Data Classes and Functions for Telegram Media Android TV
//
package org.drinkless.tdlib;

public final class TdApi {

    public static abstract class Object {
        public abstract int getConstructor();
    }

    public static abstract class Function<T extends Object> extends Object {}

    // Base Status Objects
    public static class Ok extends Object {
        public static final int CONSTRUCTOR = -722513903;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class Error extends Object {
        public static final int CONSTRUCTOR = -1679568709;
        public int code;
        public String message;

        public Error() {}
        public Error(int code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
        @Override
        public String toString() { return "Error " + code + ": " + message; }
    }

    // Authorization States
    public static abstract class AuthorizationState extends Object {}

    public static class AuthorizationStateWaitTdlibParameters extends AuthorizationState {
        public static final int CONSTRUCTOR = -1183590487;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class AuthorizationStateWaitOtherDeviceConfirmation extends AuthorizationState {
        public static final int CONSTRUCTOR = 860432549;
        public String link; // tg://login?token=...

        public AuthorizationStateWaitOtherDeviceConfirmation() {}
        public AuthorizationStateWaitOtherDeviceConfirmation(String link) {
            this.link = link;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class AuthorizationStateWaitPhoneNumber extends AuthorizationState {
        public static final int CONSTRUCTOR = 377983636;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class AuthorizationStateWaitCode extends AuthorizationState {
        public static final int CONSTRUCTOR = -2097787340;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class AuthorizationStateWaitPassword extends AuthorizationState {
        public static final int CONSTRUCTOR = -1632733979;
        public String passwordHint;
        public boolean hasRecoveryEmailAddress;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class AuthorizationStateReady extends AuthorizationState {
        public static final int CONSTRUCTOR = -1877665796;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class AuthorizationStateLoggingOut extends AuthorizationState {
        public static final int CONSTRUCTOR = 154694437;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class AuthorizationStateClosing extends AuthorizationState {
        public static final int CONSTRUCTOR = 135894149;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class AuthorizationStateClosed extends AuthorizationState {
        public static final int CONSTRUCTOR = 1632873979;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    // Updates
    public static abstract class Update extends Object {}

    public static class UpdateAuthorizationState extends Update {
        public static final int CONSTRUCTOR = 1606497746;
        public AuthorizationState authorizationState;

        public UpdateAuthorizationState() {}
        public UpdateAuthorizationState(AuthorizationState authorizationState) {
            this.authorizationState = authorizationState;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class UpdateNewMessage extends Update {
        public static final int CONSTRUCTOR = 521404104;
        public Message message;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class UpdateFile extends Update {
        public static final int CONSTRUCTOR = 478672047;
        public File file;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    // Core Parameters
    public static class TdlibParameters extends Object {
        public static final int CONSTRUCTOR = -1468171097;
        public boolean useTestDc = false;
        public String databaseDirectory = "";
        public String filesDirectory = "";
        public byte[] databaseEncryptionKey = new byte[0];
        public boolean useFileDatabase = true;
        public boolean useChatInfoDatabase = true;
        public boolean useMessageDatabase = true;
        public boolean useSecretChats = false;
        public int apiId = 0;
        public String apiHash = "";
        public String systemLanguageCode = "en";
        public String deviceModel = "Android TV";
        public String systemVersion = "Android 14";
        public String applicationVersion = "1.0.0";
        public boolean enableStorageOptimizer = true;
        public boolean ignoreFileNames = false;

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    // Functions
    public static class SetTdlibParameters extends Function<Ok> {
        public static final int CONSTRUCTOR = 1888424172;
        public boolean useTestDc;
        public String databaseDirectory;
        public String filesDirectory;
        public byte[] databaseEncryptionKey;
        public boolean useFileDatabase;
        public boolean useChatInfoDatabase;
        public boolean useMessageDatabase;
        public boolean useSecretChats;
        public int apiId;
        public String apiHash;
        public String systemLanguageCode;
        public String deviceModel;
        public String systemVersion;
        public String applicationVersion;

        public SetTdlibParameters() {}
        public SetTdlibParameters(
            boolean useTestDc,
            String databaseDirectory,
            String filesDirectory,
            byte[] databaseEncryptionKey,
            boolean useFileDatabase,
            boolean useChatInfoDatabase,
            boolean useMessageDatabase,
            boolean useSecretChats,
            int apiId,
            String apiHash,
            String systemLanguageCode,
            String deviceModel,
            String systemVersion,
            String applicationVersion
        ) {
            this.useTestDc = useTestDc;
            this.databaseDirectory = databaseDirectory;
            this.filesDirectory = filesDirectory;
            this.databaseEncryptionKey = databaseEncryptionKey;
            this.useFileDatabase = useFileDatabase;
            this.useChatInfoDatabase = useChatInfoDatabase;
            this.useMessageDatabase = useMessageDatabase;
            this.useSecretChats = useSecretChats;
            this.apiId = apiId;
            this.apiHash = apiHash;
            this.systemLanguageCode = systemLanguageCode;
            this.deviceModel = deviceModel;
            this.systemVersion = systemVersion;
            this.applicationVersion = applicationVersion;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class RequestQrCodeAuthentication extends Function<Ok> {
        public static final int CONSTRUCTOR = -1865243888;
        public String[] otherUserIds = new String[0];

        public RequestQrCodeAuthentication() {}
        public RequestQrCodeAuthentication(String[] otherUserIds) {
            this.otherUserIds = otherUserIds;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class CheckDatabaseEncryptionKey extends Function<Ok> {
        public static final int CONSTRUCTOR = 1381373977;
        public byte[] encryptionKey;
        public CheckDatabaseEncryptionKey() {}
        public CheckDatabaseEncryptionKey(byte[] encryptionKey) { this.encryptionKey = encryptionKey; }
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class LogOut extends Function<Ok> {
        public static final int CONSTRUCTOR = 765432101;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    // Media & File Models
    public static class File extends Object {
        public static final int CONSTRUCTOR = 123456789;
        public int id;
        public long size;
        public long expectedSize;
        public LocalFile local;
        public RemoteFile remote;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class LocalFile extends Object {
        public static final int CONSTRUCTOR = 123456788;
        public String path;
        public boolean canBeDownloaded;
        public boolean canBeDeleted;
        public boolean isDownloadingActive;
        public boolean isDownloadingCompleted;
        public long downloadedPrefixSize;
        public long downloadedSize;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class RemoteFile extends Object {
        public static final int CONSTRUCTOR = 123456787;
        public String id;
        public String uniqueId;
        public boolean isUploadingActive;
        public boolean isUploadingCompleted;
        public long uploadedSize;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class DownloadFile extends Function<File> {
        public static final int CONSTRUCTOR = 478672049;
        public int fileId;
        public int priority;
        public long offset;
        public long limit;
        public boolean synchronous;

        public DownloadFile() {}
        public DownloadFile(int fileId, int priority, long offset, long limit, boolean synchronous) {
            this.fileId = fileId;
            this.priority = priority;
            this.offset = offset;
            this.limit = limit;
            this.synchronous = synchronous;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class Message extends Object {
        public static final int CONSTRUCTOR = 998877665;
        public long id;
        public long chatId;
        public int date;
        public MessageContent content;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static abstract class MessageContent extends Object {}

    public static class MessageVideo extends MessageContent {
        public static final int CONSTRUCTOR = 998877664;
        public Video video;
        public FormattedText caption;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class Video extends Object {
        public static final int CONSTRUCTOR = 998877663;
        public int duration;
        public int width;
        public int height;
        public String fileName;
        public String mimeType;
        public File video;
        public Thumbnail thumbnail;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class Thumbnail extends Object {
        public static final int CONSTRUCTOR = 998877662;
        public int width;
        public int height;
        public File file;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class FormattedText extends Object {
        public static final int CONSTRUCTOR = 998877661;
        public String text;
        public FormattedText() {}
        public FormattedText(String text) { this.text = text; }
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class MessageText extends MessageContent {
        public static final int CONSTRUCTOR = 998877660;
        public FormattedText text;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static abstract class InputMessageContent extends Object {}

    public static class InputMessageText extends InputMessageContent {
        public static final int CONSTRUCTOR = 998877659;
        public FormattedText text;
        public boolean disableWebPagePreview;
        public boolean clearDraft;

        public InputMessageText() {}
        public InputMessageText(FormattedText text, boolean disableWebPagePreview, boolean clearDraft) {
            this.text = text;
            this.disableWebPagePreview = disableWebPagePreview;
            this.clearDraft = clearDraft;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class Chat extends Object {
        public static final int CONSTRUCTOR = 887766554;
        public long id;
        public String title;
        public ChatPhotoInfo photo;
        public Message lastMessage;
        public int unreadCount;
        public ChatType type;

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class ChatPhotoInfo extends Object {
        public static final int CONSTRUCTOR = 887766553;
        public File small;
        public File big;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static abstract class ChatType extends Object {}
    public static class ChatTypePrivate extends ChatType {
        public static final int CONSTRUCTOR = 887766552;
        public long userId;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }
    public static class ChatTypeBasicGroup extends ChatType {
        public static final int CONSTRUCTOR = 887766551;
        public long basicGroupId;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }
    public static class ChatTypeSupergroup extends ChatType {
        public static final int CONSTRUCTOR = 887766550;
        public long supergroupId;
        public boolean isChannel;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class Chats extends Object {
        public static final int CONSTRUCTOR = 887766549;
        public int totalCount;
        public long[] chatIds;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class Messages extends Object {
        public static final int CONSTRUCTOR = 887766548;
        public int totalCount;
        public Message[] messages;
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class LoadChats extends Function<Ok> {
        public static final int CONSTRUCTOR = 887766547;
        public int limit;
        public LoadChats() {}
        public LoadChats(int limit) { this.limit = limit; }
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class GetChats extends Function<Chats> {
        public static final int CONSTRUCTOR = 887766546;
        public int limit;
        public GetChats() {}
        public GetChats(int limit) { this.limit = limit; }
        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class GetChatHistory extends Function<Messages> {
        public static final int CONSTRUCTOR = 887766545;
        public long chatId;
        public long fromMessageId;
        public int offset;
        public int limit;
        public boolean onlyLocal;

        public GetChatHistory() {}
        public GetChatHistory(long chatId, long fromMessageId, int offset, int limit, boolean onlyLocal) {
            this.chatId = chatId;
            this.fromMessageId = fromMessageId;
            this.offset = offset;
            this.limit = limit;
            this.onlyLocal = onlyLocal;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }

    public static class SendMessage extends Function<Message> {
        public static final int CONSTRUCTOR = 887766544;
        public long chatId;
        public long messageThreadId;
        public long replyToMessageId;
        public InputMessageContent inputMessageContent;

        public SendMessage() {}
        public SendMessage(long chatId, long replyToMessageId, InputMessageContent inputMessageContent) {
            this.chatId = chatId;
            this.messageThreadId = 0;
            this.replyToMessageId = replyToMessageId;
            this.inputMessageContent = inputMessageContent;
        }

        @Override
        public int getConstructor() { return CONSTRUCTOR; }
    }
}
