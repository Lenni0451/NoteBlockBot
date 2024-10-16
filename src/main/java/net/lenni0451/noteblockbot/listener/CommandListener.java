package net.lenni0451.noteblockbot.listener;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.lenni0451.noteblockbot.Main;
import net.lenni0451.noteblockbot.data.SQLiteDB;
import net.lenni0451.noteblockbot.utils.Hash;
import net.lenni0451.noteblockbot.utils.NetUtils;
import net.raphimc.noteblocklib.NoteBlockLib;
import net.raphimc.noteblocklib.format.SongFormat;
import net.raphimc.noteblocklib.model.Song;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.util.List;

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
                    event.reply("Converting the midi file...").setEphemeral(true).queue();
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
                            event.getHook().editOriginal("Conversion finished in " + (time / 1000) + "s").setAttachments(AttachedFile.fromData(nbsData, fileName + ".nbs")).queue();
                            try (PreparedStatement statement = Main.getDb().prepare("INSERT INTO \"" + SQLiteDB.MIDI_CONVERSIONS + "\" (\"GuildId\", \"UserId\", \"UserName\", \"Date\", \"FileName\", \"FileSize\", \"FileHash\", \"ConversionDuration\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                                statement.setLong(1, event.getGuild().getIdLong());
                                statement.setLong(2, event.getUser().getIdLong());
                                statement.setString(3, event.getUser().getAsTag());
                                statement.setString(4, event.getTimeCreated().toString());
                                statement.setString(5, attachment.getFileName());
                                statement.setLong(6, attachment.getSize());
                                statement.setString(7, Hash.md5(midiData));
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
    }

}
