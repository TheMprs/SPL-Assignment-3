#pragma once
#include <fstream> // for file writing
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
        std::map<std::string, std::vector<Event>> userGames; // map of games and their events for the current user
        std::map<std::string, std::vector<Event>> allGames; // map of all games and their events

    public:

        std::string processClientInput(std::vector<std::string> words){
            if(words[0] == "login"){ // login
                    return handleLogin(words);
            }
            else if(words[0] == "join"){ // subscribe
                    return handleJoin(words);
            }
            else if(words[0] == "exit"){ // unsubscribe
                    return handleExit(words);
            }
            else if(words[0] == "report"){ // send
                    return handleReport(words);
            }
            else if(words[0] == "summary"){ // summary 
                    return handleSummary(words);
            }
            else if(words[0] == "logout"){ // logout 
                    return handleLogout();
            }
            else {
                return "Unknown command";
            }
        }

        // should log recieved frames and process them accordingly
        void processServerFrame(const std::string &frame){
            // check frame is a message frame
            if(frame.find("MESSAGE") != std::string::npos){
                // parse the frame to extract event details
                
                //find body in frame
                size_t body_start = frame.find("\n\n") + 2;
                std::string body = frame.substr(body_start);
                
                //generate event from body
                Event event = Event(body);
                // store the event in allGames map
                allGames[event.get_name()].push_back(event);
            }
        }

        //join frame
        std::string handleLogin(std::vector<std::string> words) {
            if (words.size() < 4) {
                std::cerr << "Error: login requires host:port, username and password" << std::endl;
                return;
            }
            
            std::string hostPort = words[1];
            std::string username = words[2];    
            std::string password = words[3];
            // Construct and send CONNECT frame
            std::string stompFrame = "CONNECT\n";
            stompFrame += "accept-version:1.2\n";
            stompFrame += "host:" + hostPort + "\n";
            stompFrame += "login:" + username + "\n";
            stompFrame += "passcode:" + password + "\n\n\0";

            //update current username
            user = username;
            return stompFrame;
        }

        //subscribe frame
        std::string handleJoin(std::vector<std::string> words) {
            if(words.size() < 2)
                std::cerr << "Error: join requires game_name" << std::endl;
            std::string stompFrame = "SUBSCRIBE\n";
            stompFrame += "destination: "+words[2];
            stompFrame += "id: "+subscriptionIdCounter;
            // assignemnt requires sending a reciept for every subscription
            stompFrame += "reciept: "+ std::to_string(recieptCounter) + "\n\n\0";            

            // map the topic to the subscription id
            channelIds[words[2]] = subscriptionIdCounter;
            // increment subscription id counter for next subscription
            subscriptionIdCounter+=1;
            return stompFrame;
        }

        //unsubscribe frame
        std::string handleExit(std::vector<std::string> words) {
            if(words.size() < 2)
                std::cerr << "Error: exit requires game_name" << std::endl;
            
            // get sub id based on channel name
            std::string channel = std::to_string(channelIds[words[1]]);
            
            // Construct and send UNSUBSCRIBE frame
            std::string stompFrame = "UNSUBSCRIBE\n";
            stompFrame += "id:" + channel + "\n";
            stompFrame += "receipt:" + words[1] + "\n\n\0";

            return stompFrame;
        }

        //send frames based on events file 
        std::string handleReport(std::vector<std::string> words) {
            if(words.size() < 2)
                std::cerr << "Error: exit requires {file}" << std::endl;
            
            //read the provided file, parse game name and events 
            names_and_events details = parseEventsFile(words[1]);
            std::string frames="";

            // Construct and send SEND frames
            for (Event& event : details.events) { // for each event in the file
                // in the createSendFrame function, we also add the event to the user's game events
                frames += createSendFrame(event);
            }
            return frames;
        }

        //write summary into {file}
        std::string handleSummary(std::vector<std::string> words) {
            if(words.size() < 4) {
                std::cout << "Error: exit requires {game_name}, {user}, {file}" << std::endl;
            }

            std::string game = words[1];
            std::string user = words[2];
            std::string file_path = words[3];

            // open file, trunc makes sure we overwrite current file
            std::ofstream file(file_path, std::ios::trunc);

            //write in the file the summary for the user and game
            file << writeSummary(user, game);
            file.close();
            return "";
        }

        std::string handleLogout() {
            std::string stompFrame = "DISCONNECT\n";
            stompFrame += "receipt:" + std::to_string(recieptCounter) + "\n\n\0";
            
            channelIds.clear(); // clear all subscriptions on logout
            subscriptionIdCounter=0; // reset subscription id counter
            recieptCounter=0; // reset receipt counter
            
            return stompFrame;
        }

        std::string createSendFrame(Event& event) {
            std::string stompFrame = "SEND\n";
            
            std::string gameName = event.get_team_a_name()+"_"+event.get_team_b_name();
            //construct channel name based on teams
            stompFrame += "destination: "+gameName+"\n";

            // Construct body
            std::string body;
            body += "user: " + user + "\n";
            body += "team a: " + event.get_team_a_name() + "\n";
            body += "team b: " + event.get_team_b_name() + "\n";
            body += "event name: " + event.get_name() + "\n";
            body += "time: " + std::to_string(event.get_time()) + "\n";
            body += "description: " + event.get_discription() + "\n";   
            
            stompFrame += body + "\0"; // Null character to indicate end of frame
            
            userGames[gameName].push_back(event); // add event to user's game events
            
            return stompFrame;
        }

        std::string writeSummary(std::string username, std::string game_name) {
            std::string sum="";
            // find all events for the user in the specified game
            std::vector<Event> events = allGames[username];
            for(Event event : events){
                // log only events from the specified game
                if(event.get_team_a_name()+"_"+event.get_team_b_name() == game_name)
                    sum+=event.get_discription();
            }
            return sum;
        }
    };