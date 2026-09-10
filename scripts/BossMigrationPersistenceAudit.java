import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.LinkedHashMap;

/** Read-only checkpoint evidence. Never prints player rows or credentials, or writes to PostgreSQL. */
public class BossMigrationPersistenceAudit {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path expected = Path.of("C:/Users/norma/Documents/Codex/2026-08-10/lets-work-on-the-openrune-server/work/OpenRune-Server");
        if (!root.equals(expected)) throw new IllegalStateException("Not the canonical workspace");
        var pid = Files.readAllLines(root.resolve(".data/postgres/postmaster.pid"));
        if (!Path.of(pid.get(1)).toAbsolutePath().normalize().equals(root.resolve(".data/postgres")))
            throw new IllegalStateException("Unexpected PostgreSQL authority");
        int port = Integer.parseInt(pid.get(3));
        try (var connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + port + "/postgres", "postgres", "")) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            var queries = new LinkedHashMap<String, String>();
            queries.put("inventory-containers", "SELECT character_id, inv FROM inventories ORDER BY character_id, inv");
            queries.put("inventory-items-including-banks", "SELECT character_id, inv, slot, obj, count, vars FROM inventory_objs ORDER BY character_id, inv, slot");
            queries.put("realm-spawn-respawn", "SELECT realm_id, spawn_coord, respawn_coord FROM realms ORDER BY realm_id");
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                for (var entry : queries.entrySet()) {
                    var digest = MessageDigest.getInstance("SHA-256");
                    long rows = 0;
                    try (var result = statement.executeQuery("SELECT row_to_json(t)::text FROM (" + entry.getValue() + ") t")) {
                        while (result.next()) {
                            digest.update(result.getString(1).getBytes(StandardCharsets.UTF_8));
                            digest.update((byte) '\n');
                            rows++;
                        }
                    }
                    System.out.println(entry.getKey() + " rows=" + rows + " sha256=" + HexFormat.of().formatHex(digest.digest()));
                }
            }
            connection.rollback();
        }
    }
}
