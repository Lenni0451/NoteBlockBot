package net.lenni0451.noteblockbot.data;

import net.lenni0451.optconfig.annotations.Description;
import net.lenni0451.optconfig.annotations.OptConfig;
import net.lenni0451.optconfig.annotations.Option;
import net.lenni0451.optconfig.annotations.Section;

@OptConfig
public class Config {

    @Option
    @Description({"Log all user interactions with the bot", "The logs are written into the sqlite database", "Currently they are not used but later they could be used for statistics"})
    public static boolean logInteractions = true;

    @Section(name = "SongLimits")
    public static class SongLimits {
        @Option("MaxNbsFileSize")
        @Description("The maximum file size for rendering NBS files to mp3 in bytes")
        public static int maxNbsFileSize = 1024 * 1024 * 5;

        @Option("MaxNbsLength")
        @Description("The maximum length of a NBS file in seconds")
        public static int maxNbsLength = 60 * 20; //TODO

        @Option("MaxMidiFileSize")
        @Description("The maximum file size for converting MIDI files to NBS in bytes")
        public static int maxMidiFileSize = 1024 * 1024 * 20;
    }

    @Section(name = "RateLimits")
    public static class RateLimits {
        @Option("UserMaxRequestsPerMinute")
        @Description("The maximum amount of user requests per minute")
        public static int userMaxRequestsPerMinute = 3;

        @Option("GuildMaxRequestsPerMinute")
        @Description("The maximum amount of guild requests per minute")
        public static int guildMaxRequestsPerMinute = 10;
    }

}
