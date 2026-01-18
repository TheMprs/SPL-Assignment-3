#include <fstream> // for file writing
#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include "../include/StompProtocol.h"

// STOMP protocol constructor
StompProtocol::StompProtocol():
    channelIds(), // topic -> subscription id
    subscriptionIdCounter(0), // internal counter for subscription ids
    recieptCounter(0), // internal counter for reciepts
    user(""), // current username
    userGames(), // map of games and their events for the current user
    allGames(), // map of all games and their events
    loggedIn(false) // user login status
{

}
//private helper functions
std::string StompProtocol::createSendFrame(Event& event, const std::string& filename) {
    std::string stompFrame = "SEND\n";
    
    std::string gameName = event.get_team_a_name()+"_"+event.get_team_b_name();
    //construct channel name based on teams
    stompFrame += "destination:"+gameName+"\n\n";

    // Construct body
    std::string body;
    body += "user:" + user + "\n";
    body += "team a:" + event.get_team_a_name() + "\n";
    body += "team b:" + event.get_team_b_name() + "\n";
    body += "event name:" + event.get_name() + "\n";
    body += "time:" + std::to_string(event.get_time()) + "\n";
    body += "description:" + event.get_discription() + "\n";   
    
    stompFrame += body;
    
    stompFrame += "filename:" + filename + "\n";

    userGames[gameName].push_back(event); // add event to user's game events
    
    return stompFrame;
}

std::string StompProtocol::writeSummary(std::string username, std::string game_name) {
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
    

std::string StompProtocol::processClientInput(std::vector<std::string> words){
            
            std::cout<<"[DEBUG-stmp-prtcl(cpp)] Processing client input: " << words[0] << std::endl;
            if(words[0] == "login"){ // login
                return handleLogin(words);
            }
            if(words[0] == "join"){ // subscribe
                return handleJoin(words);
            }
            if(words[0] == "exit"){ // unsubscribe
                return handleExit(words);
            }
            if(words[0] == "report"){ // send
                return handleReport(words);
            }
            if(words[0] == "summary"){ // summary 
                return handleSummary(words);
            }
            if(words[0] == "logout"){ // logout 
                return handleLogout();
            }
            else {
                return "Unknown command";
            }
        }

// should log recieved frames and process them accordingly
void StompProtocol::processServerFrame(const std::string &frame){
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
std::string StompProtocol::handleLogin(std::vector<std::string> words) {
    if (words.size() < 4) {
        std::cerr << "Error: login requires host:port, username and password" << std::endl;
        return "";
    }
    
    std::string hostPort = words[1];
    std::string username = words[2];    
    std::string password = words[3];
    // Construct and send CONNECT frame
    std::string stompFrame = "CONNECT\n";
    stompFrame += "accept-version:1.2\n";
    stompFrame += "host:" + hostPort + "\n";
    stompFrame += "login:" + username + "\n";
    stompFrame += "passcode:" + password + "\n\n";
    
    //update login status
    loggedIn = true;
    //update current username
    user = username;
    return stompFrame;
}

//subscribe frame
std::string StompProtocol::handleJoin(std::vector<std::string> words) {
    if(words.size() < 2)
        std::cerr << "Error: join requires game_name" << std::endl;
    
    std::string game_name = words[1];
    
    std::string stompFrame = "SUBSCRIBE\n";
    stompFrame += "destination:"+game_name + "\n";
    stompFrame += "id:"+std::to_string(subscriptionIdCounter) + "\n";
    // assignemnt requires sending a reciept for every subscription
    stompFrame += "receipt:"+ std::to_string(recieptCounter) + "\n\n";            

    // map the topic to the subscription id
    channelIds[game_name] = subscriptionIdCounter;
    recieptCounter+=1; // increment reciept counter for next reciept
    subscriptionIdCounter+=1; // increment subscription id counter for next subscription
    return stompFrame;
}

//unsubscribe frame
std::string StompProtocol::handleExit(std::vector<std::string> words) {
    if(words.size() < 2) {
        std::cerr << "Error: exit requires game_name" << std::endl;
        return "";
    }
    std::string game_name = words[1];

    // get sub id based on channel name
    std::string subId = std::to_string(channelIds[game_name]);
    
    // Construct and send UNSUBSCRIBE frame
    std::string stompFrame = "UNSUBSCRIBE\n";
    stompFrame += "id:" + subId + "\n";
    stompFrame += "receipt:" + std::to_string(recieptCounter) + "\n\n";

    recieptCounter+=1; // increment reciept counter for next reciept

    return stompFrame;
}

//send frames based on events file 
std::string StompProtocol::handleReport(std::vector<std::string> words) {
    if(words.size() < 2)
        std::cerr << "Error: exit requires {file}" << std::endl;
    
    std::string file_path = words[1];
    //read the provided file, parse game name and events 
    names_and_events details = parseEventsFile(file_path);
    std::string frames="";

    // Construct and send SEND frames
    for (Event& event : details.events) { // for each event in the file
        // in the createSendFrame function, we also add the event to the user's game events
        frames += createSendFrame(event,file_path);
    }
    return frames;
}

//write summary into {file}
std::string StompProtocol::handleSummary(std::vector<std::string> words) {
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

std::string StompProtocol::handleLogout() {
    std::string stompFrame = "DISCONNECT\n";
    stompFrame += "receipt:" + std::to_string(recieptCounter) + "\n\n";
    
    channelIds.clear(); // clear all subscriptions on logout
    subscriptionIdCounter=0; // reset subscription id counter
    recieptCounter=0; // reset receipt counter
    
    return stompFrame;
}

bool StompProtocol::isLoggedIn() {
    return loggedIn;
}