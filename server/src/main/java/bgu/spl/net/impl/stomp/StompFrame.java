package bgu.spl.net.impl.stomp;
import java.util.HashMap;
import java.util.Map;

public class StompFrame {

    private String command;
    private Map<String, String> headers;
    private String body;
    private String error; //if not null, frame is invalid 

    public StompFrame(String command, Map<String, String> headers, String body) {
        this.command = command;
        this.headers = headers;
        this.body = body;
        this.error = null;
    }

    //parsing constructor
    public StompFrame(String message){
        this.headers = new HashMap<>();
        this.body = "";
        
        //check for null message
        if(message == null) {
            this.error = "Empty message";
            return;
        }
        
        this.error = null;

        //split message into lines
        String[] lines = message.split("\n");

        //first line is command
        this.command = lines[0].trim();
        //parse headers
        int i = 1;
        while(i < lines.length && !lines[i].isEmpty()) { //stop when we reach the body/end of headers
            //split header into key and value
            String[] header = lines[i].split(":",2);
            //ensure header has both key and value
            if(header.length == 2) {  
                //insert parameter and value into headers map
                headers.put(header[0], header[1]); 
            }
            i++; 
        }
        //concatenate body lines
        for(int j=i+1; j<lines.length-1; j++){ // the last line should be \u0000
            this.body += lines[j]+"\n";
        }
        this.body += lines[lines.length-1]; //append last line (with \u0000)
    }

    public String getCommand() {
        return command;
    }

    //flag to indicate if receipt is requested
    public boolean receiptRequested(){
        if(headers.containsKey("receipt")){
            return true;
        }
        return false;
    }

    public String getBody() {
        return body;
    }
    
    public String getHeader(String key) {
        return headers.get(key);
    }

    public String getError() {
        return error;
    }

    public String toString(){
        /**frame format should be:
        * command
        * header1:value1
        * header2:value2
        * ... (all headers)
        * 
        * body
        * \u0000
        */
        String frame = command+"\n";
        
        for(Map.Entry<String, String> entry : headers.entrySet()){
            frame+=entry.getKey()+":"+entry.getValue()+"\n";
        }
        
        frame += "\n" + 
                body + "\n"; // we dont add the null char here bc the encoder will add it
        return frame;
    }

    //automatic error frame generation based on stored error message
    public StompFrame generateErrorFrame(){
        return generateErrorFrame(getError());
    }

    //manual error frame generation with custom message
    public StompFrame generateErrorFrame(String errorMessage){
        Map<String, String> errHeaders = new HashMap<>();
        errHeaders.put("message", "malformed frame received");
        
        // Remove null byte to avoid terminating error frame early
        String originalBody = this.toString().replace("\u0000", ""); 

        //wrap original frame and error message in the body
        String errBody = "The message:\n-----\n" + originalBody + "\n-----\n" + errorMessage;
        return new StompFrame("ERROR", errHeaders, errBody);
    }

    //create receipt frame based on receipt header
    public StompFrame generateReceiptFrame(){
        Map<String, String> receiptHeaders = new HashMap<>();
        //assume receipt header is present
        receiptHeaders.put("receipt-id", headers.get("receipt"));
        return new StompFrame("RECEIPT", receiptHeaders, "");
    }

    //create connected frame after successful connection
    public StompFrame generateConnectedFrame(String version){
        Map<String, String> connectedHeaders = new HashMap<>();
        connectedHeaders.put("version", version);
        return new StompFrame("CONNECTED", connectedHeaders, "");
    }

    //check command correctness and delegate header tests
    public boolean checkFrame(){
        if(error != null) //check if parsing already found an error
            return false;
        
        //a valid frame must end with \u0000
        if(!body.endsWith("\u0000")) {
            error = "frame did not end with \u0000 parameter";
            return false;
        }

        //check command presence
        if(command == null || command.isEmpty()){
            error = "missing command";
            return false;
        }

        //check command validity and required headers
        switch (getCommand()) {
            case "CONNECT":
                return checkParam("accept-version") && checkParam("host")
                    && checkParam("login") && checkParam("passcode");
            case "SEND":
                return checkParam("destination");
            case "SUBSCRIBE":
                return checkParam("destination") && checkParam("id");
            case "UNSUBSCRIBE":
                return checkParam("id");
            case "DISCONNECT":
                return true; //user can either disonnect gracefully or not
            default: //unknown command 
                error = "unknown command";
                return false;
        }         
    }
    
    //ensure required header is present in the frame
    private boolean checkParam(String key){        
        if(headers.containsKey(key))
            return true;
        error = "missing required header: " + key;
        return false;
    }    
}
