package net.lenni0451.noteblockbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.lenni0451.noteblockbot.api.ApiNotifier;
import net.lenni0451.noteblockbot.listener.MessageListener;
import net.raphimc.noteblocktool.audio.SoundMap;

import java.io.File;
import java.nio.file.Files;

public class Main {

    private final static File tokenFile = new File("token.txt");
    private static JDA jda;

    public static void main(String[] args) throws Throwable {
        tokenFile.createNewFile();
        String token = Files.readString(tokenFile.toPath()).trim();
        if (token.isBlank()) {
            System.out.println("Please enter a valid token in the token.txt file");
            return;
        }

        SoundMap.reload(new File("Sounds"));
        jda = JDABuilder.create(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new MessageListener())
                .build();
        ApiNotifier.run();
    }

    public static JDA getJda() {
        return jda;
    }

}
