package net.lenni0451.noteblockbot.listener;

import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.lenni0451.noteblockbot.Main;
import net.lenni0451.noteblockbot.data.Config;
import net.lenni0451.noteblockbot.data.RateLimiter;
import net.lenni0451.noteblockbot.data.SQLiteDB;
import net.lenni0451.noteblockbot.utils.NetUtils;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.format.nbs.NbsSong;
import net.raphimc.noteblocklib.model.Song;
import net.raphimc.noteblocklib.util.SongResampler;
import net.raphimc.noteblocklib.util.SongUtil;
import net.raphimc.noteblocktool.util.MinecraftOctaveClamp;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
public class CommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;
        switch (event.getName()) {
            case "setup" -> {
                GuildChannelUnion notificationChannel = event.getOption("notification-channel", OptionMapping::getAsChannel);
                if (notificationChannel != null) {
                    if (notificationChannel.getType().equals(ChannelType.TEXT)) {
                        try (PreparedStatement statement = Main.getDb().prepare("INSERT OR REPLACE INTO \"" + SQLiteDB.UPLOAD_NOTIFICATION + "\" (\"GuildId\", \"ChannelId\") VALUES (?, ?)")) {
                            statement.setLong(1, event.getGuild().getIdLong());
                            statement.setLong(2, notificationChannel.getIdLong());
                            statement.execute();
                            event.reply("Successfully set the notification channel").setEphemeral(true).queue();
                        } catch (Throwable t) {
                            event.reply("An error occurred while setting the notification channel").setEphemeral(true).queue();
                        }
                    } else {
                        event.reply("Only text channels are supported").setEphemeral(true).queue();
                    }
                }
            }
            case "midiconverter" -> {
                Message.Attachment attachment = event.getOption("midi-file", OptionMapping::getAsAttachment);
                if (attachment.getFileExtension() == null || (!attachment.getFileExtension().equalsIgnoreCase("mid") && !attachment.getFileExtension().equalsIgnoreCase("midi"))) {
                    event.reply("The attachment is not a valid midi file").setEphemeral(true).queue();
                } else {
                    log.info("User {} uploaded midi file {}", event.getUser().getAsTag(), attachment.getFileName());
                    if (!RateLimiter.tryUser(event.getUser().getIdLong()) || !RateLimiter.tryGuild(event.getGuild().getIdLong())) {
                        event.reply("You are sending too many requests. Please wait a bit before sending another request. 🐌").setEphemeral(true).queue();
                    } else if (attachment.getSize() > Config.SongLimits.maxMidiFileSize) {
                        event.reply("The midi file is too large").setEphemeral(true).queue();
                    } else {
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
            }
            case "resample" -> {
                Message.Attachment attachment = event.getOption("nbs-file", OptionMapping::getAsAttachment);
                Integer speed = event.getOption("speed", OptionMapping::getAsInt);
                String octaveClamp = event.getOption("octave-clamp", OptionMapping::getAsString);
                if (speed == null && octaveClamp == null) {
                    event.reply("You need to specify at least one option").setEphemeral(true).queue();
                } else if (attachment.getFileExtension() == null || !attachment.getFileExtension().equalsIgnoreCase("nbs")) {
                    event.reply("The attachment is not a valid nbs file").setEphemeral(true).queue();
                } else if (speed != null && (speed < 10 || speed > 100)) {
                    event.reply("The speed must be between 10 and 100").setEphemeral(true).queue();
                } else if (octaveClamp != null && (Arrays.stream(MinecraftOctaveClamp.values()).noneMatch(clamp -> clamp.name().equalsIgnoreCase(octaveClamp)) || octaveClamp.equalsIgnoreCase("NONE"))) {
                    event.reply("The octave clamp is not valid").setEphemeral(true).queue();
                } else if (!RateLimiter.tryUser(event.getUser().getIdLong()) || !RateLimiter.tryGuild(event.getGuild().getIdLong())) {
                    event.reply("You are sending too many requests. Please wait a bit before sending another request. 🐌").setEphemeral(true).queue();
                } else if (attachment.getSize() > Config.SongLimits.maxNbsFileSize) {
                    event.reply("The nbs file is too large").setEphemeral(true).queue();
                } else {
                    log.info("User {} uploaded nbs file {}", event.getUser().getAsTag(), attachment.getFileName());
                    event.reply("Resampling the nbs file 🎶...").setEphemeral(true).queue();
                    Main.getTaskQueue().add(event.getGuild().getIdLong(), List.of(() -> {
                        try {
                            long time = System.currentTimeMillis();
                            byte[] nbsData = NetUtils.get(attachment.getUrl()).getContent();
                            NbsSong song = (NbsSong) NoteBlockLib.readSong(nbsData, SongFormat.NBS);
                            if (octaveClamp != null) {
                                SongUtil.applyToAllNotes(song.getView(), note -> MinecraftOctaveClamp.valueOf(octaveClamp.toUpperCase(Locale.ROOT)).correctNote(note));
                            }
                            if (speed != null) {
                                SongResampler.applyNbsTempoChangers(song, song.getView());
                                SongResampler.changeTickSpeed(song.getView(), speed);
                            }
                            Song<?, ?, ?> resampledSong = NoteBlockLib.createSongFromView(song.getView(), SongFormat.NBS);
                            byte[] resampledData = NoteBlockLib.writeSong(resampledSong);
                            time = System.currentTimeMillis() - time;
                            log.info("Resampling of nbs file {} took {}ms", attachment.getFileName(), time);

                            event.getHook().editOriginal("Resampling finished in " + (time / 1000) + "s ⏱️").setAttachments(AttachedFile.fromData(resampledData, attachment.getFileName())).queue();
                        } catch (Throwable t) {
                            log.error("An error occurred while resampling the nbs file", t);
                            event.getHook().editOriginal("An error occurred while resampling the nbs file").queue();
                        }
                    }), () -> {});
                }
            }
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (!event.isFromGuild()) return;
        if (event.getName().equalsIgnoreCase("resample") && event.getFocusedOption().getName().equalsIgnoreCase("octave-clamp")) {
            List<Command.Choice> options = Arrays.stream(MinecraftOctaveClamp.values())
                    .filter(clamp -> clamp != MinecraftOctaveClamp.NONE)
                    .map(Enum::name)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(event.getFocusedOption().getValue().toLowerCase(Locale.ROOT)))
                    .map(name -> new Command.Choice(name, name))
                    .toList();
            event.replyChoices(options).queue();
        }
    }

}
