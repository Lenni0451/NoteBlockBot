package net.lenni0451.noteblockbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.lenni0451.noteblockbot.api.ApiNotifier;
import net.lenni0451.noteblockbot.data.Config;
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
        Config.load();
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
                        .setDefaultPermissions(DefaultMemberPermissions.DISABLED),
                Commands.slash("midiconverter", "Convert a midi file to a noteblock song")
                        .addOption(OptionType.ATTACHMENT, "midi-file", "The midi file that should be converted", true)
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED),
                Commands.slash("resample", "Resample a noteblock song")
                        .addOption(OptionType.ATTACHMENT, "nbs-file", "The nbs file that should be resampled", true)
                        .addOption(OptionType.INTEGER, "speed", "The new speed of the song", false)
                        .addOption(OptionType.STRING, "octave-clamp", "Clamp the octaves of the song", false, true)
                        .setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.ENABLED)
        ).queue();
    }

}
