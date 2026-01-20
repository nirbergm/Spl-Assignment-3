package bgu.spl.net.impl.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Database {
    private final ConcurrentHashMap<String, User> userMap;
    private final ConcurrentHashMap<Integer, User> connectionsIdMap;
    private final String sqlHost;
    private final int sqlPort;

    private Database() {
        userMap = new ConcurrentHashMap<>();
        connectionsIdMap = new ConcurrentHashMap<>();
        this.sqlHost = "127.0.0.1";
        this.sqlPort = 7778;
    }

    public static Database getInstance() {
        return Instance.instance;
    }

    private static class Instance {
        static Database instance = new Database();
    }

    private synchronized String executeSQL(String sql) {
        try (Socket socket = new Socket(sqlHost, sqlPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            out.print(sql + "\0");
            out.flush();
            
            StringBuilder response = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1) {
                if (ch == '\0') break;
                response.append((char) ch);
            }
            return response.toString();
            
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private String escapeSql(String str) {
        if (str == null) return "";
        return str.replace("'", "''");
    }

    public LoginStatus login(int connectionId, String username, String password) {
        if (connectionsIdMap.containsKey(connectionId)) {
            return LoginStatus.CLIENT_ALREADY_CONNECTED;
        }

        String query = "SELECT password FROM users WHERE username='" + username + "'";
        String result = executeSQL(query);

        if (result.startsWith("ERROR")) return LoginStatus.WRONG_PASSWORD;

        if (result.isEmpty()) {
            User newUser = new User(connectionId, username, password);
            String insert = String.format("INSERT INTO users (username, password, registration_date) VALUES ('%s', '%s', datetime('now'))", 
                            escapeSql(username), escapeSql(password));
            executeSQL(insert);
            
            userMap.put(username, newUser);
            newUser.login();
            connectionsIdMap.put(connectionId, newUser);
            
            logLogin(username);
            return LoginStatus.ADDED_NEW_USER;
        } else {
            String storedPass = result.trim();
            if (!storedPass.equals(password)) {
                return LoginStatus.WRONG_PASSWORD;
            }

            userMap.putIfAbsent(username, new User(connectionId, username, password));
            User user = userMap.get(username);

            synchronized (user) {
                if (user.isLoggedIn()) {
                    return LoginStatus.ALREADY_LOGGED_IN;
                }
                user.login();
                user.setConnectionId(connectionId);
                connectionsIdMap.put(connectionId, user);
                
                logLogin(username);
                return LoginStatus.LOGGED_IN_SUCCESSFULLY;
            }
        }
    }

    private void logLogin(String username) {
        executeSQL("INSERT INTO logins (username) VALUES ('" + escapeSql(username) + "')");
    }

    public void logout(int connectionId) {
        User user = connectionsIdMap.get(connectionId);
        if (user != null) {
            String sql = "UPDATE logins SET logout_time=CURRENT_TIMESTAMP WHERE username='" + user.name + "' AND logout_time IS NULL";
            executeSQL(sql);
            
            user.logout();
            connectionsIdMap.remove(connectionId);
        }
    }

    public void trackFileUpload(String username, String filename, String gameChannel) {
        if (filename == null) return;
        String sql = String.format("INSERT INTO files (username, filename, upload_time) VALUES ('%s', '%s', datetime('now'))",
                escapeSql(username), escapeSql(filename));
        executeSQL(sql);
    }

    public void printReport() {
        System.out.println(repeat("=", 80));
        System.out.println("SERVER REPORT - Generated at: " + java.time.LocalDateTime.now());
        
        System.out.println("\n1. REGISTERED USERS:");
        printSimpleResult(executeSQL("SELECT username, registration_date FROM users"), "User", "Date");

        System.out.println("\n2. LOGIN HISTORY:");
        printSimpleResult(executeSQL("SELECT username, login_time, logout_time FROM logins"), "User", "Login", "Logout");

        System.out.println("\n3. FILE UPLOADS:");
        printSimpleResult(executeSQL("SELECT username, filename, upload_time FROM files"), "User", "File", "Time");
        
        System.out.println(repeat("=", 80));
    }

    private void printSimpleResult(String rawResult, String... labels) {
        if (rawResult == null || rawResult.isEmpty() || rawResult.startsWith("ERROR")) {
            System.out.println("   (No data found)");
            return;
        }
        String[] rows = rawResult.split("\n");
        for (String row : rows) {
            String[] cols = row.split("\\|");
            System.out.print("   ");
            for (int i = 0; i < cols.length; i++) {
                String label = (i < labels.length) ? labels[i] : "Info";
                System.out.print(label + ": " + cols[i] + "\t");
            }
            System.out.println();
        }
    }

    private String repeat(String str, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) { sb.append(str); }
        return sb.toString();
    }
}