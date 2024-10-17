package net.lenni0451.noteblockbot.commands.impl;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.lenni0451.noteblockbot.Main;
import net.lenni0451.noteblockbot.commands.CommandParser;
import net.lenni0451.noteblockbot.commands.annotations.Arg;
import net.lenni0451.noteblockbot.commands.annotations.Command;
import net.lenni0451.noteblockbot.commands.annotations.RateLimited;
import net.lenni0451.noteblockbot.data.SQLiteDB;

import java.sql.PreparedStatement;

public class AdminCommands extends CommandParser {

    @RateLimited(guild = false)
    @Command(name = "setup", description = "Change the settings of the bot", permissions = Permission.ADMINISTRATOR)
    public void setup(
            SlashCommandInteractionEvent event,
            @Arg(type = OptionType.CHANNEL, name = "notification-channel", description = "The channel where the bot should send notifications about newly uploaded songs") GuildChannelUnion notificationChannel
    ) {
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

}
