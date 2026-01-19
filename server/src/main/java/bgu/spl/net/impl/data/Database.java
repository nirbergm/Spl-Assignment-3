package bgu.spl.net.impl.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Database {

    private final ConcurrentHashMap<Integer, String> connectionsIdMap;
    
    private final String sqlHost;
    private final int sqlPort;

    private static class Instance {
        static final Database instance = new Database();
    }

    private Database() {
        connectionsIdMap = new ConcurrentHashMap<>();
        // פרטי החיבור לשרת הפייתון
        this.sqlHost = "127.0.0.1";
        this.sqlPort = 7778;
    }

    public static Database getInstance() {
        return Instance.instance;
    }

    /**
     * פונקציית העזר לתקשורת מול הפייתון
     */
    private synchronized String executeSQL(String sql) {
        try (Socket socket = new Socket(sqlHost, sqlPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            out.print(sql + "\0"); // שליחה עם Null Terminated
            out.flush();
            
            StringBuilder response = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1) {
                if (ch == '\0') break;
                response.append((char) ch);
            }
            
            return response.toString();
            
        } catch (Exception e) {
            System.err.println("SQL Error: " + e.getMessage());
            return "ERROR";
        }
    }

    /**
     * בדיקת לוגין מלאה מול ה-SQL
     */
    public boolean login(int connectionId, String username, String password) {
        // 1. בדיקה אם הקליינט הזה כבר מחובר
        if (connectionsIdMap.containsKey(connectionId)) {
            return false; // Already connected
        }

        // 2. בדיקה אם המשתמש כבר מחובר ממקום אחר (אופציונלי, תלוי דרישות)
        if (connectionsIdMap.containsValue(username)) {
             return false; // User already logged in
        }

        // 3. שליפת הסיסמה מה-SQL
        String query = "SELECT password FROM users WHERE username='" + username + "'";
        String result = executeSQL(query);

        if (result.startsWith("ERROR")) return false;

        if (result.isEmpty()) {
            // --- משתמש חדש (Registration) ---
            String insertUser = "INSERT INTO users (username, password) VALUES ('" + username + "', '" + password + "')";
            executeSQL(insertUser);
        } else {
            // --- משתמש קיים (Login) ---
            String storedPass = result.trim(); // ניקוי רווחים/ירידות שורה
            if (!storedPass.equals(password)) {
                return false; // Wrong password
            }
        }

        // 4. רישום מוצלח - עדכון ב-SQL ובזיכרון המקומי
        String logLogin = "INSERT INTO logins (username) VALUES ('" + username + "')";
        executeSQL(logLogin);

        connectionsIdMap.put(connectionId, username);
        return true;
    }

    /**
     * ניתוק משתמש
     */
    public void logout(int connectionId) {
        if (connectionsIdMap.containsKey(connectionId)) {
            String username = connectionsIdMap.get(connectionId);
            
            // עדכון זמן יציאה ב-SQL
            String sql = "UPDATE logins SET logout_time=CURRENT_TIMESTAMP " +
                         "WHERE username='" + username + "' AND logout_time IS NULL";
            executeSQL(sql);
            
            // מחיקה מהזיכרון המקומי
            connectionsIdMap.remove(connectionId);
        }
    }

    /**
     * תיעוד העלאת קובץ
     */
    public void trackFileUpload(String username, String filename) {
        if (filename == null || filename.isEmpty()) return;
        
        String sql = "INSERT INTO files (username, filename) VALUES ('" + username + "', '" + filename + "')";
        executeSQL(sql);
    }

    /**
     * הדפסת הדו"ח (מותאם לפורמט של שרת הפייתון שיצרנו)
     */
    public void printReport() {
        System.out.println("================================================================");
        System.out.println("SERVER REPORT - Generated at: " + java.time.LocalDateTime.now());
        System.out.println("================================================================");
        
        // 1. משתמשים
        System.out.println("\n1. REGISTERED USERS:");
        System.out.println("----------------------------------------------------------------");
        String usersRes = executeSQL("SELECT username, password FROM users"); 
        printFormattedResult(usersRes, "User");
        
        // 2. היסטוריית התחברויות
        System.out.println("\n2. LOGIN HISTORY:");
        System.out.println("----------------------------------------------------------------");
        String loginRes = executeSQL("SELECT username, login_time, logout_time FROM logins");
        printFormattedResult(loginRes, "User", "Login", "Logout");
        
        // 3. קבצים
        System.out.println("\n3. FILE UPLOADS:");
        System.out.println("----------------------------------------------------------------");
        String filesRes = executeSQL("SELECT username, filename, upload_time FROM files");
        printFormattedResult(filesRes, "User", "File", "Time");
        
        System.out.println("================================================================");
    }

    // פונקציית עזר לפרסור התשובה מהפייתון (Pipe separated)
    private void printFormattedResult(String rawResult, String... labels) {
        if (rawResult == null || rawResult.isEmpty() || rawResult.startsWith("ERROR")) {
            System.out.println("   (No data found)");
            return;
        }

        String[] rows = rawResult.split("\n");
        for (String row : rows) {
            String[] cols = row.split("\\|"); // פיצול לפי |
            System.out.print("   ");
            for (int i = 0; i < cols.length && i < labels.length; i++) {
                System.out.print(labels[i] + ": " + cols[i] + "\t");
            }
            // אם יש יותר עמודות מתוויות (למשל במקרה של לוגין)
            for (int i = labels.length; i < cols.length; i++) {
                 System.out.print(cols[i] + "\t");
            }
            System.out.println();
        }
    }
}