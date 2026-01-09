package net.lenni0451.noteblockbot.listener;

import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import net.lenni0451.noteblockbot.Main;
import net.lenni0451.noteblockbot.data.Config;
import net.lenni0451.noteblockbot.data.RateLimiter;
import net.lenni0451.noteblockbot.data.SQLiteDB;
import net.lenni0451.noteblockbot.export.Mp3Encoder;
import net.lenni0451.noteblockbot.utils.NetUtils;
import net.lenni0451.noteblockbot.utils.SongInfo;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class MessageListener extends ListenerAdapter {

    private static final Emoji CALCULATING = Emoji.fromUnicode("⏱️");
    private static final Emoji RATE_LIMITED = Emoji.fromUnicode("🐌");
    private static final Emoji ERROR = Emoji.fromUnicode("❌");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S*");

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        List<Runnable> tasks = new ArrayList<>();
        this.handleNbsAttachments(event, tasks);
        if (!tasks.isEmpty()) {
            event.getMessage().addReaction(CALCULATING).queue();
            Main.getTaskQueue().add(event.getGuild().getIdLong(), tasks, () -> event.getMessage().removeReaction(CALCULATING).queue());
        }
    }

    private void handleNbsAttachments(final MessageReceivedEvent event, final List<Runnable> tasks) {
        List<Message.Attachment> nbsFiles = event.getMessage().getAttachments().stream()
                .filter(attachment -> attachment.getFileExtension() != null)
                .filter(attachment -> attachment.getFileExtension().equalsIgnoreCase("nbs"))
                .toList();
        for (Message.Attachment attachment : nbsFiles) {
            if (!RateLimiter.tryUser(event.getAuthor().getIdLong()) || !RateLimiter.tryGuild(event.getGuild().getIdLong())) {
                if (tasks.stream().noneMatch(t -> t instanceof SendRateLimitReactionTask)) {
                    tasks.add(new SendRateLimitReactionTask(event.getMessage()));
                }
                continue;
            }

            log.info("User {} uploaded song {}", event.getAuthor().getAsTag(), attachment.getFileName());
            tasks.add(() -> this.processSong(event.getMessage(), attachment.getFileName(), attachment.getUrl()));
        }
    }

    private void processSong(final Message message, final String fileName, final String url) {
        try {
            long start = System.currentTimeMillis();
            byte[] songData = NetUtils.get(url).getContent().getAsBytes();
            NbsSong song = (NbsSong) NoteBlockLib.readSong(songData, SongFormat.NBS);
            byte[] mp3Data = Mp3Encoder.encode(song, new File("Sounds"));
            String info = SongInfo.fromSong(song);
            info = URL_PATTERN.matcher(info).replaceAll("<$0>");
            String songName = fileName.substring(0, fileName.length() - 4);
            if (!song.getTitleOr("").isBlank()) songName = song.getTitle();
            message.replyFiles(FileUpload.fromData(mp3Data, songName + ".mp3")).setContent(info).queue();
            if (Config.logInteractions) {
                try (PreparedStatement statement = Main.getDb().prepare("INSERT INTO \"" + SQLiteDB.MP3_CONVERSIONS + "\" (\"GuildId\", \"UserId\", \"UserName\", \"Date\", \"Source\", \"FileName\", \"FileSize\", \"FileHash\", \"ConversionDuration\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setLong(1, message.getGuild().getIdLong());
                    statement.setLong(2, message.getAuthor().getIdLong());
                    statement.setString(3, message.getAuthor().getAsTag());
                    statement.setString(4, message.getTimeCreated().toString());
                    statement.setInt(5, 0); //0=Attachment 1=NoteblockWorld
                    statement.setString(6, fileName);
                    statement.setInt(7, songData.length);
                    statement.setString(8, Hashing.md5().hashBytes(songData).toString());
                    statement.setLong(9, System.currentTimeMillis() - start);
                    statement.execute();
                }
            }
        } catch (Throwable t) {
            log.error("Failed to render song", t);
            message.addReaction(ERROR).queue();
        }
    }


    private record SendRateLimitReactionTask(Message message) implements Runnable {
        @Override
        public void run() {
            this.message.addReaction(RATE_LIMITED).queue();
        }
    }

}
