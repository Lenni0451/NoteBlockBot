package net.lenni0451.noteblockbot.listener;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.lenni0451.noteblockbot.Main;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;

public class CommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;
        switch (event.getName()) {
            case "setup" -> {
                GuildChannelUnion notificationChannel = event.getOption("notification-channel", OptionMapping::getAsChannel);
                if (notificationChannel != null) {
                    if (notificationChannel.getType().equals(ChannelType.TEXT)) {
                        try (PreparedStatement statement = Main.getDb().prepare("INSERT OR REPLACE INTO \"NoteBlockWorldUploadNotificationChannels\" (\"GuildId\", \"ChannelId\") VALUES (?, ?)")) {
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
        }
    }

}
