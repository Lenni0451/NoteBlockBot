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
import net.raphimc.noteblocklib.format.nbs.model.NbsSong;
import net.raphimc.noteblocklib.model.Song;
import net.raphimc.noteblocklib.util.SongResampler;
import net.raphimc.noteblocktool.util.MinecraftOctaveClamp;

import java.io.ByteArrayOutputStream;
import java.sql.PreparedStatement;
import java.util.List;

@Slf4j
public class ResampleCommand extends CommandParser {

    @RateLimited
    @Command(name = "resample", description = "Resample a noteblock song")
    public void run(
            SlashCommandInteractionEvent event,
            @Arg(type = OptionType.ATTACHMENT, name = "nbs-file", description = "The nbs file that should be resampled") @Required Message.Attachment attachment,
            @Arg(type = OptionType.INTEGER, name = "speed", description = "The new speed of the song") Integer speed,
            @Arg(type = OptionType.STRING, name = "octave-clamp", description = "Clamp the octaves of the song") MinecraftOctaveClamp octaveClamp
    ) {
        this.validateAttachment(attachment, Config.SongLimits.maxNbsFileSize, "nbs");
        if (speed == null && (octaveClamp == null || octaveClamp == MinecraftOctaveClamp.NONE)) {
            event.reply("You need to specify at least one option").setEphemeral(true).queue();
        } else if (speed != null && (speed < 10 || speed > 100)) {
            event.reply("The speed must be between 10 and 100").setEphemeral(true).queue();
        } else {
            log.info("User {} uploaded nbs file {}", event.getUser().getAsTag(), attachment.getFileName());
            event.reply("Resampling the nbs file 🎶...").setEphemeral(true).queue();
            Main.getTaskQueue().add(event.getGuild().getIdLong(), List.of(() -> {
                try {
                    long time = System.currentTimeMillis();
                    byte[] nbsData = NetUtils.get(attachment.getUrl()).getContent();
                    NbsSong song = (NbsSong) NoteBlockLib.readSong(nbsData, SongFormat.NBS);
                    if (octaveClamp != null) {
                        song.getNotes().forEach(octaveClamp::correctNote);
                    }
                    if (speed != null) {
                        SongResampler.changeTickSpeed(song, speed);
                    }
                    NbsSong resampledSong = (NbsSong) NoteBlockLib.convertSong(song, SongFormat.NBS);

                    ByteArrayOutputStream resampledData = new ByteArrayOutputStream();
                    this.applyDescription(resampledSong);
                    NoteBlockLib.writeSong(resampledSong, resampledData);
                    time = System.currentTimeMillis() - time;
                    log.info("Resampling of nbs file {} took {}ms", attachment.getFileName(), time);

                    event.getHook().editOriginal("Resampling finished in " + (time / 1000) + "s ⏱️").setAttachments(AttachedFile.fromData(resampledData.toByteArray(), attachment.getFileName())).queue();
                    if (Config.logInteractions) {
                        try (PreparedStatement statement = Main.getDb().prepare("INSERT INTO \"" + SQLiteDB.RESAMPLES + "\" (\"GuildId\", \"UserId\", \"UserName\", \"Date\", \"FileName\", \"FileSize\", \"FileHash\", \"ConversionDuration\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                            statement.setLong(1, event.getGuild().getIdLong());
                            statement.setLong(2, event.getUser().getIdLong());
                            statement.setString(3, event.getUser().getAsTag());
                            statement.setString(4, event.getTimeCreated().toString());
                            statement.setString(5, attachment.getFileName());
                            statement.setLong(6, attachment.getSize());
                            statement.setString(7, Hashing.md5().hashBytes(nbsData).toString());
                            statement.setLong(8, time);
                            statement.execute();
                        } catch (Throwable t) {
                            log.error("An error occurred while saving the midi conversion", t);
                        }
                    }
                } catch (Throwable t) {
                    log.error("An error occurred while resampling the nbs file", t);
                    event.getHook().editOriginal("An error occurred while resampling the nbs file").queue();
                }
            }), () -> {});
        }
    }

    private void applyDescription(final Song song) {
        String description = song.getDescriptionOr("");
        if (!description.isEmpty()) description += "\n";
        description += "Resampled using NoteBlockBot";
        song.setDescription(description);
    }

}
