package net.lenni0451.noteblockbot.listener;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.lenni0451.noteblockbot.DataStore;
import org.jetbrains.annotations.NotNull;

public class CommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) return;
        switch (event.getName()) {
            case "setup" -> {
                GuildChannelUnion notificationChannel = event.getOption("notification-channel", OptionMapping::getAsChannel);
                if (notificationChannel != null) {
                    if (notificationChannel.getType().equals(ChannelType.TEXT)) {
                        DataStore.setChannel(event.getGuild().getIdLong(), notificationChannel.getIdLong());
                        event.reply("Successfully set the notification channel").setEphemeral(true).queue();
                    } else {
                        event.reply("Only text channels are supported").setEphemeral(true).queue();
                    }
                }
            }
        }
    }

}
