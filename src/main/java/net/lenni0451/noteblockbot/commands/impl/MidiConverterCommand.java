package net.lenni0451.noteblockbot.commands.impl;

import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.lenni0451.noteblockbot.Main;
import net.lenni0451.noteblockbot.commands.CommandParser;
import net.lenni0451.noteblockbot.commands.annotations.Arg;
import net.lenni0451.noteblockbot.commands.annotations.Command;
import net.lenni0451.noteblockbot.commands.annotations.RateLimited;
import net.lenni0451.noteblockbot.commands.annotations.Required;
import net.lenni0451.noteblockbot.data.Config;
import net.lenni0451.noteblockbot.data.SQLiteDB;
import net.lenni0451.noteblockbot.utils.NetUtils;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.model.Song;

import java.sql.PreparedStatement;
import java.util.List;

@Slf4j
public class MidiConverterCommand extends CommandParser {

    @RateLimited
    @Command(name = "midiconverter", description = "Convert a midi file to a noteblock song")
    public void run(
            SlashCommandInteractionEvent event,
            @Arg(type = OptionType.ATTACHMENT, name = "midi-file", description = "The midi file that should be converted") @Required Message.Attachment attachment
    ) {
        this.validateAttachment(attachment, Config.SongLimits.maxMidiFileSize, "mid", "midi");
        log.info("User {} uploaded midi file {}", event.getUser().getAsTag(), attachment.getFileName());
        event.reply("Converting the midi file 🎶...").setEphemeral(true).queue();
        Main.getTaskQueue().add(event.getGuild().getIdLong(), List.of(() -> {
            try {
                long time = System.currentTimeMillis();
                byte[] midiData = NetUtils.get(attachment.getUrl()).getContent();
                Song<?, ?, ?> song = NoteBlockLib.readSong(midiData, SongFormat.MIDI);
                song = NoteBlockLib.createSongFromView(song.getView(), SongFormat.NBS);
                byte[] nbsData = NoteBlockLib.writeSong(song);
                time = System.currentTimeMillis() - time;
                log.info("Conversion of midi file {} took {}ms", attachment.getFileName(), time);

                String fileName = attachment.getFileName().substring(0, attachment.getFileName().length() - attachment.getFileExtension().length() - 1);
                event.getHook().editOriginal("Conversion finished in " + (time / 1000) + "s ⏱️").setAttachments(AttachedFile.fromData(nbsData, fileName + ".nbs")).queue();
                try (PreparedStatement statement = Main.getDb().prepare("INSERT INTO \"" + SQLiteDB.MIDI_CONVERSIONS + "\" (\"GuildId\", \"UserId\", \"UserName\", \"Date\", \"FileName\", \"FileSize\", \"FileHash\", \"ConversionDuration\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    statement.setLong(1, event.getGuild().getIdLong());
                    statement.setLong(2, event.getUser().getIdLong());
                    statement.setString(3, event.getUser().getAsTag());
                    statement.setString(4, event.getTimeCreated().toString());
                    statement.setString(5, attachment.getFileName());
                    statement.setLong(6, attachment.getSize());
                    statement.setString(7, Hashing.md5().hashBytes(midiData).toString());
                    statement.setLong(8, time);
                    statement.execute();
                } catch (Throwable t) {
                    log.error("An error occurred while saving the midi conversion", t);
                }
            } catch (Throwable t) {
                log.error("An error occurred while converting the midi file", t);
                event.getHook().editOriginal("An error occurred while converting the midi file").queue();
            }
        }), () -> {});
    }

}
