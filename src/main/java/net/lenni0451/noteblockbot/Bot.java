package net.lenni0451.noteblockbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.lenni0451.noteblockbot.listener.MessageListener;

public class Bot {

    private final String token;
    private JDA jda;

    public Bot(final String token) {
        this.token = token;
        this.jda = JDABuilder.create(this.token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new MessageListener())
                .build();
    }

    public JDA getJda() {
        return this.jda;
    }

}
