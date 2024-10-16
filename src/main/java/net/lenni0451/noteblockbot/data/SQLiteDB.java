package net.lenni0451.noteblockbot.data;

import lombok.SneakyThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public class SQLiteDB {

    public static final String UPLOAD_NOTIFICATION = "NoteBlockWorldUploadNotificationChannels";
    public static final String MP3_CONVERSIONS = "Mp3Conversions";
    private static final Map<String, String> TABLES = Map.of(
            UPLOAD_NOTIFICATION, "(\"GuildId\" INTEGER NOT NULL UNIQUE, \"ChannelId\" INTEGER NOT NULL, PRIMARY KEY(\"GuildId\"))",
            MP3_CONVERSIONS, "(\"id\" INTEGER, \"GuildId\" INTEGER NOT NULL, \"UserId\" INTEGER NOT NULL, \"UserName\" TEXT NOT NULL, \"Date\" TEXT NOT NULL, \"Source\" INTEGER NOT NULL, \"FileName\" TEXT NOT NULL, \"FileSize\" INTEGER NOT NULL, \"FileHash\" TEXT NOT NULL, \"ConversionDuration\" INTEGER NOT NULL, PRIMARY KEY(\"id\" AUTOINCREMENT))"
    );

    private final Connection connection;

    public SQLiteDB(final String path) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        this.createTables();
    }

    public Connection getConnection() {
        return this.connection;
    }

    public PreparedStatement prepare(final String sql) throws SQLException {
        return this.connection.prepareStatement(sql);
    }

    @SneakyThrows
    private void createTables() {
        for (Map.Entry<String, String> entry : TABLES.entrySet()) {
            try (PreparedStatement statement = this.connection.prepareStatement("CREATE TABLE IF NOT EXISTS \"" + entry.getKey() + "\" " + entry.getValue())) {
                statement.execute();
            }
        }
    }

}