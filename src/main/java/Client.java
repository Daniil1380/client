import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

public class Client {
    static BufferedReader bufferedReader;
    static PrintWriter printWriter;
    static Scanner scanner = new Scanner(System.in);
    static Socket clientSocket;
    static boolean on = true;
    static OutputStream outputStream;
    static InputStream inputStream;
    static InputStreamReader inputStreamReader;

    public static void main(String[] args){
        try {
            clientSocket = new Socket("localhost",8511);
            bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            outputStream = clientSocket.getOutputStream();
            printWriter = new PrintWriter(outputStream, true);
            inputStream = clientSocket.getInputStream();
            inputStreamReader = new InputStreamReader(inputStream);
            readHeader(inputStream);
            String helloMessage = bufferedReader.readLine();
            System.out.println(helloMessage);
            register(outputStream, inputStream);
            readAndWrite();
            clientSocket.close();
        }
        catch (Exception e) {
        }
    }

    private static void register(OutputStream outputStream, InputStream inputStream) throws IOException {
        String name = scanner.nextLine();
        outputStream.write(createHeader(name, false, true, false, false, 0));
        printWriter.println(name);
        String zone = ZoneId.systemDefault().toString();
        outputStream.write(createHeader(zone, false, true, false, false, 0));
        printWriter.println(zone);
        readHeader(inputStream);
        String registerAcceptMessage = bufferedReader.readLine();
        System.out.println(registerAcceptMessage);
    }

    public static int readHeader(InputStream inputStream) throws IOException {
        byte[] bytes = new byte[12];
        inputStream.read(bytes, 0, 12);
        byte[] size = new byte[4];
        System.arraycopy(bytes, 8, size, 0, 4);
        return byteArrayBigToInt(size);
    }

    public static final int byteArrayBigToInt(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    public static byte[] createHeader(String message, boolean startTimeCode, boolean startName, boolean finish, boolean file, int size) throws UnsupportedEncodingException {
        byte[] array = new byte[12];
        array[0] = (byte) (1);
        array[1] = (byte) (1);
        int messageSize = message.getBytes().length;
        array[2] = (byte) (messageSize / (int) Math.pow(2, 8));
        array[3] = (byte) (messageSize % (int) Math.pow(2, 8));
        array[4] = (byte) ((booleanToInt(startTimeCode) << 7) + (booleanToInt(startName) << 6)
                + (booleanToInt(finish) << 5) + (booleanToInt(file) << 4));
        byte[] hashcode = intToByteArray(message.hashCode());
        for (int i = 5; i < 8; i++) {
            array[i] = hashcode[i - 5];
        }
        byte[] sizeBytes = intToByteArrayBig(size);
        for (int i = 8; i < 12; i++) {
            array[i] = sizeBytes[i - 8];
        }
        return array;
    }

    public static final byte[] intToByteArrayBig(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static final byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    public static int booleanToInt(boolean b){
        return b ? 1 : 0;
    }

    private static void readAndWrite() throws IOException {
        Thread messageListener = new Thread(()->{
            try {
                while (on){
                    int length = readHeader(inputStream);
                    byte[] fileInBytes = new byte[length];
                    inputStream.read(fileInBytes);
                    String message = bufferedReader.readLine();
                    Gson gson = new Gson();
                    Message messageObject = gson.fromJson(message, Message.class);
                    System.out.println("<" + messageObject.getTime() + "> " + "[" + messageObject.getName() + "] " + messageObject.getMessage());
                    if (fileInBytes.length != 0){
                        Path destinationFile = Paths.get("files/"+messageObject.getMessage().split("Файл передан:")[1].trim());
                        Files.write(destinationFile, fileInBytes);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                on = false;
            }
        });
        messageListener.start();
        while (on){
            String message = scanner.nextLine();
            if (message.contains("--file")){
                File file = new File(message.split("--file")[1].trim());
                byte[] fileInBytes = Files.readAllBytes(file.toPath());
                Message messageObject = new Message(null, null,"Файл передан: " + file.getName(), null);
                byte[] header = createHeader(message, false, false, false, true, fileInBytes.length);
                outputStream.write(header);
                outputStream.write(fileInBytes);
                printWriter.println(new Gson().toJson(messageObject));
            } else {
                Message messageObject = new Message(null, null, message, null);
                outputStream.write(createHeader(message, false, false, message.equals("/qqq"), false, 0));
                printWriter.println(new Gson().toJson(messageObject));
            }
            if (message.equals("/qqq")) on = false;
        }
    }
}

