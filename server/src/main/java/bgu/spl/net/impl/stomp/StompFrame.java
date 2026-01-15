package bgu.spl.net.impl.stomp;
import java.util.HashMap;
import java.util.Map;
public class StompFrame {

    private String command;
    private Map<String, String> headers;
    private String body;

    public StompFrame(String command, Map<String, String> headers, String body) {
        this.command = command;
        this.headers = headers;
        this.body = body;
    }

    public StompFrame(String message){
        //ensure message is valid
        String[] lines = checkFrame(message);
        //organize and store frame data
        if(lines != null){
            //first line is command
            this.command = lines[0];
            //parse headers
            int i = 1;
            while(i < lines.length - 1 && !lines[i].isEmpty()) { //stop when we reach the body/end of headers
                //split header into key and value
                String[] header = lines[i].split(":");
                //insert parameter and value into headers map
                headers.put(header[0], header[1]);
                i++; 
            }
            //concatenate body lines
            for(int j=i; j<lines.length - 1; j++){ 
                this.body += lines[j]+"\n";
            }
        }
        //if frame was invalid by this point an error frame was generated
    }

    public String getCommand() {
        return command;
    }

    private void setCommand(String command) {
        this.command = command;
    }

    private void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    private void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    
    //send error message and disconnect client in case of malformed frame
    private StompFrame generateErrorFrame(String message, String error){
        //create error message
        setCommand("ERROR");
        Map<String, String> errorHeaders = new HashMap<>();
        errorHeaders.put("message", "malformed frame received");
        setHeaders(errorHeaders);
        setBody(
            "The message:\n" +
            "-----\n" +
            message +
            "\n ----- \n" +
            error + "\n"
        );
        return this;
    }

    //ensure frame's command and delegate header correctness checking
    private String[] checkFrame(String message){
        String[] lines = message.split("\n");
        String[] parameters;
        
        switch (lines[0]) {
            case "CONNECT":
                parameters = new String[]{"accept-version:", "host:", "login:", "passcode:"};
                break;
            case "SEND":
                parameters = new String[]{"destination:"};
                break;
            case "SUBSCRIBE":
                parameters = new String[]{"destination:", "id:"};
                break;
            case "UNSUBSCRIBE":
                parameters = new String[]{"id:"};
                break;
            case "DISCONNECT":
                parameters = new String[]{"receipt:"};
                break;
            default: //unknown command detected
                generateErrorFrame(message, "unknown command");
                return null;
        }
        if(message.endsWith("\\u0000")) {//a valid frame must end with \\u0000
            //ensure all required parameters are present
            checkParams(message, parameters);    
            //check if an error frame was generated during parameter checking
            if(!getCommand().equals("ERROR")) return lines;
        } 
        else {
            generateErrorFrame(message, "frame did not include \\u0000 parameter");
        }
        return null; // if null is returned an error frame was generated
    }
    
    //assumes command header is valid
    //ensure all required headers are present in the frame
    //if one's missing generate error frame accordingly
    private void checkParams(String message, String[] parameters){        
        String[] lines = message.split("\n");
        //iterate and check that each parameter is present
        for(int i=1; i<parameters.length; i++){
            boolean valid = false;
            for(String line : lines){
                if(line.startsWith(parameters[i])){ //check if parameter is present
                    valid=true;
                    break;
                }
            }
            if(!valid) {// parameter not found
                generateErrorFrame(message, "missing header: " + parameters[i]); //generate error message 
            }
        }
    }

    
}
