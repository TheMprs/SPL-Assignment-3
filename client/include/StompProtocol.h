#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h"

// TODO: implement the STOMP protocol
class StompProtocol
{
    private:
        std::map<std::string, int> channelIds; // topic -> subscription id 
        int subscriptionIdCounter=0; // internal counter for subscription ids
        int recieptCounter=0; // internal counter for reciepts
        std::string user; // current username
        // map of games and their events for the current user
        std::map<std::string, std::vector<Event>> userGames; 
        // map of all games and their events
        std::map<std::string, std::vector<Event>> allGames; 

        bool loggedIn = false; // user login status

        std::string createSendFrame(Event& event, const std::string& filename);
        std::string writeSummary(std::string username, std::string game_name);
            
    public:
        StompProtocol();
        void processServerFrame(const std::string &frame);
        std::string processClientInput(std::vector<std::string> words);
        std::string handleLogin(std::vector<std::string> words);
        std::string handleJoin(std::vector<std::string> words);
        std::string handleExit(std::vector<std::string> words);
        std::string handleReport(std::vector<std::string> words);
        std::string handleSummary(std::vector<std::string> words);
        std::string handleLogout();
        bool isLoggedIn();
            
};

