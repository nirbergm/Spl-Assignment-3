#include "../include/StompProtocol.h"

using namespace std;

bool StompProtocol::shouldTerminate() {
    return shouldTerminate_;
}

void StompProtocol::process(std::string& frame){

    std::stringstream ss(frame);
    vector<string> lines;
    string line;

    while (getline(ss, line, '\n')) {
        lines.push_back(line);
    }

    string command = lines[0];
    
    if (command == "CONNECTED") {
        processConnected(lines);
    } 
    else if (command == "ERROR") {
        processError(lines);
    } 
    else if (command == "MESSAGE") {
        processMessage(lines);
    } 
    else if (command == "RECEIPT") {
        processReceipt(lines);
    }

    
}

std::string StompProtocol::getHeaderVal(std::vector<std::string> lines,std::string header){
    if(lines.empty() || header.empty()) return "";
    
    for(int i = 1; i < lines.size(); i++){
        if(lines[i].empty()) break;

        size_t colonIndex = lines[i].find(':');
        if(colonIndex != string::npos){
            string currHeaderName = lines[i].substr(0,colonIndex);
            if(currHeaderName == header){
                return lines[i].substr(colonIndex + 1);
            }
        }
    }
    return "";
}

std::string StompProtocol::getBody(std::vector<std::string> lines){
    if(lines.empty()) return "";
    int bodyStartIndex = -1;
    for(int i = 1; i < lines.size(); i++){
        if(lines[i].empty()){
            bodyStartIndex = i + 1;
            break;
        }
    }
    //constructing the body text
    std::stringstream bodyStream;

    if (!lines.back().empty() && lines.back().back() == '\0') {
        lines.back().pop_back(); // deletes the null part of the string if its connected to the last char of the string
    }

    for (int j = bodyStartIndex; j < lines.size(); j++) {
        bodyStream << lines[j];
        if (j < lines.size() - 1) {
            bodyStream << "\n";
        }
    }
    return bodyStream.str();

}



void StompProtocol::processConnected(std::vector<std::string> lines){
    std::cout <<"Login successful" << std::endl;
    isLoggedIn = true;
}

void StompProtocol::processMessage(std::vector<std::string> lines){
    string destination = getHeaderVal(lines,"destination");
    string body = getBody(lines);

    std::cout << "Message from " << destination << ":\n" << body << std::endl;
}

void StompProtocol::processReceipt(std::vector<std::string> lines){

    string receipt = getHeaderVal(lines,"receipt-id");
    int receiptId = std::stoi(receipt); // from str to int
    if (receipts_.find(receiptId) != receipts_.end()){

        std::string msg = receipts_[receiptId];
        std::cout << msg << std::endl;

        receipts_.erase(receiptId); //cleaning the map
    }
}

void StompProtocol::processError(std::vector<std::string> lines){
    
    std::string errorMessage = getHeaderVal(lines, "message");
    std::string errorBody = getBody(lines);
    std::cout << "Error: " << errorMessage << std::endl;
    if (!errorBody.empty()) {
        std::cout << errorBody << std::endl;
    }
    //update flags
    shouldTerminate_ = true;
    isLoggedIn = false;

}

//-------//

std::string StompProtocol::buildJoinFrame(std::string destination, std::string id, std::string receiptId) {
    string frame = "";
    
    frame += "SUBSCRIBE\n";
    
    frame += "destination:/" + destination + "\n";
    frame += "id:" + id + "\n";
    frame += "receipt:" + receiptId + "\n";
    
    //empty line to seperate
    frame += "\n";
    
    //null for ending
    frame += '\0';
    
    return frame;
}

std::string StompProtocol::buildConnectFrame(std::string host, std::string login, std::string passcode) {
    std::string frame = "";
    frame += "CONNECT\n";
    frame += "accept-version:1.2\n";
    frame += "host:" + host + "\n";
    frame += "login:" + login + "\n";
    frame += "passcode:" + passcode + "\n";
    frame += "\n"; //empty line
    frame += '\0'; //null for ending
    return frame;
}


std::string StompProtocol::buildUnsubscribeFrame(std::string subscriptionId, std::string receiptId) {
    std::string frame = "";
    frame += "UNSUBSCRIBE\n";
    frame += "id:" + subscriptionId + "\n";
    frame += "receipt:" + receiptId + "\n";
    frame += "\n"; // empty line
    frame += '\0'; // null for ending
    return frame;
}

std::string StompProtocol::buildDisconnectFrame(std::string receiptId) {
    std::string frame = "";
    frame += "DISCONNECT\n";
    frame += "receipt:" + receiptId + "\n";
    frame += "\n"; // empty line
    frame += '\0'; //null for ending
    return frame;
}

std::string StompProtocol::mapToString(const std::map<std::string, std::string>& myMap) {
    std::string result = "";
    
    for (const auto& pair : myMap) {
        result += pair.first + ": " + pair.second + "\n";
    }
    
    return result;
}

std::string StompProtocol::buildSendFrame(const Event& event, std::string gameName, std::string currentUsername) {
    std::string frame = "";
    //send command and headers
    frame += "SEND\n";
    frame += "destination:/" + gameName + "\n"; 
    frame += "\n"; //empty line to seperate body and headers

    //body 
    frame += "user: " + currentUsername + "\n";
    frame += "team a: " + event.get_team_a_name() + "\n";
    frame += "team b: " + event.get_team_b_name() + "\n";
    frame += "event name: " + event.get_name() + "\n";
    
    frame += "time: " + std::to_string(event.get_time()) + "\n";
    
    frame += "general game updates:\n";
    frame += mapToString(event.get_game_updates());
    
    frame += "team a updates:\n";
    frame += mapToString(event.get_team_a_updates());
    
    frame += "team b updates:\n";
    frame += mapToString(event.get_team_b_updates());
    
    frame += "description:\n";
    frame += event.get_discription() + "\n"; 
    
    //to end with null:
    frame += '\0';
    
    return frame;
}