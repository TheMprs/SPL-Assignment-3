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
    loggedIn(false), // user login status
    shouldTerminate(false),
    receiptToMessage(),
    logoutReceiptId(-1) // -1 means we are NOT waiting for logout
    
{

}

//private helper functions
std::string StompProtocol::createSendFrame(Event& event, const std::string& filename) {
    std::string stompFrame = "SEND\n";
    
    std::string gameName = event.get_team_a_name()+"_"+event.get_team_b_name(); //construct channel name based on teams
    stompFrame += "destination:"+gameName+"\n";
    stompFrame += "filename:" + filename + "\n"; //add filename header
    stompFrame += "\n"; // blank line before body

    // Construct body
    std::string body;
    body += "user:" + user + "\n";
    body += "team a:" + event.get_team_a_name() + "\n";
    body += "team b:" + event.get_team_b_name() + "\n";
    body += "event name:" + event.get_name() + "\n";
    body += "time:" + std::to_string(event.get_time()) + "\n";
    body += "description:" + event.get_discription() + "\n";   
    
    stompFrame += body;
    
    userGames[gameName].push_back(event); // add event to user's game events
    allGames[user].push_back(event); // add event to all games under the user's name

    return stompFrame;
}

std::string StompProtocol::writeSummary(std::string username, std::string game_name) {
    std::string sum="";
    // check if user has any events    
    
    if (allGames.find(username) == allGames.end()) {
        return "No events found for user " + username + ".\n";
    }
    
    // find all events for the user in the specified game
    std::vector<Event> events = allGames[username];
    for(Event event : events){
        std::string curr_gamename = event.get_team_a_name()+"_"+event.get_team_b_name();
        
        // log only events from the specified game
        if(curr_gamename == game_name) {
            // summary format is: 
            // time - event name:
            // description
            sum += std::to_string(event.get_time()) + " - " + event.get_name() + ":\n";
            sum+=event.get_discription()+"\n\n";
        }
    }
    if (sum.empty()) {
        return "User has events, but none for game: " + game_name + "\n";
    }
    return sum;
}
    

std::string StompProtocol::processClientInput(std::vector<std::string> words){        
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
    if (frame.find("CONNECTED") == 0) {
        loggedIn = true;
        std::cout << "Login successful" << std::endl;
    }
    
    else if(frame.find("MESSAGE") != std::string::npos){
        // parse the frame to extract event details
        
        //find body in frame
        size_t body_start = frame.find("\n\n") + 2;
        std::string body = frame.substr(body_start);
        
        std::string remove_bad_chars = "";
        for (char c : body) {
            if (c != '\r') { // remove return characters
                remove_bad_chars += c;
            }
        }
        
        body = remove_bad_chars;

        //generate event from body
        Event event = Event(body);
        
        // find the username in the body string
        std::string owner = "";
        size_t userPos = body.find("user:");
        if (userPos != std::string::npos) {
            size_t start = userPos + 5; // ignore "user:"
            size_t end = body.find('\n', start);
            if (end != std::string::npos) {
                owner = body.substr(start, end - start);
                
            }
        }

        // Remove spaces
        while (!owner.empty() && owner.front() == ' ') {
            owner.erase(0, 1);
        }
        
        // Remove back spaces 
        while (!owner.empty() && (owner.back() == ' ' || owner.back() == '\n')) {
            owner.pop_back();
        }

        // store using the username as the key
        if (!owner.empty()) { 
            allGames[owner].push_back(event);
        }
    }

    else if (frame.find("RECEIPT") == 0) {
    std::string rIdStr = getHeaderValue(frame, "receipt-id");
    if (!rIdStr.empty()) {
        int rId = std::stoi(rIdStr);
        
        
        if (logoutReceiptId != -1 && rId == logoutReceiptId) {
            std::cout << "Logout confirmed" << std::endl;
            terminate();
        }
        // Check if we have a stored message for this receipt ID
        else if (receiptToMessage.count(rId)) {
            std::cout << receiptToMessage[rId] << std::endl;
            receiptToMessage.erase(rId); // Remove after printing
            }
        }
    }
    // If the server sends an ERROR, print it and close the connection
    else if (frame.find("ERROR") == 0) {
        // Extract the short summary from the 'message' header
        std::string summary = getHeaderValue(frame, "message");
        
        // Extract the detailed explanation from the body (after the double newline)
        size_t body_start = frame.find("\n\n");
        std::string details = (body_start != std::string::npos) ? frame.substr(body_start + 2) : "";

       // check if it's a logic error (as opposed to technial error)
        if (summary.find("Wrong password") != std::string::npos) {
            std::cout << "Wrong password" << std::endl;
        } 
        else if (summary.find("User already logged in") != std::string::npos) {
            std::cout << "User already logged in" << std::endl;
        }
        else {
            //Technical error. Print error for debug
            std::cout << "Error from server: " << summary << std::endl;
            if (!details.empty()) {
                std::cout << "Details: " << details << std::endl;
            }
        }

        terminate(); // Error frame means the server will close the connection anyway
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
    stompFrame += "passcode:" + password + "\n";
    stompFrame += "\n";
    
   
    //update current username
    user = username;


    return stompFrame;
}

//subscribe frame
std::string StompProtocol::handleJoin(std::vector<std::string> words) {
    if(words.size() < 2)
        std::cerr << "Error: join requires game_name" << std::endl;
    
    std::string game_name = words[1];

    int currentReceipt = recieptCounter++;
    // Store what to print when the server confirms this request
    receiptToMessage[currentReceipt] = "Joined channel " + game_name;
    
    std::string stompFrame = "SUBSCRIBE\n";
    stompFrame += "destination:"+game_name + "\n";
    stompFrame += "id:"+std::to_string(subscriptionIdCounter) + "\n";
    stompFrame += "receipt:" + std::to_string(currentReceipt) + "\n\n";
    // map the topic to the subscription id
    channelIds[game_name] = subscriptionIdCounter;
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
    int currentReceipt = recieptCounter++;
    
    // Store the message to print when the server sends the RECEIPT frame back
    receiptToMessage[currentReceipt] = "Exited channel " + game_name;

    // Get sub id based on channel name from our local map
    std::string subId = std::to_string(channelIds[game_name]);
    
    // Construct and send UNSUBSCRIBE frame
    std::string stompFrame = "UNSUBSCRIBE\n";
    stompFrame += "id:" + subId + "\n";

    // Use the receipt ID we just created and stored
    stompFrame += "receipt:" + std::to_string(currentReceipt) + "\n\n";

    return stompFrame;
}

//send frames based on events file 
std::string StompProtocol::handleReport(std::vector<std::string> words) {
    if(words.size() < 2)
        std::cerr << "Error: report requires {file}" << std::endl;
    
    std::string file_path = words[1];
    //read the provided file, parse game name and events 
    names_and_events details = parseEventsFile(file_path);
    std::string frames="";

    // Construct and send SEND frames
    for (Event& event : details.events) { // for each event in the file
        // in the createSendFrame function, we also add the event to the user's game events
        frames += createSendFrame(event,file_path);
        frames += '\0'; // null character to indicate end of frame

    }

    if(!frames.empty()){
        frames.pop_back(); // remove last null character
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
    logoutReceiptId = recieptCounter;
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

bool StompProtocol::isTerminated() {
    return shouldTerminate.load();
}

void StompProtocol::terminate() {
    shouldTerminate.store(true); //Define client state after logout
}

// helper function to get value from a stomp header (like receipt-id:1)
std::string StompProtocol::getHeaderValue(const std::string& frame, const std::string& key) {
    std::string searchKey = key + ":";
    size_t pos = frame.find(searchKey);
    if (pos == std::string::npos) return "";
    
    size_t start = pos + searchKey.length();
    size_t end = frame.find('\n', start);
    return frame.substr(start, end - start);
}

