package net.lenni0451.noteblockbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.lenni0451.noteblockbot.api.ApiNotifier;
import net.lenni0451.noteblockbot.commands.CommandParser;
import net.lenni0451.noteblockbot.commands.impl.AdminCommands;
import net.lenni0451.noteblockbot.commands.impl.MidiConverterCommand;
import net.lenni0451.noteblockbot.commands.impl.ResampleCommand;
import net.lenni0451.noteblockbot.data.Config;
import net.lenni0451.noteblockbot.data.SQLiteDB;
import net.lenni0451.noteblockbot.listener.MessageListener;
import net.lenni0451.noteblockbot.task.TaskQueue;
import net.raphimc.noteblocktool.audio.SoundMap;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

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
                .build().awaitReady();
        registerCommands();
        ApiNotifier.run();
    }

    private static void registerCommands() {
        CommandListUpdateAction commands = jda.updateCommands();
        List<CommandParser> commandParsers = List.of(
                new AdminCommands(),
                new MidiConverterCommand(),
                new ResampleCommand()
        );
        for (CommandParser parser : commandParsers) {
            parser.register(commands);
            jda.addEventListener(parser);
        }
        commands.queue();
    }

}
