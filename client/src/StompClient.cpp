#include <stdlib.h>
#include "../include/ConnectionHandler.h"
#include <thread>
#include <mutex>
#include <vector>
#include <iostream>
#include <sstream>

using namespace std;

// נשתמש בזה כדי לסנכרן את הסגירה של התוכנית
bool shouldTerminate = false;

// פונקציה שתרוץ ב-Thread הנפרד ותקשיב לתשובות מהשרת
void socketListener(ConnectionHandler* handler) {
    while (!shouldTerminate) {
        string answer;
        
        if (!handler->getLine(answer)) {
            std::cout << "Disconnected. Exiting...\n" << std::endl;
            shouldTerminate = true;
            break;
        }

        // הסרת התו '\n' בסוף אם קיים
        int len = answer.length();
        answer.resize(len - 1);
        
        // הדפסת התשובה מהשרת למסך
        std::cout << answer << std::endl;

        // אם קיבלנו הודעת שגיאה או ניתוק מהשרת, אולי נרצה לסגור
        if (answer == "CONNECTED") {
            std::cout << "Login successful" << std::endl;
        }
    }
}

int main(int argc, char *argv[]) {
    // הלקוח מתחיל בלי חיבור. הוא מחכה לפקודת login
    
    ConnectionHandler* connectionHandler = nullptr;
    thread* listenerThread = nullptr;

    while (!shouldTerminate) {
        const short bufsize = 1024;
        char buf[bufsize];
        
        // קריאה מהמקלדת
        cin.getline(buf, bufsize);
        string line(buf);
        
        vector<string> segments;
        stringstream ss(line);
        string segment;
        while(getline(ss, segment, ' ')) {
            segments.push_back(segment);
        }

        if (segments.empty()) continue;
        string command = segments[0];

        // --- טיפול בפקודת LOGIN (יצירת חיבור) ---
        if (command == "login") {
            if (connectionHandler != nullptr) {
                cout << "The client is already logged in, log out before trying again" << endl;
                continue;
            }
            if (segments.size() < 4) {
                cout << "Usage: login {host:port} {username} {password}" << endl;
                continue;
            }

            // פירסור ה-host:port
            string hostPort = segments[1];
            size_t colonPos = hostPort.find(':');
            if (colonPos == string::npos) {
                cout << "Invalid host:port format" << endl;
                continue;
            }
            string host = hostPort.substr(0, colonPos);
            short port = stoi(hostPort.substr(colonPos + 1));

            // יצירת ה-ConnectionHandler
            connectionHandler = new ConnectionHandler(host, port);
            if (!connectionHandler->connect()) {
                cerr << "Cannot connect to " << host << ":" << port << endl;
                delete connectionHandler;
                connectionHandler = nullptr;
                continue;
            }

            // הפעלת ה-Thread שמקשיב לשרת
            listenerThread = new thread(socketListener, connectionHandler);

            // שליחת פריימים של CONNECT (זה יטופל בהמשך ע"י StompProtocol, כרגע הארד-קוד לבדיקה)
            string frame = "CONNECT\naccept-version:1.2\nhost:stomp.server\nlogin:" + segments[2] + "\npasscode:" + segments[3] + "\n\n\0";
            // שים לב: ב-ConnectionHandler צריך לשלוח את ה-Null Byte
            // כרגע נשתמש ב-sendLine רגיל לבדיקה ראשונית
             if (!connectionHandler->sendLine(frame)) {
                std::cout << "Disconnected. Exiting...\n" << std::endl;
                shouldTerminate = true;
            }

        }
        
        // --- טיפול בפקודת LOGOUT ---
        else if (command == "logout") {
            if (connectionHandler == nullptr) {
                cout << "Not connected." << endl;
                continue;
            }
            // כאן נצטרך לשלוח DISCONNECT ולחכות ל-RECEIPT
            // בינתיים רק נסגור
            connectionHandler->close();
            shouldTerminate = true;
        }

        // --- פקודות אחרות (join, add, report...) ---
        else if (connectionHandler != nullptr) {
            // כאן נצטרך להשתמש ב-StompProtocol כדי להמיר פקודה ל-Frame
            // connectionHandler->sendLine(protocol.process(line));
            cout << "Command not implemented yet in client stub" << endl;
        } 
        else {
            cout << "Please login first" << endl;
        }
    }

    // ניקוי משאבים ביציאה
    if (listenerThread != nullptr) {
        listenerThread->join();
        delete listenerThread;
    }
    if (connectionHandler != nullptr) {
        delete connectionHandler;
    }

    return 0;
}