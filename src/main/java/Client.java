import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Scanner;

public class Client {
    static BufferedReader bufferedReader;
    static PrintWriter printWriter;
    static Scanner scanner = new Scanner(System.in);
    static Socket clientSocket;
    static boolean on = true;

    public static void main(String[] args){
        try {
            clientSocket = new Socket("localhost",50001);
            bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            printWriter = new PrintWriter(clientSocket.getOutputStream(), true);
            String helloMessage = bufferedReader.readLine();
            System.out.println(helloMessage);
            register();
            readAndWrite();
            clientSocket.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void register() throws IOException {
        printWriter.println(scanner.nextLine());
        printWriter.println(ZoneId.systemDefault().toString());
        String registerAcceptMessage = bufferedReader.readLine();
        System.out.println(registerAcceptMessage);
    }

    private static void readAndWrite() throws IOException {
        Thread messageListener = new Thread(()->{
            try {
                while (on){
                    String message = bufferedReader.readLine();
                    Gson gson = new Gson();
                    Message messageObject = gson.fromJson(message, Message.class);
                    System.out.println("<" + messageObject.getTime() + "> " + "[" + messageObject.getName() + "] " + messageObject.getMessage());
                    if (messageObject.getFile() != null){
                        byte[] decodedImg = Base64.getDecoder()
                                .decode(messageObject.getFile());
                        Path destinationFile = Paths.get("11"+messageObject.getMessage().split("Файл передан:")[1].trim());
                        Files.write(destinationFile, decodedImg);
                    }
                }
            } catch (IOException e) {
                on = false;
            }
        });
        messageListener.start();
        while (on){
            String message = scanner.nextLine();
            if (message.contains("--file")){
                File file = new File(message.split("--file")[1].trim());
                String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
                Message messageObject = new Message(null, null,"Файл передан: " + file.getName(), encoded);
                printWriter.println(new Gson().toJson(messageObject));
            } else {
                Message messageObject = new Message(null, null, message, null);
                printWriter.println(new Gson().toJson(messageObject));
            }
            if (message.equals("/qqq")) on = false;
        }
    }
}

