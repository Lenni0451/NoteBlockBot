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
import net.raphimc.noteblocklib.format.nbs.NbsSong;
import net.raphimc.noteblocklib.format.nbs.model.NbsHeader;
import net.raphimc.noteblocklib.util.SongResampler;
import net.raphimc.noteblocklib.util.SongUtil;
import net.raphimc.noteblocktool.util.MinecraftOctaveClamp;

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
                        SongUtil.applyToAllNotes(song.getView(), octaveClamp::correctNote);
                    }
                    if (speed != null) {
                        SongResampler.applyNbsTempoChangers(song, song.getView());
                        SongResampler.changeTickSpeed(song.getView(), speed);
                    }
                    NbsSong resampledSong = (NbsSong) NoteBlockLib.createSongFromView(song.getView(), SongFormat.NBS);
                    this.copyHeader(song.getHeader(), resampledSong.getHeader());
                    byte[] resampledData = NoteBlockLib.writeSong(resampledSong);
                    time = System.currentTimeMillis() - time;
                    log.info("Resampling of nbs file {} took {}ms", attachment.getFileName(), time);

                    event.getHook().editOriginal("Resampling finished in " + (time / 1000) + "s ⏱️").setAttachments(AttachedFile.fromData(resampledData, attachment.getFileName())).queue();
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
                } catch (Throwable t) {
                    log.error("An error occurred while resampling the nbs file", t);
                    event.getHook().editOriginal("An error occurred while resampling the nbs file").queue();
                }
            }), () -> {});
        }
    }

    private void copyHeader(final NbsHeader from, final NbsHeader to) {
        to.setVersion((byte) Math.max(from.getVersion(), to.getVersion()));
        to.setAuthor(from.getAuthor());
        to.setOriginalAuthor(from.getOriginalAuthor());
        to.setDescription(from.getDescription());
        to.setAutoSave(from.isAutoSave());
        to.setAutoSaveInterval(from.getAutoSaveInterval());
        to.setTimeSignature(from.getTimeSignature());
        to.setMinutesSpent(from.getMinutesSpent());
        to.setLeftClicks(from.getLeftClicks());
        to.setRightClicks(from.getRightClicks());
        to.setNoteBlocksAdded(from.getNoteBlocksAdded());
        to.setNoteBlocksRemoved(from.getNoteBlocksRemoved());
        to.setSourceFileName(from.getSourceFileName());
        to.setLoop(from.isLoop());
        to.setMaxLoopCount(from.getMaxLoopCount());
        to.setLoopStartTick(from.getLoopStartTick());

        String newDescription = from.getDescription();
        if (!newDescription.isEmpty()) newDescription += "\n";
        newDescription += "Resampled using NoteBlockBot";
        to.setDescription(newDescription);
    }

}
