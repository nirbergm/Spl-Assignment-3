#include <stdlib.h>
#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include <thread>
#include <iostream>
#include <vector>
#include <sstream>
#include <map>
#include <string>

using namespace std;

class StompProtocol {
private:
    int subscriptionIdCounter;
    int receiptIdCounter;
    std::map<std::string, int> topicToSubId; 
    std::map<int, std::string> receiptIdToAction;

    string createConnectFrame(const string& host, const string& login, const string& passcode) {
        return "CONNECT\naccept-version:1.2\nhost:" + host + "\nlogin:" + login + "\npasscode:" + passcode + "\n\n";
    }

    string createSubscribeFrame(const string& topic, int id, int receipt) {
        return "SUBSCRIBE\ndestination:" + topic + "\nid:" + to_string(id) + "\nreceipt:" + to_string(receipt) + "\n\n";
    }

    string createUnsubscribeFrame(int id, int receipt) {
        return "UNSUBSCRIBE\nid:" + to_string(id) + "\nreceipt:" + to_string(receipt) + "\n\n";
    }

    string createSendFrame(const string& topic, const string& body, const string& filename = "") {
        string frame = "SEND\ndestination:" + topic + "\n";
        if (!filename.empty()) {
            frame += "file:" + filename + "\n";
        }
        frame += "\n" + body + "\n";
        return frame;
    }

    string createDisconnectFrame(int receipt) {
        return "DISCONNECT\nreceipt:" + to_string(receipt) + "\n\n";
    }

public:
    StompProtocol() : subscriptionIdCounter(0), receiptIdCounter(0), topicToSubId(), receiptIdToAction() {}

    vector<string> processFromKeyboard(const std::string& input) {
        vector<string> framesToSend;
        stringstream ss(input);
        string command;
        ss >> command;

        if (command == "login") {
            string hostPort, username, password;
            ss >> hostPort >> username >> password;
            framesToSend.push_back(createConnectFrame(hostPort, username, password));
        }
        else if (command == "join") {
            string game_name;
            ss >> game_name;
            if (topicToSubId.count(game_name)) {
                cout << "Already subscribed to " << game_name << endl;
                return framesToSend;
            }
            int subId = ++subscriptionIdCounter;
            int receiptId = ++receiptIdCounter;
            topicToSubId[game_name] = subId;
            receiptIdToAction[receiptId] = "Joined channel " + game_name;
            framesToSend.push_back(createSubscribeFrame(game_name, subId, receiptId));
        }
        else if (command == "exit") {
            string game_name;
            ss >> game_name;
            if (!topicToSubId.count(game_name)) {
                cout << "Not subscribed to " << game_name << endl;
                return framesToSend;
            }
            int subId = topicToSubId[game_name];
            int receiptId = ++receiptIdCounter;
            topicToSubId.erase(game_name);
            receiptIdToAction[receiptId] = "Exited channel " + game_name;
            framesToSend.push_back(createUnsubscribeFrame(subId, receiptId));
        }
        else if (command == "logout") {
            int receiptId = ++receiptIdCounter;
            receiptIdToAction[receiptId] = "DISCONNECT";
            framesToSend.push_back(createDisconnectFrame(receiptId));
        }
        else if (command == "report") {
            string file_path;
            ss >> file_path;
            names_and_events data = parseEventsFile(file_path);
            string game_name = data.team_a_name + "_" + data.team_b_name;

            if (topicToSubId.count(game_name)) {
                for (const Event& event : data.events) {
                    string body = "user:" + data.team_a_name + "\n" + 
                                  "team a:" + data.team_a_name + "\n" + 
                                  "team b:" + data.team_b_name + "\n" + 
                                  "event name:" + event.get_name() + "\n" + 
                                  "time:" + to_string(event.get_time()) + "\n" + 
                                  "general game updates:\n";
                    
                    for (auto const& pair : event.get_game_updates()) {
                        body += pair.first + ":" + pair.second + "\n";
                    }
                    body += "team a updates:\n";
                    for (auto const& pair : event.get_team_a_updates()) {
                        body += pair.first + ":" + pair.second + "\n";
                    }
                    body += "team b updates:\n";
                    for (auto const& pair : event.get_team_b_updates()) {
                        body += pair.first + ":" + pair.second + "\n";
                    }
                    body += "description:\n" + event.get_discription();
                    
                    framesToSend.push_back(createSendFrame(game_name, body, file_path));
                }
            } 
        }
        return framesToSend;
    }

    bool processFromServer(const std::string& frame) {
        stringstream ss(frame);
        string command;
        ss >> command;

        if (command == "CONNECTED") {
            cout << "Login successful" << endl;
        }
        else if (command == "ERROR") {
            cout << frame << endl;
        }
        else if (command == "MESSAGE") {
             size_t bodyPos = frame.find("\n\n");
             if (bodyPos != string::npos) cout << frame.substr(bodyPos + 2) << endl;
             else cout << frame << endl;
        }
        else if (command == "RECEIPT") {
            size_t idPos = frame.find("receipt-id:");
            if (idPos != string::npos) {
                int start = idPos + 11;
                int end = frame.find('\n', start);
                string idStr = frame.substr(start, end - start);
                try {
                    int id = stoi(idStr);
                    if (receiptIdToAction.count(id)) {
                        string action = receiptIdToAction[id];
                        if (action == "DISCONNECT") {
                            cout << "Disconnected." << endl;
                            return false; 
                        }
                        cout << action << endl;
                        receiptIdToAction.erase(id);
                    }
                } catch (...) {}
            }
        }
        return true;
    }
};

volatile bool isConnected = false; 

void socketListener(ConnectionHandler* handler, StompProtocol& protocol) {
    while (isConnected) {
        string answer;
        if (!handler->getFrameAscii(answer, '\0')) {
            cout << "Disconnected from server." << endl;
            isConnected = false;
            break;
        }
        
        if (!protocol.processFromServer(answer)) {
            isConnected = false;
            break;
        }
    }
}

int main(int argc, char *argv[]) {
    ConnectionHandler* connectionHandler = nullptr;
    thread* listenerThread = nullptr;
    StompProtocol protocol;

    const short bufsize = 1024;
    char buf[bufsize];

    while (true) {
        cin.getline(buf, bufsize);
        string line(buf);
        if (line.empty()) continue;

        stringstream ss(line);
        string command;
        ss >> command;

        if (command == "login") {
            if (connectionHandler != nullptr) {
                if (isConnected) {
                    cout << "The client is already logged in, log out before trying again" << endl;
                    continue;
                } else {
                    if (listenerThread && listenerThread->joinable()) {
                        listenerThread->join();
                        delete listenerThread;
                        listenerThread = nullptr;
                    }
                    delete connectionHandler;
                    connectionHandler = nullptr;
                }
            }

            string hostPort;
            ss >> hostPort;
            size_t colonPos = hostPort.find(':');
            if (colonPos == string::npos) {
                cout << "Usage: login {host:port} {username} {password}" << endl;
                continue;
            }
            string host = hostPort.substr(0, colonPos);
            short port = (short)stoi(hostPort.substr(colonPos + 1));

            connectionHandler = new ConnectionHandler(host, port);
            if (!connectionHandler->connect()) {
                cerr << "Could not connect to " << host << ":" << port << endl;
                delete connectionHandler;
                connectionHandler = nullptr;
                continue;
            }

            isConnected = true;
            listenerThread = new thread(socketListener, connectionHandler, ref(protocol));
        }
        
        if (command == "quit") {
             break; 
        }

        vector<string> frames = protocol.processFromKeyboard(line);
        for (const string& frame : frames) {
            if (connectionHandler != nullptr && isConnected) {
                if (!connectionHandler->sendFrameAscii(frame, '\0')) {
                    cout << "Error sending message to server." << endl;
                    isConnected = false; 
                    break;
                }
            } else if (command != "login") {
                 cout << "Not connected. Please login first." << endl;
            }
        }
    }

    if (connectionHandler != nullptr) {
        connectionHandler->close();
        isConnected = false;
    }
    if (listenerThread != nullptr) {
        if (listenerThread->joinable()) listenerThread->join();
        delete listenerThread;
    }
    if (connectionHandler != nullptr) delete connectionHandler;

    return 0;
}