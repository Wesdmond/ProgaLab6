package utilty;

import commandLine.Printable;
import dtp.Request;
import dtp.Response;
import dtp.ResponseStatus;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class Client {
    private String host;
    private int port;
    private int reconnectionTimeout;
    private int reconnectionAttempts;
    private int maxReconnectionAttempts;
    private Printable console;
    private Socket socket;


    public Client(String host, int port, int reconnectionTimeout, int maxReconnectionAttempts, Printable console) {
        this.host = host;
        this.port = port;
        this.reconnectionTimeout = reconnectionTimeout;
        this.maxReconnectionAttempts = maxReconnectionAttempts;
        this.console = console;
    }

    public Response sendAndAskResponse(Request request){
        while (true) {
            try {
                if(Objects.isNull(socket)) throw new IOException();
                if (request.isEmpty()) return new Response(ResponseStatus.WRONG_ARGUMENTS, "Запрос пустой!");
                if (!socket.isConnected())
                    throw new IOException();
                SocketChannel sc = socket.getChannel();
                // Сериализуем объект в массив байтов
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                objectOutputStream.writeObject(request);
                objectOutputStream.flush();

                // Получаем массив байтов из сериализованного объекта
                byte[] serializedObject = byteArrayOutputStream.toByteArray();

                // Отправляем массив байтов по сокету
                ByteBuffer buffer = ByteBuffer.wrap(serializedObject);
                while (buffer.hasRemaining()) {
                    sc.write(buffer);
                }
                try {
                    Thread.sleep(50);
                } catch (Exception ex) {}
                buffer = ByteBuffer.allocate(1024);

                // Читаем данные из соксета в буфер
                int bytesRead;
                ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
                try {
                    while ((bytesRead = sc.read(buffer)) != -1) {
                        if (bytesRead == 0) break;
                        buffer.flip(); // Переключаем буфер в режим чтения
                        // Записываем данные из буфера во временное хранилище
                        receivedData.write(buffer.array(), 0, bytesRead);
                        buffer.clear(); // Очищаем буфер для следующего чтения
                    }
                } catch (Exception ingored) {console.printError(ingored.getMessage());}
                // Десериализуем объект из полученных данных
                byte[] receivedBytes = receivedData.toByteArray();
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(receivedBytes);
                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
                Response response = null;
                try {
                    response = (Response) objectInputStream.readObject();
                    this.disconnectFromServer();
                    reconnectionAttempts = 0;
                    return response;
                } catch (ClassNotFoundException | IOException ingored) {
                    console.printError(ingored.getMessage());
                }
                this.disconnectFromServer();
                reconnectionAttempts = 0;
                return response;
            } catch (IOException e) {
                if (reconnectionAttempts == 0){
                    connectToServer();
                    reconnectionAttempts++;
                    continue;
                } else {
                    console.printError("Соединение с сервером разорвано");
                }
                try {
                    reconnectionAttempts++;
                    if (reconnectionAttempts >= maxReconnectionAttempts) {
                        console.printError("Превышено максимальное количество попыток соединения с сервером");
                        return new Response(ResponseStatus.EXIT);
                    }
                    console.println("Повторная попытка через " + reconnectionTimeout / 1000 + " секунд");
                    Thread.sleep(reconnectionTimeout);
                    connectToServer();
                } catch (Exception exception) {
                    console.printError("Попытка соединения с сервером неуспешна");
                }
            }
        }
    }

    public void connectToServer(){
        try{
            if(reconnectionAttempts > 0) console.println("Попытка повторного подключения", ConsoleColors.CYAN);
            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
            socketChannel.configureBlocking(false);
            this.socket = socketChannel.socket();
            console.println("Подключение успешно восстановлено", ConsoleColors.GREEN);
        } catch (IllegalArgumentException e){
            console.printError("Адрес сервера введен некорректно");
        } catch (IOException e) {
            console.printError("Произошла ошибка при соединении с сервером");
        }
    }

    public void disconnectFromServer(){
        try {
            this.socket.close();
        } catch (IOException e) {
            console.printError("Не подключен к серверу");
        }
    }
}
