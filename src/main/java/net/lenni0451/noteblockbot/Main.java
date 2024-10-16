package net.lenni0451.noteblockbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.lenni0451.noteblockbot.api.ApiNotifier;
import net.lenni0451.noteblockbot.data.SQLiteDB;
import net.lenni0451.noteblockbot.listener.CommandListener;
import net.lenni0451.noteblockbot.listener.MessageListener;
import net.lenni0451.noteblockbot.task.TaskQueue;
import net.raphimc.noteblocktool.audio.SoundMap;

import java.io.File;
import java.nio.file.Files;

@Slf4j
public class Main {

    private final static File tokenFile = new File("token.txt");
    @Getter
    private static TaskQueue taskQueue;
    @Getter
    private static SQLiteDB db;
    @Getter
    private static JDA jda;

    public static void main(String[] args) throws Throwable {
        tokenFile.createNewFile();
        String token = Files.readString(tokenFile.toPath()).trim();
        if (token.isBlank()) {
            log.error("Please enter a valid token in the token.txt file");
            return;
        }

        SoundMap.reload(new File("Sounds"));
        taskQueue = new TaskQueue();
        db = new SQLiteDB("data.db");
        jda = JDABuilder.create(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new MessageListener())
                .addEventListeners(new CommandListener())
                .build().awaitReady();
        registerCommands();
        ApiNotifier.run();
    }

    private static void registerCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("setup", "Change the settings of the bot")
                        .addOption(OptionType.CHANNEL, "notification-channel", "The channel where the bot should send notifications about newly uploaded songs", false)
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        ).queue();
    }

}
