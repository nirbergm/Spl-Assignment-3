#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include <map>
#include <string>
// TODO: implement the STOMP protocol
class StompProtocol
{
    private: 
        bool shouldTerminate_ = false;
        bool isLoggedIn = false;
        std::map<int, std::string> receipts_; // mapping receipts-id's to a message about the matching command that it was sent
    public:
        void process(std::string& frame);

        std::string getHeaderVal(std::vector<std::string> lines,std::string header);

        std::string getBody(std::vector<std::string> lines);

        bool shouldTerminate();

        void processConnected(std::vector<std::string> lines);

        void processMessage(std::vector<std::string> lines);

        void processReceipt(std::vector<std::string> lines);

        void processError(std::vector<std::string> lines);

        bool shouldTerminate() const { return shouldTerminate_; }

        //--------------------//
        
        std::string buildJoinFrame(std::string destination, std::string id, std::string receiptId);

        std::string buildConnectFrame(std::string host, std::string login, std::string passcode);

        std::string buildUnsubscribeFrame(std::string subscriptionId, std::string receiptId);

        std::string buildDisconnectFrame(std::string receiptId);

        std::string buildSendFrame(const Event& event, std::string gameName);
};
