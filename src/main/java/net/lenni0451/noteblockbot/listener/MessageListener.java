package net.lenni0451.noteblockbot.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;
import net.lenni0451.noteblockbot.utils.Mp3Encoder;
import net.lenni0451.noteblockbot.utils.NetUtils;
import net.lenni0451.noteblockbot.utils.SongInfo;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.nbs.NbsSong;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Slf4j
public class MessageListener extends ListenerAdapter {

    private static final Emoji CALCULATING = Emoji.fromUnicode("⏱️");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S*");
    private static final Pattern NOTEBLOCK_WORLD_PATTERN = Pattern.compile("https://noteblock\\.world/song/([^/]+)");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        boolean isRunningAction = this.handleAttachments(event);
        isRunningAction |= this.handleNoteBlockWorldLinks(event);
        if (isRunningAction) {
            event.getMessage().addReaction(CALCULATING).queue();
            EXECUTOR.submit(() -> event.getMessage().removeReaction(CALCULATING).queue());
        }
    }

    private boolean handleAttachments(final MessageReceivedEvent event) {
        List<Message.Attachment> nbsFiles = this.getNbsFiles(event.getMessage());
        for (Message.Attachment attachment : nbsFiles) {
            log.info("User {} uploaded song {}", event.getAuthor().getAsTag(), attachment.getFileName());
            EXECUTOR.submit(() -> this.processSong(event.getMessage(), attachment.getFileName(), attachment.getUrl()));
        }
        return !nbsFiles.isEmpty();
    }

    private boolean handleNoteBlockWorldLinks(final MessageReceivedEvent event) {
        String rawMessage = event.getMessage().getContentRaw();
        List<String> ids = NOTEBLOCK_WORLD_PATTERN.matcher(rawMessage).results().map(match -> match.group(1)).toList();
        for (String id : ids) {
            String downloadUrl = "https://api.noteblock.world/api/v1/song/" + id + "/download?src=downloadButton";
            EXECUTOR.submit(() -> this.processSong(event.getMessage(), id + ".nbs", downloadUrl));
        }
        return !ids.isEmpty();
    }

    private List<Message.Attachment> getNbsFiles(final Message message) {
        return message
                .getAttachments()
                .stream()
                .filter(attachment -> attachment.getFileExtension() != null)
                .filter(attachment -> attachment.getFileExtension().equalsIgnoreCase("nbs"))
                .toList();
    }

    private void processSong(final Message message, final String fileName, final String url) {
        try {
            byte[] songData = NetUtils.get(url).getContent();
            NbsSong song = (NbsSong) NoteBlockLib.readSong(songData, SongFormat.NBS);
            byte[] mp3Data = Mp3Encoder.encode(song);
            String info = SongInfo.fromSong(song);
            info = URL_PATTERN.matcher(info).replaceAll("<$0>");
            String songName = fileName.substring(0, fileName.length() - 4);
            if (!song.getHeader().getTitle().isBlank()) songName = song.getHeader().getTitle();
            message.replyFiles(FileUpload.fromData(mp3Data, songName + ".mp3")).setContent(info).queue();
        } catch (Throwable t) {
            log.error("Failed to render song", t);
        }
    }

}
